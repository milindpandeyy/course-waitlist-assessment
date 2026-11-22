package com.example.waitlist.model;

import java.util.Set;

/**
 * A learner requesting enrollment (DESIGN.md section 3).
 *
 * <p>No validation happens here: a null or blank {@code learnerId} and a null
 * {@code completedCourseIds} must stay representable, because the service reports them as
 * {@code MISSING_LEARNER_DETAILS} (DESIGN.md section 5, assumption A6).
 *
 * @param learnerId          identifier of the learner
 * @param completedCourseIds IDs of courses the learner has completed; an empty set is valid;
 *                           a non-null set is stored as an unmodifiable copy
 */
public record LearnerDetails(String learnerId, Set<String> completedCourseIds) {

    public LearnerDetails {
        // Defensive, unmodifiable copy (DESIGN.md section 3); null is kept as null.
        // Set.copyOf rejects null elements with NullPointerException.
        completedCourseIds = completedCourseIds == null ? null : Set.copyOf(completedCourseIds);
    }
}
