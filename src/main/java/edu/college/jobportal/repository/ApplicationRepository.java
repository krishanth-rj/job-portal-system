package edu.college.jobportal.repository;

import edu.college.jobportal.model.ApplicationEntry;

import java.sql.SQLException;
import java.util.List;

/** Database operations for submitting applications and viewing seeker history. */
public interface ApplicationRepository {
    enum ApplyResult {
        APPLIED,
        ALREADY_APPLIED,
        JOB_UNAVAILABLE,
        NO_SEEKER_PROFILE
    }

    record ApplicationsOverview(boolean seekerProfileExists, List<ApplicationEntry> applications) {
    }

    ApplyResult applyToJob(int userId, int jobId) throws SQLException;

    ApplicationsOverview listForUser(int userId) throws SQLException;
}
