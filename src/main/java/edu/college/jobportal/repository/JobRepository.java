package edu.college.jobportal.repository;

import edu.college.jobportal.model.JobEntry;
import edu.college.jobportal.model.JobListing;

import java.sql.SQLException;
import java.util.List;

/** Database operations available for job search and employer job management. */
public interface JobRepository {
    List<JobEntry> listForEmployer(int employerUserId, int companyId) throws SQLException;

    List<JobListing> searchActiveJobs(int seekerUserId, String keyword, String location) throws SQLException;

    JobEntry findForEmployer(int employerUserId, int companyId, int jobId) throws SQLException;

    boolean createForEmployer(int employerUserId, int companyId, JobEntry job) throws SQLException;

    boolean updateForEmployer(int employerUserId, int companyId, int jobId, JobEntry job) throws SQLException;

    boolean deactivateForEmployer(int employerUserId, int companyId, int jobId) throws SQLException;
}
