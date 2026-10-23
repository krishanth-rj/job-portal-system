package edu.college.jobportal.model;

import java.sql.Timestamp;

/** Application history for one authenticated job seeker. */
public record ApplicationEntry(
        int applicationId,
        String jobTitle,
        String companyName,
        Timestamp applicationDate,
        String status,
        String jobLocation,
        String jobStatus) {
}
