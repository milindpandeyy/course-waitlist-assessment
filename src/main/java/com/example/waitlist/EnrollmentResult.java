package com.example.waitlist;

/** Outcome of {@link Course#enroll(StudentId)}. */
public enum EnrollmentResult {
    /** A seat was free and the student now holds it. */
    ENROLLED,
    /** The course was full; the student was appended to the end of the waitlist. */
    WAITLISTED,
    /** No-op: the student already holds a seat. */
    ALREADY_ENROLLED,
    /** No-op: the student is already on the waitlist (position unchanged). */
    ALREADY_WAITLISTED
}
