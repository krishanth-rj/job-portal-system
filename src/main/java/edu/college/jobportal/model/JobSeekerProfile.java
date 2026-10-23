package edu.college.jobportal.model;

/** Profile fields visible to the authenticated job seeker. */
public record JobSeekerProfile(
        String name,
        String email,
        String phone,
        String education,
        String skills,
        String experience,
        String location,
        boolean seekerProfileExists) {
}
