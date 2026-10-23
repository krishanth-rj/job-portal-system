package edu.college.jobportal.dao;

import java.sql.SQLException;

/** The single authenticated account for this desktop application process. */
public final class UserSession {
    private static volatile AuthenticatedUser currentUser;

    private UserSession() {
    }

    public static synchronized void start(AuthenticatedUser user) {
        if (user == null) {
            throw new IllegalArgumentException("An authenticated user is required.");
        }
        currentUser = user;
    }

    public static AuthenticatedUser currentUser() {
        return currentUser;
    }

    public static boolean isCurrent(AuthenticatedUser user) {
        return user != null && currentUser == user;
    }

    public static boolean isCurrent(int userId, UserRole role) {
        AuthenticatedUser active = currentUser;
        return active != null && active.userId() == userId && active.role() == role;
    }

    /** Clears only the session that initiated the logout. */
    public static synchronized void clear(AuthenticatedUser expectedUser) {
        if (currentUser == expectedUser) {
            currentUser = null;
        }
    }

    public static synchronized void clear() {
        currentUser = null;
    }

    public static void requireCurrent(int userId, UserRole role) throws SQLException {
        if (!isCurrent(userId, role)) {
            throw new SQLException("No active authenticated session for this operation.");
        }
    }
}
