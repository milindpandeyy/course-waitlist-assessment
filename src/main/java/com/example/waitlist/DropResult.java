package com.example.waitlist;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of {@link Course#drop(StudentId)}.
 *
 * @param outcome  what happened to the dropping student
 * @param promoted the waitlisted student who was given the freed seat, if any
 */
public record DropResult(Outcome outcome, Optional<StudentId> promoted) {

    /** What happened to the student who dropped. */
    public enum Outcome {
        /** The student gave up an enrolled seat. */
        LEFT_COURSE,
        /** The student was removed from the waitlist; no seat was freed. */
        LEFT_WAITLIST,
        /** No-op: the student was neither enrolled nor waitlisted. */
        NOT_REGISTERED
    }

    public DropResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(promoted, "promoted");
        if (promoted.isPresent() && outcome != Outcome.LEFT_COURSE) {
            throw new IllegalArgumentException("Only leaving the course can promote someone");
        }
    }

    static DropResult leftCourse(Optional<StudentId> promoted) {
        return new DropResult(Outcome.LEFT_COURSE, promoted);
    }

    static DropResult leftWaitlist() {
        return new DropResult(Outcome.LEFT_WAITLIST, Optional.empty());
    }

    static DropResult notRegistered() {
        return new DropResult(Outcome.NOT_REGISTERED, Optional.empty());
    }
}
