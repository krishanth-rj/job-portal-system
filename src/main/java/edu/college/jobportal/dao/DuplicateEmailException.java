package edu.college.jobportal.dao;

/** Indicates that the users table's unique email constraint rejected a registration. */
public class DuplicateEmailException extends Exception {
    public DuplicateEmailException() {
        super("An account with that email address already exists.");
    }

    public DuplicateEmailException(Throwable cause) {
        super("An account with that email address already exists.", cause);
    }
}
