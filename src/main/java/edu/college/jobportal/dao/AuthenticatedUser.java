package edu.college.jobportal.dao;

/** Safe account details returned after successful authentication. */
public record AuthenticatedUser(int userId, String name, String email, UserRole role) {
}
