package com.example.waitlist.service;

import static com.example.waitlist.model.EnrollmentResult.ALREADY_ENROLLED;
import static com.example.waitlist.model.EnrollmentResult.ALREADY_WAITLISTED;
import static com.example.waitlist.model.EnrollmentResult.ENROLLED;
import static com.example.waitlist.model.EnrollmentResult.INELIGIBLE;
import static com.example.waitlist.model.EnrollmentResult.INVALID_COURSE_ID;
import static com.example.waitlist.model.EnrollmentResult.MISSING_LEARNER_DETAILS;
import static com.example.waitlist.model.EnrollmentResult.WAITLISTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.waitlist.model.Course;
import com.example.waitlist.model.EnrollmentResult;
import com.example.waitlist.model.LearnerDetails;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Enrollment behaviour (DESIGN.md sections 5, 6 and 9). Only {@code enroll} exists in this
 * phase, so state is observed through later {@code enroll} results: a learner who was not
 * stored gets a fresh result instead of ALREADY_*, and a seat that was not taken is still
 * available to the next eligible learner.
 */
class CourseRegistrationServiceTest {

    private static final String COURSE_ID = "CS201";
    private static final String PREREQ = "CS101";

    private CourseRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new CourseRegistrationService(new Course(COURSE_ID, 2, Set.of(PREREQ)));
    }

    private static LearnerDetails eligible(String learnerId) {
        return new LearnerDetails(learnerId, Set.of(PREREQ));
    }

    private static LearnerDetails ineligible(String learnerId) {
        return new LearnerDetails(learnerId, Set.of());
    }

    private EnrollmentResult enroll(LearnerDetails learner) {
        return service.enroll(COURSE_ID, learner);
    }

    private void fillCourse() {
        assertEquals(ENROLLED, enroll(eligible("seat-1")));
        assertEquals(ENROLLED, enroll(eligible("seat-2")));
    }

    @Nested
    class SeatsAndWaitlist {

        @Test
        void eligibleLearnerEnrollsWhenSeatAvailable() {
            assertEquals(ENROLLED, enroll(eligible("alice")));
        }

        @Test
        void eligibleLearnerIsWaitlistedWhenFull() {
            fillCourse();

            assertEquals(WAITLISTED, enroll(eligible("carol")));
            assertEquals(WAITLISTED, enroll(eligible("dave")));
        }

        @Test
        void neverEnrollsMoreThanCapacityAndFillsSeatsInArrivalOrder() {
            service = new CourseRegistrationService(new Course(COURSE_ID, 3, Set.of()));

            for (int i = 1; i <= 10; i++) {
                EnrollmentResult expected = i <= 3 ? ENROLLED : WAITLISTED;
                assertEquals(expected, service.enroll(COURSE_ID, new LearnerDetails("l" + i, Set.of())),
                        "learner l" + i);
            }
        }

        @Test
        void eachStoredLearnerIsReportedInExactlyTheStateTheyWereGiven() {
            fillCourse();
            enroll(eligible("carol"));

            assertEquals(ALREADY_ENROLLED, enroll(eligible("seat-1")));
            assertEquals(ALREADY_ENROLLED, enroll(eligible("seat-2")));
            assertEquals(ALREADY_WAITLISTED, enroll(eligible("carol")));
        }
    }

    @Nested
    class Prerequisites {

        @Test
        void missingPrerequisiteIsIneligibleAndTakesNoSeatOrQueuePlace() {
            assertEquals(INELIGIBLE, enroll(ineligible("ivan")));

            fillCourse(); // both seats were still free
            assertEquals(WAITLISTED, enroll(eligible("carol")));
            // Not stored anywhere: a repeat is evaluated afresh, not reported as ALREADY_*.
            assertEquals(INELIGIBLE, enroll(ineligible("ivan")));
        }

        @Test
        void allPrerequisitesAreRequired() {
            service = new CourseRegistrationService(new Course(COURSE_ID, 2, Set.of("A", "B")));

            assertEquals(INELIGIBLE, service.enroll(COURSE_ID, new LearnerDetails("x", Set.of("A"))));
            assertEquals(ENROLLED, service.enroll(COURSE_ID, new LearnerDetails("y", Set.of("A", "B", "C"))));
        }

        @ParameterizedTest
        @ValueSource(strings = {"java", "JAVA", "Java", " java ", "\tJaVa\n"})
        void prerequisiteMatchesCompletedCourseIgnoringCaseAndSurroundingWhitespace(String completed) {
            service = new CourseRegistrationService(new Course(COURSE_ID, 2, Set.of("JAVA")));

            assertEquals(ENROLLED, service.enroll(COURSE_ID, new LearnerDetails("alice", Set.of(completed))));
        }

        @Test
        void prerequisiteWrittenInAnyCaseMatches() {
            service = new CourseRegistrationService(new Course(COURSE_ID, 2, Set.of(" Java ", "sql")));

            assertEquals(ENROLLED, service.enroll(COURSE_ID, new LearnerDetails("alice", Set.of("JAVA", "SQL"))));
            assertEquals(INELIGIBLE, service.enroll(COURSE_ID, new LearnerDetails("bob", Set.of("java"))));
        }

        @Test
        void prerequisiteWithDifferentInnerTextDoesNotMatch() {
            service = new CourseRegistrationService(new Course(COURSE_ID, 2, Set.of("JAVA")));

            assertEquals(INELIGIBLE, service.enroll(COURSE_ID, new LearnerDetails("alice", Set.of("ja va", "javascript"))));
        }

        @Test
        void courseWithoutPrerequisitesAcceptsEmptyCompletedCourses() {
            service = new CourseRegistrationService(new Course(COURSE_ID, 1, Set.of()));

            assertEquals(ENROLLED, service.enroll(COURSE_ID, new LearnerDetails("alice", Set.of())));
        }
    }

    @Nested
    class DuplicateRequests {

        @Test
        void repeatedRequestFromEnrolledLearnerTakesNoExtraSeat() {
            assertEquals(ENROLLED, enroll(eligible("alice")));
            assertEquals(ALREADY_ENROLLED, enroll(eligible("alice")));

            assertEquals(ENROLLED, enroll(eligible("bob"))); // second seat still free
            assertEquals(WAITLISTED, enroll(eligible("carol")));
        }

        @Test
        void repeatedRequestFromWaitlistedLearnerIsNotQueuedAgain() {
            fillCourse();
            assertEquals(WAITLISTED, enroll(eligible("carol")));

            assertEquals(ALREADY_WAITLISTED, enroll(eligible("carol")));
            assertEquals(ALREADY_WAITLISTED, enroll(eligible("carol")));
            // Queue position is checked in the drop/promotion phase (DESIGN.md section 12).
        }

        @Test
        void enrolledLearnerIsReportedBeforePrerequisitesAreRechecked() {
            assertEquals(ENROLLED, enroll(eligible("alice")));

            // DESIGN.md section 5 order: rule 3 (already enrolled) precedes rule 5 (eligibility).
            assertEquals(ALREADY_ENROLLED, enroll(ineligible("alice")));
        }
    }

    @Nested
    class InvalidInput {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"CS999", "CS2011", "CS 201", "   "})
        void unknownCourseIdIsRejectedWithoutChangingState(String courseId) {
            assertEquals(INVALID_COURSE_ID, service.enroll(courseId, eligible("alice")));

            assertEquals(ENROLLED, enroll(eligible("alice"))); // alice was not stored
        }

        @ParameterizedTest
        @ValueSource(strings = {"cs201", "Cs201", " CS201", "cs201 ", "\tcS201\n"})
        void courseIdMatchesIgnoringCaseAndSurroundingWhitespace(String courseId) {
            assertEquals(ENROLLED, service.enroll(courseId, eligible("alice")));
            assertEquals(ALREADY_ENROLLED, service.enroll(COURSE_ID, eligible("alice")));
        }

        @ParameterizedTest
        @ValueSource(strings = {"alice", "ALICE", " Alice", "alice ", "\taLiCe\n"})
        void learnerIdMatchesIgnoringCaseAndSurroundingWhitespace(String sameLearner) {
            assertEquals(ENROLLED, enroll(eligible("Alice")));

            assertEquals(ALREADY_ENROLLED, enroll(eligible(sameLearner)));
            assertEquals(ENROLLED, enroll(eligible("bob"))); // no second seat was used for alice
        }

        @Test
        void waitlistedLearnerIdMatchesIgnoringCaseAndSurroundingWhitespace() {
            fillCourse();
            assertEquals(WAITLISTED, enroll(eligible("Carol")));

            assertEquals(ALREADY_WAITLISTED, enroll(eligible("  CAROL ")));
        }

        @Test
        void innerWhitespaceStillDistinguishesLearners() {
            assertEquals(ENROLLED, enroll(eligible("mary ann")));
            assertEquals(ENROLLED, enroll(eligible("maryann")));
        }

        @Test
        void courseIdIsCheckedBeforeLearnerDetails() {
            assertEquals(INVALID_COURSE_ID, service.enroll("CS999", null));
        }

        @Test
        void nullLearnerDetailsAreMissing() {
            assertEquals(MISSING_LEARNER_DETAILS, enroll(null));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        void nullOrBlankLearnerIdIsMissing(String learnerId) {
            assertEquals(MISSING_LEARNER_DETAILS, enroll(eligible(learnerId)));
        }

        @Test
        void nullCompletedCoursesAreMissing() {
            assertEquals(MISSING_LEARNER_DETAILS, enroll(new LearnerDetails("alice", null)));
        }

        @Test
        void missingDetailsChangeNoState() {
            enroll(null);
            enroll(eligible(" "));
            enroll(new LearnerDetails("alice", null));

            assertEquals(ENROLLED, enroll(eligible("alice"))); // alice was not stored
            assertEquals(ENROLLED, enroll(eligible("bob")));   // no seat was taken
            assertEquals(WAITLISTED, enroll(eligible("carol")));
        }

        @Test
        void missingDetailsAreReportedBeforeWaitlistDuplicateCheck() {
            fillCourse();
            assertEquals(WAITLISTED, enroll(eligible("carol")));

            // DESIGN.md section 5 order: rule 2 (missing details) precedes rule 4 (already waitlisted).
            assertEquals(MISSING_LEARNER_DETAILS, enroll(new LearnerDetails("carol", null)));
        }
    }
}
