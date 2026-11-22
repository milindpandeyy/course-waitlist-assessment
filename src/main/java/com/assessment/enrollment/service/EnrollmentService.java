package com.assessment.enrollment.service;

import com.assessment.enrollment.model.Course;
import com.assessment.enrollment.model.DropResult;
import com.assessment.enrollment.model.EnrollmentResult;
import com.assessment.enrollment.model.LearnerDetails;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * Enrollment, drop, status lookup and available seats for the course held by a
 * {@link CourseConfigurationService}, with in-memory state (DESIGN.md sections 3-5).
 *
 * <p>Not thread-safe: single-threaded use only (DESIGN.md section 2, assumption A4).
 */
public final class EnrollmentService {

    private final CourseConfigurationService configuration;
    private final Set<String> enrolledLearners = new HashSet<>();
    private final Queue<String> waitlistedLearners = new ArrayDeque<>();

    /**
     * @param configuration source of the course; it may be configured before or after this
     *                      service is created, but every operation needs a configured course
     * @throws NullPointerException if {@code configuration} is null
     */
    public EnrollmentService(CourseConfigurationService configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    /**
     * Enrolls an eligible learner, or waitlists them when the course is full.
     * Checks run in the order of DESIGN.md section 5; every non-success result leaves
     * the state unchanged.
     *
     * @throws IllegalStateException if no course is configured
     */
    public EnrollmentResult enroll(String courseId, LearnerDetails learnerDetails) {
        Course course = requireCourse();
        if (!isThisCourse(course, courseId)) {
            return EnrollmentResult.INVALID_COURSE_ID;
        }
        if (!isValidLearner(learnerDetails)) {
            return EnrollmentResult.MISSING_LEARNER_DETAILS;
        }
        String learnerId = Ids.normalize(learnerDetails.learnerId());
        if (enrolledLearners.contains(learnerId)) {
            return EnrollmentResult.ALREADY_ENROLLED;
        }
        if (waitlistedLearners.contains(learnerId)) {
            return EnrollmentResult.ALREADY_WAITLISTED;
        }
        if (!meetsPrerequisites(course, learnerDetails)) {
            return EnrollmentResult.INELIGIBLE;
        }
        return enrollOrWaitlist(course, learnerId);
    }

    /**
     * Drops a learner (DESIGN.md section 5). Dropping an enrolled learner frees their seat and
     * promotes the oldest waitlisted learner, if any; dropping a waitlisted learner only removes
     * them from the queue. The learner ID is matched ignoring case and surrounding whitespace.
     * A null/blank or unknown ID, including a repeated drop, changes nothing.
     *
     * @throws IllegalStateException if no course is configured
     */
    public DropResult drop(String learnerId) {
        requireCourse();
        if (Ids.isBlank(learnerId)) {
            return DropResult.LEARNER_DETAIL_NOT_FOUND;
        }
        String id = Ids.normalize(learnerId);
        if (enrolledLearners.remove(id)) {
            promoteFromWaitlist();
            return DropResult.DROPPED;
        }
        if (waitlistedLearners.remove(id)) {
            return DropResult.DROPPED;
        }
        return DropResult.LEARNER_DETAIL_NOT_FOUND;
    }

    /**
     * Seats still free: capacity minus enrolled learners (DESIGN.md section 4).
     *
     * @throws IllegalStateException if no course is configured
     */
    public int getAvailableSeats() {
        return requireCourse().capacity() - enrolledLearners.size();
    }

    /**
     * Current registration status, derived from the enrolled set and the waitlist (DESIGN.md
     * section 5 "Status lookup"). Read-only. The learner ID is matched ignoring case and
     * surrounding whitespace.
     *
     * @return {@code MISSING_LEARNER_DETAILS} for a null/blank ID, otherwise {@code ENROLLED},
     *         {@code WAITLISTED} or {@code NOT_ENROLLED}
     * @throws IllegalStateException if no course is configured
     */
    public EnrollmentResult getLearnerStatus(String learnerId) {
        requireCourse();
        if (Ids.isBlank(learnerId)) {
            return EnrollmentResult.MISSING_LEARNER_DETAILS;
        }
        String id = Ids.normalize(learnerId);
        if (enrolledLearners.contains(id)) {
            return EnrollmentResult.ENROLLED;
        }
        if (waitlistedLearners.contains(id)) {
            return EnrollmentResult.WAITLISTED;
        }
        return EnrollmentResult.NOT_ENROLLED;
    }

    /**
     * Guard called first by every public method: no operation runs before the course is
     * configured.
     *
     * @throws IllegalStateException if no course is configured
     */
    private Course requireCourse() {
        return configuration.getCourse();
    }

    /**
     * Enrolls the oldest waitlisted learner into the seat just freed, if anyone is waiting.
     * Prerequisites are not re-checked: every queued learner was eligible when queued
     * (DESIGN.md section 5 "Promotion", assumptions A1-A3, policy in section 8).
     */
    private void promoteFromWaitlist() {
        String next = waitlistedLearners.poll();
        if (next != null) {
            enrolledLearners.add(next);
        }
    }

    /** True if {@code courseId} names the course, ignoring case and surrounding whitespace. */
    private static boolean isThisCourse(Course course, String courseId) {
        return courseId != null && course.courseId().equals(Ids.normalize(courseId));
    }

    /** True if the details, a non-blank learner ID and a completed-course set are all present. */
    private static boolean isValidLearner(LearnerDetails learnerDetails) {
        return learnerDetails != null
                && !Ids.isBlank(learnerDetails.learnerId())
                && learnerDetails.completedCourseIds() != null;
    }

    /**
     * True if the learner completed every prerequisite, comparing course IDs ignoring case and
     * surrounding whitespace (a prerequisite {@code "JAVA"} is met by a completed {@code "java"}).
     * The course's prerequisites are already stored normalized.
     */
    private static boolean meetsPrerequisites(Course course, LearnerDetails learnerDetails) {
        return Ids.normalizeAll(learnerDetails.completedCourseIds()).containsAll(course.prerequisiteCourseIds());
    }

    /** Takes a free seat if there is one, otherwise joins the end of the FIFO waitlist. */
    private EnrollmentResult enrollOrWaitlist(Course course, String learnerId) {
        if (enrolledLearners.size() < course.capacity()) {
            enrolledLearners.add(learnerId);
            return EnrollmentResult.ENROLLED;
        }
        waitlistedLearners.add(learnerId);
        return EnrollmentResult.WAITLISTED;
    }
}
