package edu.college.jobportal.dao;

import at.favre.lib.crypto.bcrypt.BCrypt;
import edu.college.jobportal.database.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

/** User registration, authentication, and read-only user queries. */
public class UserDAO {
    private static final String INSERT_USER =
            "INSERT INTO users (name, email, password, phone, role) VALUES (?, ?, ?, ?, ?)";
    private static final String SELECT_USERS =
            "SELECT user_id, name, email, role FROM users ORDER BY user_id";
    private static final String FIND_LOGIN_USER =
            "SELECT user_id, name, email, `password`, role FROM users WHERE email = ?";
    private static final Set<String> REGISTRABLE_ROLES = Set.of("JOB_SEEKER", "EMPLOYER");

    /** Stores a BCrypt hash and inserts a non-admin account. */
    public boolean registerUser(String name, String email, char[] password, String phone, String role)
            throws SQLException, DuplicateEmailException {
        if (role == null || !REGISTRABLE_ROLES.contains(role)) {
            throw new IllegalArgumentException("Only job seeker and employer registrations are allowed.");
        }

        String passwordHash = BCrypt.withDefaults().hashToString(12, password);
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_USER)) {
            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            if (phone == null || phone.isBlank()) {
                statement.setNull(4, java.sql.Types.VARCHAR);
            } else {
                statement.setString(4, phone);
            }
            statement.setString(5, role);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            if (isDuplicateKey(exception)) {
                throw new DuplicateEmailException(exception);
            }
            throw exception;
        }
    }

    /** Returns the authenticated account, or null when email/password do not match. */
    public AuthenticatedUser authenticate(String email, char[] password) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_LOGIN_USER)) {
            statement.setString(1, email);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return null;
                }

                String storedPasswordHash = results.getString("password");
                BCrypt.Result verification = BCrypt.verifyer().verify(password, storedPasswordHash);
                if (!verification.verified) {
                    return null;
                }

                UserRole role;
                try {
                    role = UserRole.fromDatabaseValue(results.getString("role"));
                } catch (IllegalArgumentException exception) {
                    throw new SQLException("The account has an unsupported role.", exception);
                }
                return new AuthenticatedUser(results.getInt("user_id"), results.getString("name"),
                        results.getString("email"), role);
            }
        }
    }

    /** Prints users using the caller's connection, which remains owned by the caller. */
    public void printUsers(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_USERS);
             ResultSet results = statement.executeQuery()) {
            int count = 0;
            while (results.next()) {
                count++;
                System.out.printf("user_id=%d, name=%s, email=%s, role=%s%n",
                        results.getInt("user_id"),
                        results.getString("name"),
                        results.getString("email"),
                        results.getString("role"));
            }
            if (count == 0) {
                System.out.println("SELECT completed successfully; users table is empty.");
            }
        }
    }

    private static boolean isDuplicateKey(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            if (current.getErrorCode() == 1062 && "23000".equals(current.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
