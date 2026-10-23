package edu.college.jobportal.dao;

import edu.college.jobportal.database.DBConnection;
import edu.college.jobportal.model.JobEntry;
import edu.college.jobportal.model.JobListing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Job operations that verify company ownership against the employer user ID on every query. */
public class JobDAO {
    private static final String SEEKER_SEARCH_COLUMNS = """
            SELECT j.job_id, j.job_title, j.description, j.required_skills, j.location,
                   j.salary, j.experience_required, j.job_type, j.posted_date,
                   c.company_name, c.description AS company_description,
                   c.location AS company_location, c.website AS company_website
            FROM jobs j
            JOIN companies c ON c.company_id = j.company_id
            WHERE UPPER(j.status) = 'ACTIVE'
            """;
    private static final String OWNERSHIP_JOINS = """
            FROM jobs j
            JOIN companies c ON c.company_id = j.company_id
            JOIN users u ON u.user_id = c.user_id
            """;

    private static final String SELECT_COLUMNS = """
            SELECT j.job_id, j.job_title, j.description, j.required_skills, j.location,
                   j.salary, j.experience_required, j.job_type, j.posted_date, j.status
            """;

    public List<JobEntry> listForEmployer(int employerUserId, int companyId) throws SQLException {
        UserSession.requireCurrent(employerUserId, UserRole.EMPLOYER);
        String sql = SELECT_COLUMNS + OWNERSHIP_JOINS + " WHERE c.company_id = ? AND c.user_id = ? "
                + "AND u.role = 'EMPLOYER' ORDER BY j.posted_date DESC, j.job_id DESC";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, companyId);
            statement.setInt(2, employerUserId);
            try (ResultSet results = statement.executeQuery()) {
                List<JobEntry> jobs = new ArrayList<>();
                while (results.next()) {
                    jobs.add(mapJob(results));
                }
                return jobs;
            }
        }
    }

    /** Searches active vacancies only; optional keyword and location filters are parameterized. */
    public List<JobListing> searchActiveJobs(int seekerUserId, String keyword, String location) throws SQLException {
        UserSession.requireCurrent(seekerUserId, UserRole.JOB_SEEKER);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedLocation = location == null ? "" : location.trim();
        StringBuilder sql = new StringBuilder(SEEKER_SEARCH_COLUMNS);
        if (!normalizedKeyword.isEmpty()) {
            sql.append(" AND (j.job_title LIKE ? OR c.company_name LIKE ? OR j.description LIKE ?")
                    .append(" OR j.required_skills LIKE ? OR j.experience_required LIKE ? OR j.job_type LIKE ?)");
        }
        if (!normalizedLocation.isEmpty()) {
            sql.append(" AND (j.location LIKE ? OR c.location LIKE ?)");
        }
        sql.append(" ORDER BY j.posted_date DESC, j.job_id DESC");

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int parameter = 1;
            if (!normalizedKeyword.isEmpty()) {
                String pattern = "%" + normalizedKeyword + "%";
                for (int i = 0; i < 6; i++) {
                    statement.setString(parameter++, pattern);
                }
            }
            if (!normalizedLocation.isEmpty()) {
                String pattern = "%" + normalizedLocation + "%";
                statement.setString(parameter++, pattern);
                statement.setString(parameter, pattern);
            }
            try (ResultSet results = statement.executeQuery()) {
                List<JobListing> jobs = new ArrayList<>();
                while (results.next()) {
                    jobs.add(mapListing(results));
                }
                return jobs;
            }
        }
    }

    public JobEntry findForEmployer(int employerUserId, int companyId, int jobId) throws SQLException {
        UserSession.requireCurrent(employerUserId, UserRole.EMPLOYER);
        String sql = SELECT_COLUMNS + OWNERSHIP_JOINS + " WHERE j.job_id = ? AND c.company_id = ? "
                + "AND c.user_id = ? AND u.role = 'EMPLOYER'";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, jobId);
            statement.setInt(2, companyId);
            statement.setInt(3, employerUserId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? mapJob(results) : null;
            }
        }
    }

    public boolean createForEmployer(int employerUserId, int companyId, JobEntry job) throws SQLException {
        UserSession.requireCurrent(employerUserId, UserRole.EMPLOYER);
        String sql = """
                INSERT INTO jobs (company_id, job_title, description, required_skills, location,
                                  salary, experience_required, job_type)
                SELECT c.company_id, ?, ?, ?, ?, ?, ?, ?
                FROM companies c JOIN users u ON u.user_id = c.user_id
                WHERE c.company_id = ? AND c.user_id = ? AND u.role = 'EMPLOYER'
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindJobFields(statement, job, 1);
            statement.setInt(8, companyId);
            statement.setInt(9, employerUserId);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean updateForEmployer(int employerUserId, int companyId, int jobId, JobEntry job)
            throws SQLException {
        UserSession.requireCurrent(employerUserId, UserRole.EMPLOYER);
        String sql = """
                UPDATE jobs j
                JOIN companies c ON c.company_id = j.company_id
                JOIN users u ON u.user_id = c.user_id
                SET j.job_title = ?, j.description = ?, j.required_skills = ?, j.location = ?,
                    j.salary = ?, j.experience_required = ?, j.job_type = ?
                WHERE j.job_id = ? AND c.company_id = ? AND c.user_id = ? AND u.role = 'EMPLOYER'
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindJobFields(statement, job, 1);
            statement.setInt(8, jobId);
            statement.setInt(9, companyId);
            statement.setInt(10, employerUserId);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean deactivateForEmployer(int employerUserId, int companyId, int jobId) throws SQLException {
        UserSession.requireCurrent(employerUserId, UserRole.EMPLOYER);
        String sql = """
                UPDATE jobs j
                JOIN companies c ON c.company_id = j.company_id
                JOIN users u ON u.user_id = c.user_id
                SET j.status = 'INACTIVE'
                WHERE j.job_id = ? AND c.company_id = ? AND c.user_id = ? AND u.role = 'EMPLOYER'
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, jobId);
            statement.setInt(2, companyId);
            statement.setInt(3, employerUserId);
            return statement.executeUpdate() == 1;
        }
    }

    private static void bindJobFields(PreparedStatement statement, JobEntry job, int startIndex)
            throws SQLException {
        statement.setString(startIndex, job.jobTitle());
        statement.setString(startIndex + 1, job.description());
        statement.setString(startIndex + 2, blankToNull(job.requiredSkills()));
        statement.setString(startIndex + 3, blankToNull(job.location()));
        if (job.salary() == null) {
            statement.setNull(startIndex + 4, Types.DECIMAL);
        } else {
            statement.setBigDecimal(startIndex + 4, job.salary());
        }
        statement.setString(startIndex + 5, blankToNull(job.experienceRequired()));
        statement.setString(startIndex + 6, blankToNull(job.jobType()));
    }

    private static JobEntry mapJob(ResultSet result) throws SQLException {
        return new JobEntry(result.getInt("job_id"), result.getString("job_title"),
                result.getString("description"), result.getString("required_skills"),
                result.getString("location"), result.getBigDecimal("salary"),
                result.getString("experience_required"), result.getString("job_type"),
                result.getTimestamp("posted_date"), result.getString("status"));
    }

    private static JobListing mapListing(ResultSet result) throws SQLException {
        return new JobListing(result.getInt("job_id"), result.getString("job_title"),
                result.getString("description"), result.getString("required_skills"),
                result.getString("location"), result.getBigDecimal("salary"),
                result.getString("experience_required"), result.getString("job_type"),
                result.getTimestamp("posted_date"), result.getString("company_name"),
                result.getString("company_description"), result.getString("company_location"),
                result.getString("company_website"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
