package edu.college.jobportal.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

public record JobEntry(
        int jobId,
        String jobTitle,
        String description,
        String requiredSkills,
        String location,
        BigDecimal salary,
        String experienceRequired,
        String jobType,
        Timestamp postedDate,
        String status) {
}
