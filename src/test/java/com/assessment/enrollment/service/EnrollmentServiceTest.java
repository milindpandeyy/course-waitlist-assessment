package com.assessment.enrollment.service;

import static com.assessment.enrollment.model.DropResult.DROPPED;
import static com.assessment.enrollment.model.DropResult.LEARNER_DETAIL_NOT_FOUND;
import static com.assessment.enrollment.model.EnrollmentResult.ALREADY_ENROLLED;
import static com.assessment.enrollment.model.EnrollmentResult.ALREADY_WAITLISTED;
import static com.assessment.enrollment.model.EnrollmentResult.ENROLLED;
import static com.assessment.enrollment.model.EnrollmentResult.INELIGIBLE;
import static com.assessment.enrollment.model.EnrollmentResult.INVALID_COURSE_ID;
import static com.assessment.enrollment.model.EnrollmentResult.MISSING_LEARNER_DETAILS;
import static com.assessment.enrollment.model.EnrollmentResult.NOT_ENROLLED;
import static com.assessment.enrollment.model.EnrollmentResult.WAITLISTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.assessment.enrollment.model.EnrollmentResult;
import com.assessment.enrollment.model.LearnerDetails;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Enrollment, drop, status lookup and available seats (DESIGN.md sections 4-6 and 9). Many
 * tests observe state through later {@code enroll} results: a learner who was not stored gets
 * a fresh result instead of ALREADY_*, and a seat that was not taken is still available to the
 * next eligible learner.
 */
class EnrollmentServiceTest {

    private static final String COURSE_ID = "CS201";
    private static final String PREREQ = "CS101";

    private EnrollmentService service;

    @BeforeEach
    void setUp() {
        service = serviceFor(COURSE_ID, 2, Set.of(PREREQ));
    }

    /** An enrollment service over a freshly configured course. */
    private static EnrollmentService serviceFor(String courseId, int capacity, Set<String> prerequisites) {
        CourseConfigurationService configuration = new CourseConfigurationService();
        configuration.configure(courseId, capacity, prerequisites);
        return new EnrollmentService(configuration);
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
            service = serviceFor(COURSE_ID, 3, Set.of());

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
            service = serviceFor(COURSE_ID, 2, Set.of("A", "B"));

            assertEquals(INELIGIBLE, service.enroll(COURSE_ID, new LearnerDetails("x", Set.of("A"))));
            assertEquals(ENROLLED, service.enroll(COURSE_ID, new LearnerDetails("y", Set.of("A", "B", "C"))));
        }

        @ParameterizedTest
        @ValueSource(strings = {"java", "JAVA", "Java", " java ", "\tJaVa\n"})
        void prerequisiteMatchesCompletedCourseIgnoringCaseAndSurroundingWhitespace(String completed) {
            service = serviceFor(COURSE_ID, 2, Set.of("JAVA"));

            assertEquals(ENROLLED, service.enroll(COURSE_ID, new LearnerDetails("alice", Set.of(completed))));
        }

        @Test
        void prerequisiteWrittenInAnyCaseMatches() {
            service = serviceFor(COURSE_ID, 2, Set.of(" Java ", "sql"));

            assertEquals(ENROLLED, service.enroll(COURSE_ID, new LearnerDetails("alice", Set.of("JAVA", "SQL"))));
            assertEquals(INELIGIBLE, service.enroll(COURSE_ID, new LearnerDetails("bob", Set.of("java"))));
        }

        @Test
        void prerequisiteWithDifferentInnerTextDoesNotMatch() {
            service = serviceFor(COURSE_ID, 2, Set.of("JAVA"));

            assertEquals(INELIGIBLE, service.enroll(COURSE_ID, new LearnerDetails("alice", Set.of("ja va", "javascript"))));
        }

        @Test
        void courseWithoutPrerequisitesAcceptsEmptyCompletedCourses() {
            service = serviceFor(COURSE_ID, 1, Set.of());

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

        @Test
        void waitlistedLearnerIsReportedBeforePrerequisitesAreRechecked() {
            fillCourse();
            assertEquals(WAITLISTED, enroll(eligible("carol")));

            // DESIGN.md section 5 order: rule 4 (already waitlisted) precedes rule 5 (eligibility).
            assertEquals(ALREADY_WAITLISTED, enroll(ineligible("carol")));
            assertEquals(WAITLISTED, service.getLearnerStatus("carol"));
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
        void courseConfiguredInMixedCaseMatchesAnyCase() {
            service = serviceFor(" Cs201 ", 1, Set.of(" JAVA "));

            assertEquals(ENROLLED, service.enroll("CS201", new LearnerDetails("alice", Set.of("java"))));
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

    /**
     * Drop and FIFO promotion (DESIGN.md sections 5, 6 and 9 items 6-10, 15). Most tests use a
     * one-seat course so that "who holds the seat" identifies exactly who was promoted. Without
     * status lookup (a later phase), state is read through {@code enroll} and {@code drop}
     * results: ALREADY_ENROLLED means enrolled, ALREADY_WAITLISTED means still queued, and a
     * second drop returning DROPPED would reveal a leftover copy of the learner.
     */
    @Nested
    class DropAndPromotion {

        @BeforeEach
        void oneSeat() {
            service = serviceFor(COURSE_ID, 1, Set.of(PREREQ));
        }

        @Test
        void droppingEnrolledLearnerWithEmptyWaitlistFreesTheSeat() {
            assertEquals(ENROLLED, enroll(eligible("alice")));

            assertEquals(DROPPED, service.drop("alice"));
            assertEquals(ENROLLED, enroll(eligible("bob")));
        }

        @Test
        void droppingEnrolledLearnerPromotesOldestWaitlistedLearner() {
            enroll(eligible("alice"));
            enroll(eligible("bob"));
            enroll(eligible("carol"));

            assertEquals(DROPPED, service.drop("alice"));

            assertEquals(ALREADY_ENROLLED, enroll(eligible("bob")));
            assertEquals(ALREADY_WAITLISTED, enroll(eligible("carol")));
        }

        @Test
        void promotionFollowsArrivalOrderAcrossSuccessiveDrops() {
            enroll(eligible("seat"));
            for (String learner : new String[] {"w1", "w2", "w3", "w4"}) {
                assertEquals(WAITLISTED, enroll(eligible(learner)));
            }

            String holder = "seat";
            for (String expectedNext : new String[] {"w1", "w2", "w3", "w4"}) {
                assertEquals(DROPPED, service.drop(holder));
                assertEquals(ALREADY_ENROLLED, enroll(eligible(expectedNext)), expectedNext + " promoted");
                holder = expectedNext;
            }
            assertEquals(DROPPED, service.drop(holder));
            assertEquals(ENROLLED, enroll(eligible("late"))); // queue empty, seat free
        }

        @Test
        void eachDropPromotesExactlyOneLearner() {
            service = serviceFor(COURSE_ID, 2, Set.of(PREREQ));
            enroll(eligible("a"));
            enroll(eligible("b"));
            enroll(eligible("w1"));
            enroll(eligible("w2"));

            assertEquals(DROPPED, service.drop("a"));

            assertEquals(ALREADY_ENROLLED, enroll(eligible("w1")));
            assertEquals(ALREADY_WAITLISTED, enroll(eligible("w2"))); // capacity 2 still respected
            assertEquals(WAITLISTED, enroll(eligible("new")));
        }

        @Test
        void droppingWaitlistedLearnerRemovesThemWithoutPromotionOrFreeingASeat() {
            enroll(eligible("alice"));
            enroll(eligible("bob"));
            enroll(eligible("carol"));

            assertEquals(DROPPED, service.drop("bob"));

            assertEquals(ALREADY_ENROLLED, enroll(eligible("alice")));     // seat untouched
            assertEquals(ALREADY_WAITLISTED, enroll(eligible("carol")));   // not promoted
            assertEquals(DROPPED, service.drop("alice"));
            assertEquals(ALREADY_ENROLLED, enroll(eligible("carol")));     // carol is now first
        }

        @Test
        void droppingWaitlistedLearnerKeepsOrderOfOthers() {
            enroll(eligible("seat"));
            enroll(eligible("w1"));
            enroll(eligible("w2"));
            enroll(eligible("w3"));

            assertEquals(DROPPED, service.drop("w2"));

            service.drop("seat");
            assertEquals(ALREADY_ENROLLED, enroll(eligible("w1")));
            service.drop("w1");
            assertEquals(ALREADY_ENROLLED, enroll(eligible("w3")));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "unknown"})
        void invalidOrUnknownDropChangesNothing(String learnerId) {
            enroll(eligible("alice"));
            enroll(eligible("bob"));

            assertEquals(LEARNER_DETAIL_NOT_FOUND, service.drop(learnerId));

            assertEquals(ALREADY_ENROLLED, enroll(eligible("alice")));
            assertEquals(ALREADY_WAITLISTED, enroll(eligible("bob")));
            assertEquals(WAITLISTED, enroll(eligible("carol"))); // no seat was freed
        }

        @Test
        void repeatedDropOfEnrolledLearnerDoesNotFreeAnotherSeatOrPromoteAgain() {
            enroll(eligible("alice"));
            enroll(eligible("bob"));
            enroll(eligible("carol"));

            assertEquals(DROPPED, service.drop("alice"));
            assertEquals(LEARNER_DETAIL_NOT_FOUND, service.drop("alice"));
            assertEquals(LEARNER_DETAIL_NOT_FOUND, service.drop(" ALICE "));

            assertEquals(ALREADY_ENROLLED, enroll(eligible("bob")));
            assertEquals(ALREADY_WAITLISTED, enroll(eligible("carol")));
        }

        @Test
        void repeatedDropOfWaitlistedLearnerIsNotFound() {
            enroll(eligible("alice"));
            enroll(eligible("bob"));

            assertEquals(DROPPED, service.drop("bob"));
            assertEquals(LEARNER_DETAIL_NOT_FOUND, service.drop("bob"));
        }

        @Test
        void promotedLearnerIsNoLongerWaitlisted() {
            enroll(eligible("alice"));
            enroll(eligible("bob"));
            service.drop("alice");

            // If bob were both enrolled and still queued, the second drop would return DROPPED.
            assertEquals(DROPPED, service.drop("bob"));
            assertEquals(LEARNER_DETAIL_NOT_FOUND, service.drop("bob"));
        }

        @Test
        void reEnrollmentAfterDropJoinsEndOfWaitlist() {
            enroll(eligible("alice"));
            enroll(eligible("bob"));
            service.drop("alice");                                   // bob promoted

            assertEquals(WAITLISTED, enroll(eligible("carol")));
            assertEquals(WAITLISTED, enroll(eligible("alice")));     // new attempt, behind carol

            service.drop("bob");
            assertEquals(ALREADY_ENROLLED, enroll(eligible("carol")));
            assertEquals(ALREADY_WAITLISTED, enroll(eligible("alice")));
        }

        @Test
        void reEnrollmentAfterDropTakesAFreeSeat() {
            assertEquals(ENROLLED, enroll(eligible("alice")));
            assertEquals(DROPPED, service.drop("alice"));

            assertEquals(ENROLLED, enroll(eligible("alice")));
            assertEquals(ENROLLED, service.getLearnerStatus("alice"));
            assertEquals(0, service.getAvailableSeats());
        }

        @Test
        void reEnrollmentAfterDropRechecksPrerequisites() {
            enroll(eligible("alice"));
            service.drop("alice");

            assertEquals(INELIGIBLE, enroll(ineligible("alice")));
        }

        @Test
        void duplicateWaitlistRequestDoesNotMoveQueuePosition() {
            enroll(eligible("seat"));
            enroll(eligible("bob"));
            enroll(eligible("carol"));

            assertEquals(ALREADY_WAITLISTED, enroll(eligible("bob")));
            service.drop("seat");

            assertEquals(ALREADY_ENROLLED, enroll(eligible("bob")));
            assertEquals(ALREADY_WAITLISTED, enroll(eligible("carol")));
        }

        @Test
        void dropMatchesLearnerIdIgnoringCaseAndSurroundingWhitespace() {
            enroll(eligible("Alice"));
            enroll(eligible("Bob"));

            assertEquals(DROPPED, service.drop("  ALICE "));
            assertEquals(ALREADY_ENROLLED, enroll(eligible("bob")));
            assertEquals(DROPPED, service.drop("\tbOB\n"));
        }

        @Test
        void seatsNeverExceedCapacityThroughDropsAndPromotions() {
            service = serviceFor(COURSE_ID, 2, Set.of(PREREQ));
            for (String learner : new String[] {"a", "b", "c", "d", "e"}) {
                enroll(eligible(learner));
            }

            service.drop("a");                       // c promoted
            service.drop("d");                       // waitlisted drop, no promotion
            service.drop("b");                       // e promoted

            assertEquals(ALREADY_ENROLLED, enroll(eligible("c")));
            assertEquals(ALREADY_ENROLLED, enroll(eligible("e")));
            assertEquals(WAITLISTED, enroll(eligible("f"))); // both seats taken, not three
        }
    }

    /** No operation before configuration (DESIGN.md section 4 "Enrollment", section 9 item 19). */
    @Nested
    class BeforeConfiguration {

        private CourseConfigurationService configuration;

        @BeforeEach
        void unconfigured() {
            configuration = new CourseConfigurationService();
            service = new EnrollmentService(configuration);
        }

        @Test
        void constructorRejectsNullConfiguration() {
            assertThrows(NullPointerException.class, () -> new EnrollmentService(null));
        }

        @Test
        void everyOperationIsRejectedBeforeACourseIsConfigured() {
            assertThrows(IllegalStateException.class, () -> service.enroll(COURSE_ID, eligible("alice")));
            assertThrows(IllegalStateException.class, () -> service.drop("alice"));
            assertThrows(IllegalStateException.class, () -> service.getLearnerStatus("alice"));
            assertThrows(IllegalStateException.class, () -> service.getAvailableSeats());
        }

        @Test
        void operationsWorkOnceTheCourseIsConfiguredAndRejectedAttemptsLeftNoState() {
            assertThrows(IllegalStateException.class, () -> service.enroll(COURSE_ID, eligible("alice")));

            configuration.configure(COURSE_ID, 1, Set.of(PREREQ));

            assertEquals(1, service.getAvailableSeats());
            assertEquals(NOT_ENROLLED, service.getLearnerStatus("alice"));
            assertEquals(ENROLLED, enroll(eligible("alice")));
        }
    }

    /** Status lookup and available seats (DESIGN.md sections 4, 5, 6 and 9 items 13-15). */
    @Nested
    class StatusAndSeats {

        @Test
        void newCourseHasAllSeatsFree() {
            assertEquals(2, service.getAvailableSeats());
        }

        @Test
        void enrolledLearnerIsEnrolled() {
            enroll(eligible("alice"));

            assertEquals(ENROLLED, service.getLearnerStatus("alice"));
        }

        @Test
        void waitlistedLearnerIsWaitlisted() {
            fillCourse();
            enroll(eligible("carol"));

            assertEquals(WAITLISTED, service.getLearnerStatus("carol"));
        }

        @Test
        void unregisteredLearnerIsNotEnrolled() {
            assertEquals(NOT_ENROLLED, service.getLearnerStatus("nobody"));
        }

        @Test
        void ineligibleLearnerIsNotEnrolled() {
            enroll(ineligible("ivan"));

            assertEquals(NOT_ENROLLED, service.getLearnerStatus("ivan"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        void nullOrBlankIdIsMissingLearnerDetails(String learnerId) {
            assertEquals(MISSING_LEARNER_DETAILS, service.getLearnerStatus(learnerId));
        }

        @Test
        void statusMatchesLearnerIdIgnoringCaseAndSurroundingWhitespace() {
            enroll(eligible("Alice"));

            assertEquals(ENROLLED, service.getLearnerStatus("  ALICE "));
            assertEquals(ENROLLED, service.getLearnerStatus("alice"));
        }

        @Test
        void promotedLearnerBecomesEnrolledAndDroppedLearnerIsNotEnrolled() {
            fillCourse();
            enroll(eligible("carol"));
            enroll(eligible("dave"));

            service.drop("seat-1");

            assertEquals(NOT_ENROLLED, service.getLearnerStatus("seat-1"));
            assertEquals(ENROLLED, service.getLearnerStatus("carol"));
            assertEquals(WAITLISTED, service.getLearnerStatus("dave"));
        }

        @Test
        void droppedWaitlistedLearnerIsNotEnrolled() {
            fillCourse();
            enroll(eligible("carol"));

            service.drop("carol");

            assertEquals(NOT_ENROLLED, service.getLearnerStatus("carol"));
        }

        @Test
        void availableSeatsFollowEnrollmentsDropsAndPromotions() {
            assertEquals(2, service.getAvailableSeats());

            enroll(eligible("a"));
            assertEquals(1, service.getAvailableSeats());
            enroll(eligible("b"));
            assertEquals(0, service.getAvailableSeats());
            enroll(eligible("w1"));                  // waitlisted: no seat used
            assertEquals(0, service.getAvailableSeats());

            service.drop("a");                       // w1 promoted into the freed seat
            assertEquals(0, service.getAvailableSeats());
            service.drop("w1");                      // nobody waiting: seat stays free
            assertEquals(1, service.getAvailableSeats());
            service.drop("b");
            assertEquals(2, service.getAvailableSeats());
        }

        @Test
        void availableSeatsUnchangedByRejectedRequestsWaitlistDropsAndRepeatedDrops() {
            enroll(eligible("a"));
            enroll(eligible("b"));
            enroll(eligible("w1"));

            enroll(ineligible("ivan"));
            service.enroll("CS999", eligible("x"));
            enroll(null);
            enroll(eligible("a"));                   // duplicate
            service.drop("w1");                      // waitlisted drop
            service.drop("w1");                      // repeated drop
            service.drop("unknown");

            assertEquals(0, service.getAvailableSeats());
        }

        @Test
        void statusLookupDoesNotChangeState() {
            fillCourse();
            enroll(eligible("carol"));

            for (int i = 0; i < 3; i++) {
                service.getLearnerStatus("seat-1");
                service.getLearnerStatus("carol");
                service.getLearnerStatus("nobody");
                service.getLearnerStatus(null);
            }

            assertEquals(0, service.getAvailableSeats());
            assertEquals(ENROLLED, service.getLearnerStatus("seat-1"));
            assertEquals(WAITLISTED, service.getLearnerStatus("carol"));
            assertEquals(NOT_ENROLLED, service.getLearnerStatus("nobody"));
        }

        @Test
        void invariantsHoldAfterMixedOperations() {
            String[] learners = {"a", "b", "c", "d", "e", "f"};
            for (String learner : learners) {
                enroll(eligible(learner));
            }
            service.drop("b");
            service.drop("e");
            service.drop("a");
            enroll(eligible("a"));

            int enrolled = 0;
            for (String learner : learners) {
                EnrollmentResult status = service.getLearnerStatus(learner);
                if (status == ENROLLED) {
                    enrolled++;
                }
            }
            assertEquals(2, enrolled, "enrolled count equals capacity");
            assertEquals(0, service.getAvailableSeats());
            assertEquals(ENROLLED, service.getLearnerStatus("c"));
            assertEquals(ENROLLED, service.getLearnerStatus("d"));
            assertEquals(WAITLISTED, service.getLearnerStatus("f"));
            assertEquals(WAITLISTED, service.getLearnerStatus("a"));
            assertEquals(NOT_ENROLLED, service.getLearnerStatus("b"));
            assertEquals(NOT_ENROLLED, service.getLearnerStatus("e"));
        }
    }
}
