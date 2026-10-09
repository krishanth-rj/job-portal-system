package edu.college.jobportal.dao;

/** Roles as stored by the existing users.role enum. */
public enum UserRole {
    JOB_SEEKER,
    EMPLOYER,
    ADMIN;

    public static UserRole fromDatabaseValue(String value) {
        return UserRole.valueOf(value);
    }
}
