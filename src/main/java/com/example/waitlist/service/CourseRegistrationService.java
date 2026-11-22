package com.example.waitlist.service;

import com.example.waitlist.model.Course;
import com.example.waitlist.model.DropResult;
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
     * @param course the single offering this instance manages; it is validated and stored in
     *               lower case through {@link #createCourse}, so both paths stay in sync
     * @throws NullPointerException     if {@code course} is null
     * @throws IllegalArgumentException if the course is invalid (see {@link #createCourse})
     */
    public CourseRegistrationService(Course course) {
        Objects.requireNonNull(course, "course");
        this.course = createCourse(course.courseId(), course.capacity(), course.prerequisiteCourseIds());
    }

    /**
     * Creates a course with its prerequisites, storing the course ID and every prerequisite ID
     * trimmed and in lower case, whatever case they were entered in.
     *
     * <p>Invalid input creates no course: an {@link IllegalArgumentException} is thrown when the
     * course ID is null or blank, the capacity is below 1, the prerequisite set is null, or a
     * prerequisite ID is null or blank.
     *
     * @return the normalized course
     */
    public static Course createCourse(String courseId, int capacity, Set<String> prerequisiteCourseIds) {
        if (courseId == null || courseId.isBlank()) {
            throw new IllegalArgumentException("course ID must not be null or blank");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1, was " + capacity);
        }
        if (prerequisiteCourseIds == null) {
            throw new IllegalArgumentException("prerequisite course IDs must not be null");
        }
        Set<String> prerequisites = new HashSet<>();
        for (String prerequisite : prerequisiteCourseIds) {
            if (prerequisite == null || prerequisite.isBlank()) {
                throw new IllegalArgumentException("prerequisite course ID must not be null or blank");
            }
            prerequisites.add(normalizeId(prerequisite));
        }
        return new Course(normalizeId(courseId), capacity, prerequisites);
    }

    /**
     * Enrolls an eligible learner, or waitlists them when the course is full.
     * Checks run in the order of DESIGN.md section 5; every non-success result leaves
     * the state unchanged.
     */
    public EnrollmentResult enroll(String courseId, LearnerDetails learnerDetails) {
        requireCourse();
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

    /**
     * Drops a learner (DESIGN.md section 5). Dropping an enrolled learner frees their seat and
     * promotes the oldest waitlisted learner, if any; dropping a waitlisted learner only removes
     * them from the queue. The learner ID is matched ignoring case and surrounding whitespace.
     * A null/blank or unknown ID, including a repeated drop, changes nothing.
     */
    public DropResult drop(String learnerId) {
        requireCourse();
        if (learnerId == null || learnerId.isBlank()) {
            return DropResult.LEARNER_DETAIL_NOT_FOUND;
        }
        String id = normalizeId(learnerId);
        if (enrolledLearners.remove(id)) {
            promoteFromWaitlist();
            return DropResult.DROPPED;
        }
        if (waitlistedLearners.remove(id)) {
            return DropResult.DROPPED;
        }
        return DropResult.LEARNER_DETAIL_NOT_FOUND;
    }

    /** Seats still free: capacity minus enrolled learners (DESIGN.md section 4). */
    public int getAvailableSeats() {
        requireCourse();
        return course.capacity() - enrolledLearners.size();
    }

    /**
     * Current registration status, derived from the enrolled set and the waitlist (DESIGN.md
     * section 5 "Status lookup"). Read-only. The learner ID is matched ignoring case and
     * surrounding whitespace.
     *
     * @return {@code MISSING_LEARNER_DETAILS} for a null/blank ID, otherwise {@code ENROLLED},
     *         {@code WAITLISTED} or {@code NOT_ENROLLED}
     */
    public EnrollmentResult getLearnerStatus(String learnerId) {
        requireCourse();
        if (learnerId == null || learnerId.isBlank()) {
            return EnrollmentResult.MISSING_LEARNER_DETAILS;
        }
        String id = normalizeId(learnerId);
        if (enrolledLearners.contains(id)) {
            return EnrollmentResult.ENROLLED;
        }
        if (waitlistedLearners.contains(id)) {
            return EnrollmentResult.WAITLISTED;
        }
        return EnrollmentResult.NOT_ENROLLED;
    }

    /**
     * Guard called first by every public instance method: no operation runs unless the course
     * has been created. The constructor already guarantees this, so the check is defensive.
     *
     * @throws IllegalStateException if no course is present
     */
    private void requireCourse() {
        if (course == null) {
            throw new IllegalStateException("course has not been created");
        }
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

    /** True if {@code courseId} names this offering, ignoring case and surrounding whitespace. */
    private boolean isValidCourse(String courseId) {
        return courseId != null && course.courseId().equals(normalizeId(courseId));
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
        // Prerequisites are already stored normalized by createCourse.
        return normalizeIds(learnerDetails.completedCourseIds()).containsAll(course.prerequisiteCourseIds());
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
