package com.example.waitlist.model;

/**
 * Result of {@code enroll} and of {@code getLearnerStatus} (DESIGN.md section 4).
 *
 * <p>{@code NOT_ENROLLED} is only returned by status lookup, for a learner who is neither
 * enrolled nor waitlisted; it is never a result of {@code enroll}.
 */
public enum EnrollmentResult {
    ENROLLED,
    WAITLISTED,
    INELIGIBLE,
    MISSING_LEARNER_DETAILS,
    INVALID_COURSE_ID,
    ALREADY_ENROLLED,
    ALREADY_WAITLISTED,
    NOT_ENROLLED
}
