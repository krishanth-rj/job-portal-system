package edu.college.jobportal.model;

/** A job belonging to an employer, shown in the applicant manager. */
public record ApplicantJob(int jobId, String jobTitle, String jobStatus) {
    @Override
    public String toString() {
        return jobTitle + (jobStatus == null || jobStatus.isBlank() ? "" : " (" + jobStatus + ")");
    }
}
