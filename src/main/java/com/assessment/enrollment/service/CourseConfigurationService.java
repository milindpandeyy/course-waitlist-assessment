package com.assessment.enrollment.service;

import com.assessment.enrollment.model.Course;
import java.util.Set;

/**
 * Configures the single course offering, once (DESIGN.md section 4 "Course configuration").
 *
 * <p>Not thread-safe: single-threaded use only (DESIGN.md section 2, assumption A4).
 */
public final class CourseConfigurationService {

    private Course course;

    /**
     * Configures the course with its prerequisites, storing the course ID and every prerequisite
     * ID trimmed and in lower case, whatever case they were entered in.
     *
     * @return the stored, normalized course
     * @throws IllegalStateException    if a course is already configured; it stays unchanged
     * @throws IllegalArgumentException if the course ID is null or blank, the capacity is below
     *                                  1, the prerequisite set is null, or a prerequisite ID is
     *                                  null or blank; nothing is configured
     */
    public Course configure(String courseId, int capacity, Set<String> prerequisiteCourseIds) {
        if (course != null) {
            throw new IllegalStateException("course " + course.courseId() + " is already configured");
        }
        if (Ids.isBlank(courseId)) {
            throw new IllegalArgumentException("course ID must not be null or blank");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1, was " + capacity);
        }
        if (prerequisiteCourseIds == null) {
            throw new IllegalArgumentException("prerequisite course IDs must not be null");
        }
        for (String prerequisite : prerequisiteCourseIds) {
            if (Ids.isBlank(prerequisite)) {
                throw new IllegalArgumentException("prerequisite course ID must not be null or blank");
            }
        }
        course = new Course(Ids.normalize(courseId), capacity, Ids.normalizeAll(prerequisiteCourseIds));
        return course;
    }

    /** True once a course has been configured. */
    public boolean isConfigured() {
        return course != null;
    }

    /**
     * @return the configured course
     * @throws IllegalStateException if no course has been configured yet
     */
    public Course getCourse() {
        if (course == null) {
            throw new IllegalStateException("course has not been configured");
        }
        return course;
    }
}
