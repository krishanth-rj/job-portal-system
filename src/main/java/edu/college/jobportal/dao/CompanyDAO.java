package edu.college.jobportal.dao;

import edu.college.jobportal.database.DBConnection;
import edu.college.jobportal.model.CompanyProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Company profile operations scoped to the authenticated employer. */
public class CompanyDAO {
    private static final String FIND_EMPLOYER_COMPANY = """
            SELECT c.company_id, c.user_id, c.company_name, c.description, c.location, c.website
            FROM companies c
            JOIN users u ON u.user_id = c.user_id
            WHERE c.user_id = ? AND u.role = 'EMPLOYER'
            ORDER BY c.company_id
            LIMIT 1
            """;

    public CompanyProfile findForEmployer(int employerUserId) throws SQLException {
        UserSession.requireCurrent(employerUserId, UserRole.EMPLOYER);
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_EMPLOYER_COMPANY)) {
            statement.setInt(1, employerUserId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? mapCompany(results) : null;
            }
        }
    }

    /** Creates a profile only for the current employer; serializes concurrent creation by locking its user row. */
    public CompanyProfile createForEmployer(int employerUserId, String name, String description,
                                            String location, String website) throws SQLException {
        UserSession.requireCurrent(employerUserId, UserRole.EMPLOYER);
        try (Connection connection = DBConnection.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lockUser = connection.prepareStatement(
                        "SELECT user_id FROM users WHERE user_id = ? AND role = 'EMPLOYER' FOR UPDATE")) {
                    lockUser.setInt(1, employerUserId);
                    try (ResultSet result = lockUser.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("Employer account could not be verified.");
                        }
                    }
                }

                CompanyProfile existing = findForEmployer(connection, employerUserId);
                if (existing != null) {
                    connection.commit();
                    return existing;
                }

                String insert = "INSERT INTO companies (user_id, company_name, description, location, website) "
                        + "VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement statement = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, employerUserId);
                    statement.setString(2, name);
                    statement.setString(3, blankToNull(description));
                    statement.setString(4, blankToNull(location));
                    statement.setString(5, blankToNull(website));
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("Company profile was not created.");
                    }
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Company profile ID was not returned.");
                        }
                        CompanyProfile created = new CompanyProfile(keys.getInt(1), employerUserId, name,
                                blankToNull(description), blankToNull(location), blankToNull(website));
                        connection.commit();
                        return created;
                    }
                }
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    private CompanyProfile findForEmployer(Connection connection, int employerUserId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_EMPLOYER_COMPANY)) {
            statement.setInt(1, employerUserId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? mapCompany(results) : null;
            }
        }
    }

    private static CompanyProfile mapCompany(ResultSet result) throws SQLException {
        return new CompanyProfile(result.getInt("company_id"), result.getInt("user_id"),
                result.getString("company_name"), result.getString("description"),
                result.getString("location"), result.getString("website"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
