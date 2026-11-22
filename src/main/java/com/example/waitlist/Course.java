package com.example.waitlist;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.SequencedSet;

/**
 * A course with a fixed number of seats and a first-come-first-served waitlist.
 *
 * <p>Invariants, checked (with {@code -ea}) after every mutation:
 * <ul>
 *   <li>{@code enrolledCount() <= capacity()}</li>
 *   <li>a student is never both enrolled and waitlisted</li>
 *   <li>the waitlist is non-empty only while the course is full</li>
 * </ul>
 *
 * <p>All public methods are {@code synchronized}, so a single instance is safe to share
 * between threads. State is in-memory only.
 */
public final class Course {

    private final String id;
    private final String title;
    private final int capacity;
    private final SequencedSet<StudentId> enrolled = new LinkedHashSet<>();
    private final SequencedSet<StudentId> waitlist = new LinkedHashSet<>();

    public Course(String id, String title, int capacity) {
        this.id = requireText(id, "id");
        this.title = requireText(title, "title");
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be at least 1, was " + capacity);
        }
        this.capacity = capacity;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public int capacity() {
        return capacity;
    }

    /** Enrolls the student if a seat is free, otherwise appends them to the waitlist. Idempotent. */
    public synchronized EnrollmentResult enroll(StudentId student) {
        Objects.requireNonNull(student, "student");
        if (enrolled.contains(student)) {
            return EnrollmentResult.ALREADY_ENROLLED;
        }
        if (waitlist.contains(student)) {
            return EnrollmentResult.ALREADY_WAITLISTED;
        }
        EnrollmentResult result;
        if (enrolled.size() < capacity) {
            enrolled.add(student);
            result = EnrollmentResult.ENROLLED;
        } else {
            waitlist.addLast(student);
            result = EnrollmentResult.WAITLISTED;
        }
        assertInvariants();
        return result;
    }

    /**
     * Removes the student from the course or from the waitlist. If an enrolled student
     * leaves, the head of the waitlist (if any) is promoted into the freed seat immediately.
     */
    public synchronized DropResult drop(StudentId student) {
        Objects.requireNonNull(student, "student");
        DropResult result;
        if (enrolled.remove(student)) {
            Optional<StudentId> promoted =
                    waitlist.isEmpty() ? Optional.empty() : Optional.of(waitlist.removeFirst());
            promoted.ifPresent(enrolled::add);
            result = DropResult.leftCourse(promoted);
        } else if (waitlist.remove(student)) {
            result = DropResult.leftWaitlist();
        } else {
            result = DropResult.notRegistered();
        }
        assertInvariants();
        return result;
    }

    public synchronized EnrollmentStatus statusOf(StudentId student) {
        Objects.requireNonNull(student, "student");
        if (enrolled.contains(student)) {
            return EnrollmentStatus.ENROLLED;
        }
        if (waitlist.contains(student)) {
            return EnrollmentStatus.WAITLISTED;
        }
        return EnrollmentStatus.NOT_REGISTERED;
    }

    /** 1-based position on the waitlist, or empty if the student is not waitlisted. */
    public synchronized OptionalInt waitlistPosition(StudentId student) {
        Objects.requireNonNull(student, "student");
        int position = 1;
        for (StudentId s : waitlist) {
            if (s.equals(student)) {
                return OptionalInt.of(position);
            }
            position++;
        }
        return OptionalInt.empty();
    }

    /** Snapshot of enrolled students in enrollment order. */
    public synchronized List<StudentId> enrolledStudents() {
        return List.copyOf(enrolled);
    }

    /** Snapshot of the waitlist, head first. */
    public synchronized List<StudentId> waitlistedStudents() {
        return List.copyOf(waitlist);
    }

    public synchronized int enrolledCount() {
        return enrolled.size();
    }

    public synchronized int availableSeats() {
        return capacity - enrolled.size();
    }

    public synchronized boolean isFull() {
        return enrolled.size() >= capacity;
    }

    @Override
    public synchronized String toString() {
        return "Course[" + id + " \"" + title + "\" " + enrolled.size() + "/" + capacity
                + ", waitlist=" + waitlist.size() + "]";
    }

    private void assertInvariants() {
        assert enrolled.size() <= capacity : "over capacity";
        assert waitlist.isEmpty() || enrolled.size() == capacity : "waitlist while seats free";
        assert waitlist.stream().noneMatch(enrolled::contains) : "student both enrolled and waitlisted";
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
