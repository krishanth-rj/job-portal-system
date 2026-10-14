package edu.college.jobportal.model;

public record CompanyProfile(
        int companyId,
        int userId,
        String companyName,
        String description,
        String location,
        String website) {
}
