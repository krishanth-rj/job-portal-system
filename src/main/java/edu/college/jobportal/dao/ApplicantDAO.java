package edu.college.jobportal.dao;

import edu.college.jobportal.database.DBConnection;
import edu.college.jobportal.model.ApplicantJob;
import edu.college.jobportal.model.EmployerApplicant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Employer applicant queries always verify job ownership against the authenticated user ID. */
public class ApplicantDAO {
    public record ApplicantStatusUpdate(boolean updated, boolean statusIsAvailable) {
    }

    public List<ApplicantJob> listJobsForEmployer(int employerUserId) throws SQLException {
        UserSession.requireCurrent(employerUserId, UserRole.EMPLOYER);
        String sql = """
                SELECT j.job_id, j.job_title, j.status
                FROM jobs j
                JOIN companies c ON c.company_id = j.company_id
                JOIN users employer ON employer.user_id = c.user_id
                WHERE c.user_id = ? AND employer.role = 'EMPLOYER'
                ORDER BY j.posted_date DESC, j.job_id DESC
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, employerUserId);
            try (ResultSet results = statement.executeQuery()) {
                List<ApplicantJob> jobs = new ArrayList<>();
                while (results.next()) {
                    jobs.add(new ApplicantJob(results.getInt("job_id"), results.getString("job_title"),
                            results.getString("status")));
                }
                return List.copyOf(jobs);
            }
        }
    }

    /** Statuses are drawn from values already stored in the existing VARCHAR column. */
    public List<String> listExistingStatuses(int employerUserId) throws SQLException {
        UserSession.requireCurrent(employerUserId, UserRole.EMPLOYER);
        String sql = "SELECT DISTINCT status FROM applications "
                + "WHERE status IS NOT NULL AND TRIM(status) <> '' ORDER BY status";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            List<String> statuses = new ArrayList<>();
            while (results.next()) {
                statuses.add(results.getString("status"));
            }
            return List.copyOf(statuses);
        }
    }

    public List<EmployerApplicant> listApplicants(int employerUserId, Integer jobId, String status)
            throws SQLException {
        UserSession.requireCurrent(employerUserId, UserRole.EMPLOYER);
        StringBuilder sql = new StringBuilder("""
                SELECT a.application_id, a.job_id, j.job_title, seeker.name, seeker.email, seeker.phone,
                       a.application_date, a.status, js.skills, js.education, js.experience, js.location
                FROM applications a
                JOIN jobs j ON j.job_id = a.job_id
                JOIN companies c ON c.company_id = j.company_id
                JOIN users employer ON employer.user_id = c.user_id
                JOIN job_seekers js ON js.seeker_id = a.seeker_id
                JOIN users seeker ON seeker.user_id = js.user_id
                WHERE c.user_id = ? AND employer.role = 'EMPLOYER' AND seeker.role = 'JOB_SEEKER'
                """);
        if (jobId != null) {
            sql.append(" AND j.job_id = ?");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND a.status = ?");
        }
        sql.append(" ORDER BY a.application_date DESC, a.application_id DESC");

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int parameter = 1;
            statement.setInt(parameter++, employerUserId);
            if (jobId != null) {
                statement.setInt(parameter++, jobId);
            }
            if (status != null && !status.isBlank()) {
                statement.setString(parameter, status);
            }
            try (ResultSet results = statement.executeQuery()) {
                List<EmployerApplicant> applicants = new ArrayList<>();
                while (results.next()) {
                    applicants.add(new EmployerApplicant(results.getInt("application_id"),
                            results.getInt("job_id"), results.getString("job_title"),
                            results.getString("name"), results.getString("email"),
                            results.getString("phone"), results.getTimestamp("application_date"),
                            results.getString("status"), results.getString("skills"),
                            results.getString("education"), results.getString("experience"),
                            results.getString("location")));
                }
                return List.copyOf(applicants);
            }
        }
    }

    /** Updates only an application for a job owned by employerUserId and only to a known DB status. */
    public ApplicantStatusUpdate updateStatus(int employerUserId, int applicationId, int jobId, String newStatus)
            throws SQLException {
        UserSession.requireCurrent(employerUserId, UserRole.EMPLOYER);
        if (newStatus == null || newStatus.isBlank() || newStatus.length() > 30) {
            return new ApplicantStatusUpdate(false, false);
        }
        try (Connection connection = DBConnection.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (!statusExists(connection, newStatus)) {
                    connection.rollback();
                    return new ApplicantStatusUpdate(false, false);
                }

                String verifyOwnership = """
                        SELECT a.status
                        FROM applications a
                        JOIN jobs j ON j.job_id = a.job_id
                        JOIN companies c ON c.company_id = j.company_id
                        JOIN users employer ON employer.user_id = c.user_id
                        WHERE a.application_id = ? AND j.job_id = ?
                          AND c.user_id = ? AND employer.role = 'EMPLOYER'
                        FOR UPDATE
                        """;
                try (PreparedStatement statement = connection.prepareStatement(verifyOwnership)) {
                    statement.setInt(1, applicationId);
                    statement.setInt(2, jobId);
                    statement.setInt(3, employerUserId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return new ApplicantStatusUpdate(false, true);
                        }
                        if (newStatus.equals(result.getString("status"))) {
                            connection.commit();
                            return new ApplicantStatusUpdate(true, true);
                        }
                    }
                }

                String sql = """
                        UPDATE applications a
                        JOIN jobs j ON j.job_id = a.job_id
                        JOIN companies c ON c.company_id = j.company_id
                        JOIN users employer ON employer.user_id = c.user_id
                        SET a.status = ?
                        WHERE a.application_id = ? AND j.job_id = ?
                          AND c.user_id = ? AND employer.role = 'EMPLOYER'
                        """;
                int updated;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, newStatus);
                    statement.setInt(2, applicationId);
                    statement.setInt(3, jobId);
                    statement.setInt(4, employerUserId);
                    updated = statement.executeUpdate();
                }
                connection.commit();
                return new ApplicantStatusUpdate(updated == 1, true);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    private boolean statusExists(Connection connection, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM applications WHERE status = ? LIMIT 1")) {
            statement.setString(1, status);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }
}
