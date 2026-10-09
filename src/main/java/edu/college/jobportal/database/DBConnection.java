package edu.college.jobportal.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Creates a new JDBC connection when requested. Callers own and close the returned connection. */
public final class DBConnection {
    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/job_portal";

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        String url = setting("DB_URL", DEFAULT_URL);
        String username = setting("DB_USERNAME", null);
        String password = setting("DB_PASSWORD", null);

        if (username == null || username.isBlank()) {
            throw new SQLException("DB_USERNAME is not set. Set DB_USERNAME and DB_PASSWORD before running the database test.");
        }

        return DriverManager.getConnection(url, username, password == null ? "" : password);
    }

    private static String setting(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
