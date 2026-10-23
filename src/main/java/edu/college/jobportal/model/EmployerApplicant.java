package edu.college.jobportal.model;

import java.sql.Timestamp;

/** Applicant data visible to the employer that owns the associated job. */
public record EmployerApplicant(
        int applicationId,
        int jobId,
        String jobTitle,
        String name,
        String email,
        String phone,
        Timestamp applicationDate,
        String status,
        String skills,
        String education,
        String experience,
        String location) {
}
