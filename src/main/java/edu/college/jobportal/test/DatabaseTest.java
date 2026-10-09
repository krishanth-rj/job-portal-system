package edu.college.jobportal.test;

import edu.college.jobportal.dao.UserDAO;
import edu.college.jobportal.database.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;

/** Command-line smoke test for JDBC connectivity and reading the users table. */
public final class DatabaseTest {
    private DatabaseTest() {
    }

    public static void main(String[] args) {
        try (Connection connection = DBConnection.getConnection()) {
            System.out.println("Database Connected Successfully!");
            new UserDAO().printUsers(connection);
        } catch (SQLException exception) {
            System.err.println("Database connection or users query failed: " + exception.getMessage());
            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
