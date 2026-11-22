package com.example.waitlist.service;

import com.example.waitlist.model.Course;
import com.example.waitlist.model.EnrollmentResult;
import com.example.waitlist.model.LearnerDetails;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * Registration for exactly one course offering, held in memory (DESIGN.md sections 2-5).
 *
 * <p>Not thread-safe: single-threaded use only (DESIGN.md section 2, assumption A4).
 */
public class CourseRegistrationService {

    private final Course course;
    private final Set<String> enrolledLearners = new HashSet<>();
    private final Queue<String> waitlistedLearners = new ArrayDeque<>();

    /**
     * @param course the single offering this instance manages
     */
    public CourseRegistrationService(Course course) {
        this.course = Objects.requireNonNull(course, "course");
    }

    /**
     * Enrolls an eligible learner, or waitlists them when the course is full.
     * Checks run in the order of DESIGN.md section 5; every non-success result leaves
     * the state unchanged.
     */
    public EnrollmentResult enroll(String courseId, LearnerDetails learnerDetails) {
        if (!isValidCourse(courseId)) {
            return EnrollmentResult.INVALID_COURSE_ID;
        }
        if (!isValidLearner(learnerDetails)) {
            return EnrollmentResult.MISSING_LEARNER_DETAILS;
        }
        String learnerId = normalizeId(learnerDetails.learnerId());
        if (enrolledLearners.contains(learnerId)) {
            return EnrollmentResult.ALREADY_ENROLLED;
        }
        if (waitlistedLearners.contains(learnerId)) {
            return EnrollmentResult.ALREADY_WAITLISTED;
        }
        if (!meetsPrerequisites(learnerDetails)) {
            return EnrollmentResult.INELIGIBLE;
        }
        return enrollOrWaitlist(learnerId);
    }

    /** True if {@code courseId} names this offering, ignoring case and surrounding whitespace. */
    private boolean isValidCourse(String courseId) {
        return courseId != null && normalizeId(course.courseId()).equals(normalizeId(courseId));
    }

    /**
     * Canonical form of a course or learner ID: surrounding whitespace removed, lower-cased
     * (locale-independent). Learner IDs are stored in this form, so {@code " Alice "} and
     * {@code "alice"} are the same learner.
     */
    private static String normalizeId(String id) {
        return id.strip().toLowerCase(Locale.ROOT);
    }

    /** True if the details, a non-blank learner ID and a completed-course set are all present. */
    private boolean isValidLearner(LearnerDetails learnerDetails) {
        return learnerDetails != null
                && learnerDetails.learnerId() != null
                && !learnerDetails.learnerId().isBlank()
                && learnerDetails.completedCourseIds() != null;
    }

    /**
     * True if the learner completed every prerequisite, comparing course IDs ignoring case and
     * surrounding whitespace (a prerequisite {@code "JAVA"} is met by a completed {@code "java"}).
     */
    private boolean meetsPrerequisites(LearnerDetails learnerDetails) {
        Set<String> completed = normalizeIds(learnerDetails.completedCourseIds());
        return completed.containsAll(normalizeIds(course.prerequisiteCourseIds()));
    }

    private static Set<String> normalizeIds(Set<String> ids) {
        Set<String> normalized = new HashSet<>();
        for (String id : ids) {
            normalized.add(normalizeId(id));
        }
        return normalized;
    }

    /** Takes a free seat if there is one, otherwise joins the end of the FIFO waitlist. */
    private EnrollmentResult enrollOrWaitlist(String learnerId) {
        if (enrolledLearners.size() < course.capacity()) {
            enrolledLearners.add(learnerId);
            return EnrollmentResult.ENROLLED;
        }
        waitlistedLearners.add(learnerId);
        return EnrollmentResult.WAITLISTED;
    }
}
