package com.assessment.enrollment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.assessment.enrollment.model.Course;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Course configuration (DESIGN.md section 4 "Course configuration", section 9 items 17-18). */
class CourseConfigurationServiceTest {

    private CourseConfigurationService configuration;

    @BeforeEach
    void setUp() {
        configuration = new CourseConfigurationService();
    }

    @Test
    void storesIdAndPrerequisitesTrimmedInLowerCase() {
        Course course = configuration.configure(" CS201 ", 3, Set.of("JAVA", " Sql "));

        assertEquals("cs201", course.courseId());
        assertEquals(3, course.capacity());
        assertEquals(Set.of("java", "sql"), course.prerequisiteCourseIds());
        assertSame(course, configuration.getCourse());
    }

    @Test
    void mergesPrerequisitesThatDifferOnlyInCase() {
        Course course = configuration.configure("CS201", 1, Set.of("JAVA", "java "));

        assertEquals(Set.of("java"), course.prerequisiteCourseIds());
    }

    @Test
    void acceptsNoPrerequisites() {
        assertTrue(configuration.configure("CS201", 1, Set.of()).prerequisiteCourseIds().isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void rejectsNullOrBlankCourseId(String courseId) {
        assertThrows(IllegalArgumentException.class, () -> configuration.configure(courseId, 1, Set.of()));
        assertFalse(configuration.isConfigured());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsCapacityBelowOne(int capacity) {
        assertThrows(IllegalArgumentException.class, () -> configuration.configure("CS201", capacity, Set.of()));
        assertFalse(configuration.isConfigured());
    }

    @Test
    void rejectsNullOrBlankPrerequisites() {
        assertThrows(IllegalArgumentException.class, () -> configuration.configure("CS201", 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> configuration.configure("CS201", 1, Set.of("JAVA", " ")));
        Set<String> withNull = new HashSet<>();
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> configuration.configure("CS201", 1, withNull));
        assertFalse(configuration.isConfigured());
    }

    @Test
    void validRetryAfterRejectedInputSucceeds() {
        assertThrows(IllegalArgumentException.class, () -> configuration.configure("CS201", 0, Set.of()));

        assertEquals("cs201", configuration.configure("CS201", 1, Set.of()).courseId());
        assertTrue(configuration.isConfigured());
    }

    @Test
    void secondConfigurationIsRejectedAndCourseIsUnchanged() {
        Course first = configuration.configure("CS201", 2, Set.of("CS101"));

        assertThrows(IllegalStateException.class, () -> configuration.configure("CS301", 5, Set.of()));
        assertThrows(IllegalStateException.class, () -> configuration.configure("CS201", 2, Set.of("CS101")));
        assertSame(first, configuration.getCourse());
    }

    @Test
    void secondConfigurationIsRejectedBeforeInputIsValidated() {
        configuration.configure("CS201", 2, Set.of());

        assertThrows(IllegalStateException.class, () -> configuration.configure(null, 0, null));
    }

    @Test
    void notConfiguredUntilConfigureSucceeds() {
        assertFalse(configuration.isConfigured());
        assertThrows(IllegalStateException.class, configuration::getCourse);

        configuration.configure("CS201", 1, Set.of());

        assertTrue(configuration.isConfigured());
    }
}
