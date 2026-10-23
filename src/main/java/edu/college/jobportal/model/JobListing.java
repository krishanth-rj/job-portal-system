package edu.college.jobportal.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/** An active vacancy with the company details needed by job seekers. */
public record JobListing(
        int jobId,
        String jobTitle,
        String description,
        String requiredSkills,
        String location,
        BigDecimal salary,
        String experienceRequired,
        String jobType,
        Timestamp postedDate,
        String companyName,
        String companyDescription,
        String companyLocation,
        String companyWebsite) {
}
