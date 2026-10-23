package edu.college.jobportal.dao;

import edu.college.jobportal.database.DBConnection;
import edu.college.jobportal.model.ApplicationEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Application operations scoped to the authenticated user's job seeker profile. */
public class ApplicationDAO {
    public enum ApplyResult {
        APPLIED,
        ALREADY_APPLIED,
        JOB_UNAVAILABLE,
        NO_SEEKER_PROFILE
    }

    public record ApplicationsOverview(boolean seekerProfileExists, List<ApplicationEntry> applications) {
    }

    /** Resolves seeker_id from userId and applies only while the selected job is active. */
    public ApplyResult applyToJob(int userId, int jobId) throws SQLException {
        UserSession.requireCurrent(userId, UserRole.JOB_SEEKER);
        try (Connection connection = DBConnection.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Integer seekerId = findSeekerId(connection, userId);
                if (seekerId == null) {
                    connection.rollback();
                    return ApplyResult.NO_SEEKER_PROFILE;
                }

                try (PreparedStatement lockJob = connection.prepareStatement(
                        "SELECT job_id FROM jobs WHERE job_id = ? AND UPPER(status) = 'ACTIVE' FOR UPDATE")) {
                    lockJob.setInt(1, jobId);
                    try (ResultSet result = lockJob.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return ApplyResult.JOB_UNAVAILABLE;
                        }
                    }
                }

                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO applications (job_id, seeker_id) VALUES (?, ?)")) {
                    insert.setInt(1, jobId);
                    insert.setInt(2, seekerId);
                    if (insert.executeUpdate() != 1) {
                        throw new SQLException("Application was not inserted.");
                    }
                }
                connection.commit();
                return ApplyResult.APPLIED;
            } catch (SQLException exception) {
                connection.rollback();
                if (isDuplicateKey(exception)) {
                    return ApplyResult.ALREADY_APPLIED;
                }
                throw exception;
            } catch (RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    /** Returns only applications whose seeker profile belongs to the authenticated user. */
    public ApplicationsOverview listForUser(int userId) throws SQLException {
        UserSession.requireCurrent(userId, UserRole.JOB_SEEKER);
        String sql = """
                SELECT a.application_id, a.application_date, a.status,
                       j.job_title, j.location AS job_location, j.status AS job_status,
                       c.company_name
                FROM applications a
                JOIN job_seekers js ON js.seeker_id = a.seeker_id
                JOIN users u ON u.user_id = js.user_id
                JOIN jobs j ON j.job_id = a.job_id
                JOIN companies c ON c.company_id = j.company_id
                WHERE u.user_id = ? AND u.role = 'JOB_SEEKER'
                ORDER BY a.application_date DESC, a.application_id DESC
                """;
        try (Connection connection = DBConnection.getConnection()) {
            Integer seekerId = findSeekerId(connection, userId);
            if (seekerId == null) {
                return new ApplicationsOverview(false, List.of());
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, userId);
                try (ResultSet results = statement.executeQuery()) {
                    List<ApplicationEntry> applications = new ArrayList<>();
                    while (results.next()) {
                        applications.add(new ApplicationEntry(results.getInt("application_id"),
                                results.getString("job_title"), results.getString("company_name"),
                                results.getTimestamp("application_date"), results.getString("status"),
                                results.getString("job_location"), results.getString("job_status")));
                    }
                    return new ApplicationsOverview(true, List.copyOf(applications));
                }
            }
        }
    }

    private Integer findSeekerId(Connection connection, int userId) throws SQLException {
        String sql = """
                SELECT js.seeker_id
                FROM job_seekers js
                JOIN users u ON u.user_id = js.user_id
                WHERE u.user_id = ? AND u.role = 'JOB_SEEKER'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt("seeker_id") : null;
            }
        }
    }

    private boolean isDuplicateKey(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            if (current.getErrorCode() == 1062 && "23000".equals(current.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
