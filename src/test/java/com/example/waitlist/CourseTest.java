package com.example.waitlist;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CourseTest {

    private static final StudentId ALICE = StudentId.of("alice");
    private static final StudentId BOB = StudentId.of("bob");
    private static final StudentId CAROL = StudentId.of("carol");
    private static final StudentId DAVE = StudentId.of("dave");

    private static Course courseWithCapacity(int capacity) {
        return new Course("CS101", "Intro to CS", capacity);
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @ParameterizedTest
        @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
        void rejectsNonPositiveCapacity(int capacity) {
            assertThrows(IllegalArgumentException.class, () -> courseWithCapacity(capacity));
        }

        @Test
        void rejectsBlankIdOrTitle() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new Course(" ", "t", 1)),
                    () -> assertThrows(IllegalArgumentException.class, () -> new Course("id", "", 1)),
                    () -> assertThrows(NullPointerException.class, () -> new Course(null, "t", 1)));
        }

        @Test
        void startsEmpty() {
            Course course = courseWithCapacity(3);
            assertEquals(0, course.enrolledCount());
            assertEquals(3, course.availableSeats());
            assertFalse(course.isFull());
            assertTrue(course.waitlistedStudents().isEmpty());
        }
    }

    @Nested
    @DisplayName("enroll")
    class Enroll {

        @Test
        void enrollsWhileSeatsAreFree() {
            Course course = courseWithCapacity(2);
            assertEquals(EnrollmentResult.ENROLLED, course.enroll(ALICE));
            assertEquals(EnrollmentResult.ENROLLED, course.enroll(BOB));
            assertEquals(List.of(ALICE, BOB), course.enrolledStudents());
            assertTrue(course.isFull());
        }

        @Test
        void waitlistsInArrivalOrderOnceFull() {
            Course course = courseWithCapacity(1);
            course.enroll(ALICE);
            assertEquals(EnrollmentResult.WAITLISTED, course.enroll(BOB));
            assertEquals(EnrollmentResult.WAITLISTED, course.enroll(CAROL));
            assertEquals(List.of(BOB, CAROL), course.waitlistedStudents());
            assertEquals(OptionalInt.of(1), course.waitlistPosition(BOB));
            assertEquals(OptionalInt.of(2), course.waitlistPosition(CAROL));
        }

        @Test
        void enrollingTwiceIsANoOp() {
            Course course = courseWithCapacity(1);
            course.enroll(ALICE);
            assertEquals(EnrollmentResult.ALREADY_ENROLLED, course.enroll(ALICE));
            assertEquals(1, course.enrolledCount());
        }

        @Test
        void reEnrollingWhileWaitlistedKeepsPosition() {
            Course course = courseWithCapacity(1);
            course.enroll(ALICE);
            course.enroll(BOB);
            course.enroll(CAROL);
            assertEquals(EnrollmentResult.ALREADY_WAITLISTED, course.enroll(BOB));
            assertEquals(List.of(BOB, CAROL), course.waitlistedStudents());
        }

        @Test
        void studentIdsAreComparedByTrimmedValue() {
            Course course = courseWithCapacity(1);
            course.enroll(StudentId.of("alice"));
            assertEquals(EnrollmentResult.ALREADY_ENROLLED, course.enroll(StudentId.of("  alice ")));
        }
    }

    @Nested
    @DisplayName("drop")
    class Drop {

        @Test
        void droppingEnrolledStudentPromotesHeadOfWaitlist() {
            Course course = courseWithCapacity(1);
            course.enroll(ALICE);
            course.enroll(BOB);
            course.enroll(CAROL);

            DropResult result = course.drop(ALICE);

            assertEquals(DropResult.Outcome.LEFT_COURSE, result.outcome());
            assertEquals(Optional.of(BOB), result.promoted());
            assertEquals(List.of(BOB), course.enrolledStudents());
            assertEquals(List.of(CAROL), course.waitlistedStudents());
            assertEquals(OptionalInt.of(1), course.waitlistPosition(CAROL));
            assertEquals(EnrollmentStatus.NOT_REGISTERED, course.statusOf(ALICE));
        }

        @Test
        void droppingWithEmptyWaitlistFreesASeat() {
            Course course = courseWithCapacity(2);
            course.enroll(ALICE);
            course.enroll(BOB);

            DropResult result = course.drop(ALICE);

            assertEquals(DropResult.Outcome.LEFT_COURSE, result.outcome());
            assertTrue(result.promoted().isEmpty());
            assertEquals(1, course.availableSeats());
            assertEquals(EnrollmentResult.ENROLLED, course.enroll(CAROL));
        }

        @Test
        void leavingTheWaitlistDoesNotPromoteAnyone() {
            Course course = courseWithCapacity(1);
            course.enroll(ALICE);
            course.enroll(BOB);
            course.enroll(CAROL);

            DropResult result = course.drop(BOB);

            assertEquals(DropResult.Outcome.LEFT_WAITLIST, result.outcome());
            assertTrue(result.promoted().isEmpty());
            assertEquals(List.of(ALICE), course.enrolledStudents());
            assertEquals(List.of(CAROL), course.waitlistedStudents());
        }

        @Test
        void droppingUnknownStudentIsANoOp() {
            Course course = courseWithCapacity(1);
            course.enroll(ALICE);
            assertEquals(DropResult.Outcome.NOT_REGISTERED, course.drop(DAVE).outcome());
            assertEquals(List.of(ALICE), course.enrolledStudents());
        }

        @Test
        void droppedStudentRejoiningGoesToBackOfWaitlist() {
            Course course = courseWithCapacity(1);
            course.enroll(ALICE);
            course.enroll(BOB);
            course.drop(ALICE); // Bob promoted

            assertEquals(EnrollmentResult.WAITLISTED, course.enroll(CAROL));
            assertEquals(EnrollmentResult.WAITLISTED, course.enroll(ALICE));
            assertEquals(List.of(CAROL, ALICE), course.waitlistedStudents());
        }

        @Test
        void promotionsCascadeInFifoOrder() {
            Course course = courseWithCapacity(2);
            List.of(ALICE, BOB, CAROL, DAVE).forEach(course::enroll);

            assertEquals(Optional.of(CAROL), course.drop(ALICE).promoted());
            assertEquals(Optional.of(DAVE), course.drop(BOB).promoted());
            assertEquals(List.of(CAROL, DAVE), course.enrolledStudents());
            assertTrue(course.waitlistedStudents().isEmpty());
        }
    }

    @Nested
    @DisplayName("queries")
    class Queries {

        @Test
        void statusReflectsEachState() {
            Course course = courseWithCapacity(1);
            course.enroll(ALICE);
            course.enroll(BOB);
            assertAll(
                    () -> assertEquals(EnrollmentStatus.ENROLLED, course.statusOf(ALICE)),
                    () -> assertEquals(EnrollmentStatus.WAITLISTED, course.statusOf(BOB)),
                    () -> assertEquals(EnrollmentStatus.NOT_REGISTERED, course.statusOf(CAROL)),
                    () -> assertEquals(OptionalInt.empty(), course.waitlistPosition(ALICE)));
        }

        @Test
        void snapshotsAreImmutableAndDetached() {
            Course course = courseWithCapacity(1);
            course.enroll(ALICE);
            List<StudentId> snapshot = course.enrolledStudents();
            assertThrows(UnsupportedOperationException.class, () -> snapshot.add(BOB));
            course.drop(ALICE);
            assertEquals(List.of(ALICE), snapshot);
        }
    }

    @Test
    @DisplayName("concurrent enrolls never exceed capacity and lose nobody")
    void concurrentEnrollsRespectCapacity() throws Exception {
        int capacity = 10;
        int students = 200;
        Course course = courseWithCapacity(capacity);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            List<Future<EnrollmentResult>> futures = IntStream.range(0, students)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        return course.enroll(StudentId.of("s" + i));
                    }))
                    .toList();
            start.countDown();
            long enrolled = 0;
            for (Future<EnrollmentResult> f : futures) {
                if (f.get() == EnrollmentResult.ENROLLED) {
                    enrolled++;
                }
            }
            assertEquals(capacity, enrolled);
        }

        assertEquals(capacity, course.enrolledCount());
        assertEquals(students - capacity, course.waitlistedStudents().size());
    }
}
