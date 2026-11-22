package com.example.waitlist.model;

import java.util.Set;

/**
 * The single course offering managed by a service instance (DESIGN.md section 3).
 *
 * @param courseId              identifier of this offering
 * @param capacity              maximum number of enrolled learners
 * @param prerequisiteCourseIds IDs of courses that must all be completed before enrolling;
 *                              stored as an unmodifiable copy
 */
public record Course(String courseId, int capacity, Set<String> prerequisiteCourseIds) {

    public Course {
        // Defensive, unmodifiable copy (DESIGN.md section 3). Set.copyOf rejects a null set
        // and null elements with NullPointerException.
        prerequisiteCourseIds = Set.copyOf(prerequisiteCourseIds);
    }
}
