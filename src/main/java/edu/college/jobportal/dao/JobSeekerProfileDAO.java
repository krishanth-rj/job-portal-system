package edu.college.jobportal.dao;

import edu.college.jobportal.database.DBConnection;
import edu.college.jobportal.model.JobSeekerProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.nio.charset.StandardCharsets;

/** Profile operations scoped to the current job seeker session. */
public class JobSeekerProfileDAO {
    private static final String FIND_PROFILE = """
            SELECT u.name, u.email, u.phone, js.seeker_id, js.education, js.skills,
                   js.experience, js.location
            FROM users u
            LEFT JOIN job_seekers js ON js.user_id = u.user_id
            WHERE u.user_id = ? AND u.role = 'JOB_SEEKER'
            ORDER BY js.seeker_id
            LIMIT 1
            """;

    /** Loads only the profile belonging to the authenticated job seeker. */
    public JobSeekerProfile loadCurrentProfile() throws SQLException {
        AuthenticatedUser user = currentSeeker();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_PROFILE)) {
            statement.setInt(1, user.userId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("The authenticated job seeker account could not be found.");
                }
                return mapProfile(result);
            }
        }
    }

    /** Updates account and seeker details, creating a missing seeker row when needed. */
    public JobSeekerProfile saveCurrentProfile(String name, String phone, String education,
                                                String skills, String experience, String location)
            throws SQLException {
        AuthenticatedUser user = currentSeeker();
        String normalizedName = normalizeRequired(name, 100, "Full name");
        String normalizedPhone = normalizeOptional(phone, 15, "Phone number");
        String normalizedEducation = normalizeOptional(education, 150, "Education");
        String normalizedSkills = normalizeText(skills, 65_535, "Skills");
        String normalizedExperience = normalizeOptional(experience, 100, "Experience");
        String normalizedLocation = normalizeOptional(location, 100, "Location");

        try (Connection connection = DBConnection.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                verifyAndLockCurrentSeeker(connection, user);

                try (PreparedStatement updateUser = connection.prepareStatement(
                        "UPDATE users SET name = ?, phone = ? WHERE user_id = ? AND role = 'JOB_SEEKER'")) {
                    updateUser.setString(1, normalizedName);
                    setNullableString(updateUser, 2, normalizedPhone);
                    updateUser.setInt(3, user.userId());
                    // MySQL may report zero changed rows when the user saved unchanged values.
                    updateUser.executeUpdate();
                }

                Integer seekerId = findSeekerId(connection, user.userId());
                if (seekerId == null) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO job_seekers (user_id, education, skills, experience, location) "
                                    + "VALUES (?, ?, ?, ?, ?)")) {
                        insert.setInt(1, user.userId());
                        setNullableString(insert, 2, normalizedEducation);
                        setNullableString(insert, 3, normalizedSkills);
                        setNullableString(insert, 4, normalizedExperience);
                        setNullableString(insert, 5, normalizedLocation);
                        if (insert.executeUpdate() != 1) {
                            throw new SQLException("The job seeker profile could not be created.");
                        }
                    }
                } else {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE job_seekers SET education = ?, skills = ?, experience = ?, location = ? "
                                    + "WHERE seeker_id = ? AND user_id = ?")) {
                        setNullableString(update, 1, normalizedEducation);
                        setNullableString(update, 2, normalizedSkills);
                        setNullableString(update, 3, normalizedExperience);
                        setNullableString(update, 4, normalizedLocation);
                        update.setInt(5, seekerId);
                        update.setInt(6, user.userId());
                        // A zero-row count is valid when every saved value is unchanged.
                        update.executeUpdate();
                    }
                }

                JobSeekerProfile saved = new JobSeekerProfile(normalizedName, user.email(), normalizedPhone,
                        normalizedEducation, normalizedSkills, normalizedExperience, normalizedLocation, true);
                connection.commit();
                return saved;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private static AuthenticatedUser currentSeeker() throws SQLException {
        AuthenticatedUser user = UserSession.currentUser();
        if (user == null || user.role() != UserRole.JOB_SEEKER) {
            throw new SQLException("An authenticated job seeker session is required.");
        }
        UserSession.requireCurrent(user.userId(), UserRole.JOB_SEEKER);
        return user;
    }

    private static void verifyAndLockCurrentSeeker(Connection connection, AuthenticatedUser user)
            throws SQLException {
        UserSession.requireCurrent(user.userId(), UserRole.JOB_SEEKER);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT user_id FROM users WHERE user_id = ? AND role = 'JOB_SEEKER' FOR UPDATE")) {
            statement.setInt(1, user.userId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("The authenticated job seeker account could not be verified.");
                }
            }
        }
    }

    private static Integer findSeekerId(Connection connection, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT seeker_id FROM job_seekers WHERE user_id = ? ORDER BY seeker_id LIMIT 1")) {
            statement.setInt(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt("seeker_id") : null;
            }
        }
    }

    private static JobSeekerProfile mapProfile(ResultSet result) throws SQLException {
        int seekerId = result.getInt("seeker_id");
        boolean hasSeekerProfile = !result.wasNull();
        return new JobSeekerProfile(result.getString("name"), result.getString("email"),
                result.getString("phone"), result.getString("education"), result.getString("skills"),
                result.getString("experience"), result.getString("location"), hasSeekerProfile);
    }

    private static String normalizeRequired(String value, int maxLength, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be " + maxLength + " characters or fewer.");
        }
        return normalized;
    }

    private static String normalizeOptional(String value, int maxLength, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be " + maxLength + " characters or fewer.");
        }
        return normalized;
    }

    private static String normalizeText(String value, int maxBytes, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException(field + " exceeds the MySQL TEXT size limit.");
        }
        return normalized;
    }

    private static void setNullableString(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }
}
