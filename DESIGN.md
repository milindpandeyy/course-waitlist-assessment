# Course Waitlist — Design Specification

**Status:** Approved by the candidate; implemented.  
**Source:** `MILIND PANDEY_AI_Assessment.pdf` — *Focused AI-Assisted Engineering Assessment: Course Waitlist*  
**Scope:** One course offering, plain Java service, in-memory state.

## 1. Assessment requirements (source-derived)

The assessment requires a bounded feature supporting enrollment, drop, and FIFO waitlist promotion for one offering. Eligible learners enroll when a seat exists or join the waitlist when full, without duplicate entries. A drop opens a seat and promotes the earliest eligible waiting learner. Ineligible learners must not be enrolled or promoted. Invalid requests and repeated drops must return clear results without corrupting state. Focused automated tests must cover normal, full-capacity, duplicate, prerequisite, FIFO, and invalid/repeated-drop cases. Deliverables include runnable code, tests, a proportionate design specification, setup/run instructions, limitations, and an honest AI prompt/use record with independent verification. No starter was supplied; a small in-memory Java service is permitted.

The assessment also asks for an explicit policy for an ineligible learner reaching the front of the waitlist. The policy (§8) is prevention: an ineligible learner is never added to the waitlist, so this state cannot occur.

## 2. Prerequisites and scope

- **JDK 25** and **Gradle 9.x** (candidate decisions, not assessment requirements).
- A **plain Java service** managing **exactly one course offering per instance**.
- State is **in-memory only** and lost on restart.
- **Single-threaded use only.** The service uses no locks and is not thread-safe.
- Tests use **JUnit Jupiter (JUnit 5)**.
- Records and enums live in `com.assessment.enrollment.model`; service logic in `com.assessment.enrollment.service`; the interactive console front end in `com.assessment.enrollment.cli` (§13).

## 3. Domain model

### `Course` (record in `model`)

- `String courseId`
- `int capacity` — maximum enrolled learners
- `Set<String> prerequisiteCourseIds` — IDs of courses required before this offering

### `LearnerDetails` (record in `model`)

- `String learnerId`
- `Set<String> completedCourseIds`

Both records store an unmodifiable copy of their set. `LearnerDetails` accepts a null learner ID or a null completed-course set so that the service can report them as `MISSING_LEARNER_DETAILS`. There is no course catalog or completed-course management.

### In-memory registration state (owned by `EnrollmentService`)

- `Set<String> enrolledLearners = new HashSet<>()`
- `Queue<String> waitlistedLearners = new ArrayDeque<>()`

There is no separate status map, waitlist membership map, or repository abstraction. Waitlist membership is checked with `ArrayDeque.contains` (linear time), which is acceptable for this bounded exercise.

## 4. Service boundary and public API

Two concrete classes in the `service` package, one per concern (no interface/implementation split):

- `CourseConfigurationService` configures the single course offering, once.
- `EnrollmentService` handles enrollment, drop, status lookup and available seats for that course.

```java
// CourseConfigurationService
Course configure(String courseId, int capacity, Set<String> prerequisiteCourseIds);
Set<String> getAvailableCourseIds();
boolean isConfigured();
Course getCourse();

// EnrollmentService
EnrollmentService(CourseConfigurationService configuration);   // null is rejected
EnrollmentResult enroll(String courseId, LearnerDetails learnerDetails);
DropResult drop(String learnerId);
int getAvailableSeats();
EnrollmentResult getLearnerStatus(String learnerId);
```

### Course configuration

- `configure` creates the course with its prerequisites. It stores the course ID and every prerequisite ID trimmed and in lower case, whatever case they were entered in, and returns the stored `Course`.
- A course can be configured **once**. A second call throws `IllegalStateException` and leaves the configured course unchanged. This check runs before input validation.
- Invalid input configures nothing: `IllegalArgumentException` for a null/blank course ID, a capacity below 1, a null prerequisite set, or a null/blank prerequisite ID. A later call with valid input can still succeed.
- `getAvailableCourseIds()` lists the IDs (lower case) of the courses learners can enroll in: the configured course, or an empty set before configuration. A full course is still listed, because learners can join its waitlist. The CLI shows this list, so a list of several courses would need no CLI change.
- `isConfigured()` reports whether a course exists. `getCourse()` returns it, or throws `IllegalStateException` if none is configured yet.

### Enrollment

- `EnrollmentService` holds no copy of the course: each public method reads it from the configuration service through `getCourse()`. So it always works on the one validated, normalized course.
- Before a course is configured, every public method throws `IllegalStateException` and changes nothing.
- `getAvailableSeats()` returns `course.capacity() - enrolledLearners.size()`. It takes no course ID because there is one offering.

### Shared ID handling

ID normalization and the null/blank check are needed by both services. They live once, in a package-private helper (`Ids`) in the `service` package, not in either service.

### `EnrollmentResult` enum

```java
ENROLLED,
WAITLISTED,
INELIGIBLE,
MISSING_LEARNER_DETAILS,
INVALID_COURSE_ID,
ALREADY_ENROLLED,
ALREADY_WAITLISTED,
NOT_ENROLLED
```

`NOT_ENROLLED` is returned only by status lookup, never by `enroll`.

### `DropResult` enum

```java
DROPPED,
LEARNER_DETAIL_NOT_FOUND
```

The enums are the return types themselves; there are no `EnrollmentStatus` / `DropStatus` wrappers.

## 5. Operation rules

### Input handling (all methods)

Every identifier passed to any method is **case-insensitive**, and **surrounding whitespace is ignored**. This applies to:

- the `courseId` argument of `enroll`, compared with `Course.courseId`;
- learner IDs in `enroll`, `drop` and `getLearnerStatus`;
- prerequisite and completed course IDs when checking eligibility — a prerequisite `"JAVA"` is met by a completed course `"java"` or `" Java "`.

The services compare IDs in a normalized form (whitespace stripped from both ends, lower-cased with `Locale.ROOT`). `CourseConfigurationService` stores the course ID and prerequisite IDs in that form, and `EnrollmentService` stores learner IDs in that form, so `" Alice "` and `"alice"` are the same learner. Inner whitespace is significant (`"mary ann"` ≠ `"maryann"`). Normalization happens in the services; the records themselves do not change their values.

### Enrollment

Checks run in this order; the first one that applies decides the result.

1. `courseId` does not match the offering → `INVALID_COURSE_ID`.
2. `learnerDetails` is null, the learner ID is null/blank, or the completed-course set is null → `MISSING_LEARNER_DETAILS`. An empty completed-course set is valid.
3. Learner already enrolled → `ALREADY_ENROLLED`.
4. Learner already waitlisted → `ALREADY_WAITLISTED` (queue position unchanged).
5. Any prerequisite missing from the completed courses → `INELIGIBLE`; the learner is stored nowhere.
6. A seat is free → learner added to the enrolled set, `ENROLLED`.
7. Otherwise → learner appended to the FIFO waitlist, `WAITLISTED`.

Results 1–5 leave the state unchanged.

### Drop

- Enrolled learner: removed from the enrolled set, then the oldest waitlisted learner (if any) is promoted; `DROPPED`.
- Waitlisted learner: removed from the queue, no promotion; `DROPPED`.
- Null/blank ID, or a learner who is neither enrolled nor waitlisted: `LEARNER_DETAIL_NOT_FOUND`, no change.
- A repeated drop therefore never frees another seat or triggers another promotion.

### Promotion

- FIFO: when a seat is freed, the oldest waiting learner is enrolled. Each drop promotes at most one learner.
- Prerequisites are not re-checked at promotion: every queued learner was eligible when queued and completion is permanent (§8).

### Status lookup

- Null/blank learner ID → `MISSING_LEARNER_DETAILS`.
- In the enrolled set → `ENROLLED`.
- In the waitlist → `WAITLISTED`.
- Otherwise → `NOT_ENROLLED`.
- Read-only; status is derived from the enrolled set and the waitlist, with no separate index.

## 6. Invariants and idempotency

1. `0 <= enrolledLearners.size() <= course.capacity()`; capacity is at least 1.
2. No learner ID occurs more than once in the waitlist.
3. No learner ID is both enrolled and waitlisted.
4. Only learners meeting **all** prerequisites are enrolled or waitlisted.
5. Queue order is insertion order; promotion is FIFO.
6. Duplicate enroll requests change nothing; they return `ALREADY_ENROLLED` or `ALREADY_WAITLISTED`.
7. Repeated drops change nothing; they return `LEARNER_DETAIL_NOT_FOUND` after the first `DROPPED`.
8. A learner who drops may enroll again. This is a **new attempt**: prerequisites are checked again and, if the course is full, the learner joins the **end** of the waitlist.
9. Status lookups do not change state.

This is **state-level idempotency**, not request-key replay or identical-response idempotency; there is no idempotency-key store.

## 7. Design assumptions and decisions

- **A1:** Completed courses are permanent: once completed, a course cannot become incomplete.
- **A2:** Prerequisites are sets of course IDs, and all of them must be completed.
- **A3:** Only eligible learners can join the waitlist; the public API cannot produce an ineligible waitlist entry.
- **A4:** Single-threaded operation; concurrency is not handled.
- **A5:** One offering, configured once per `CourseConfigurationService`; an `EnrollmentService` serves the course of the configuration service it was given.
- **A6:** Learner ID and completed-course set are required; an empty completed-course set is valid.
- **A7:** In-memory state is ephemeral.
- **A8:** Duplicate enroll/drop requests create no additional state changes, although their returned values may differ.
- **A9:** Re-enrollment after a drop is a new attempt; waitlist position is not preserved.
- **A10:** A waitlisted learner may drop to cancel their waiting entry.
- **A11:** Registration status is derived from the enrolled set and the FIFO queue, not kept in a separate map.
- **A12:** There is no `INVALID_LEARNER_DETAILS`; missing/blank IDs and a missing completed-course set map to `MISSING_LEARNER_DETAILS`.
- **A13:** All IDs (course, learner, prerequisite and completed-course) are case-insensitive and ignore surrounding whitespace (§5 *Input handling*).
- **A14:** A learner who is neither enrolled nor waitlisted has status `NOT_ENROLLED`.
- **A15:** A course needs a non-blank ID, a capacity of at least 1 and non-blank prerequisite IDs; otherwise no course is configured (§4 *Course configuration*). The `Course` record itself does not validate; `CourseConfigurationService` does.

## 8. Policy: ineligible learner at the front of the waitlist

No ineligible learner is ever added to the waitlist.

- `enroll` checks all prerequisites (§5 rule 5) *before* a learner can take a seat or join the queue; an ineligible learner gets `INELIGIBLE` and is stored nowhere.
- Course completion is permanent (A1), and the API offers no way to change a queued learner's completed courses.
- Therefore every queued learner is eligible, the front of the queue is always eligible, and promotion enrolls the head of the queue without re-checking prerequisites.

This answers the assessment's "ineligible learner at the front of the waitlist" case by prevention rather than by a skip/remove/retain rule. Re-validation at promotion would only be needed if a future change let eligibility be lost after queueing (for example, completion revoked or prerequisites changed).

## 9. Testing

Tests are in `EnrollmentServiceTest` and `CourseConfigurationServiceTest` (JUnit Jupiter), grouped by area, and test observable service behaviour and invariants. Records and enums have no behaviour of their own and are not tested directly.

Covered scenarios:

1. Eligible learner enrolls when a seat is available.
2. Eligible learner is waitlisted when the course is full.
3. Missing prerequisite returns `INELIGIBLE` and takes no seat or queue position.
4. A course without prerequisites accepts an empty completed-course set.
5. Duplicate request from an enrolled learner returns `ALREADY_ENROLLED` without change.
6. Duplicate request from a waitlisted learner returns `ALREADY_WAITLISTED` without moving the queue position.
7. Dropping an enrolled learner promotes the oldest waitlisted learner.
8. Dropping a waitlisted learner removes them without affecting capacity.
9. Invalid and repeated drops return `LEARNER_DETAIL_NOT_FOUND` without extra promotion.
10. Re-enrollment after a drop joins the end of the queue when the course is full.
11. Null/blank learner ID, null learner details, and a null completed-course set return `MISSING_LEARNER_DETAILS`.
12. Unknown course ID returns `INVALID_COURSE_ID` without change.
13. `getAvailableSeats()` reflects enrollments, drops and promotions.
14. `getLearnerStatus()` reports enrolled, waitlisted, `NOT_ENROLLED` and missing ID, including after promotion and drop.
15. Capacity, uniqueness, and enrolled/waitlisted disjointness hold after mixed operations.
16. IDs differing only in case or surrounding whitespace are equal in every method, including prerequisite matching.
17. `configure` stores course and prerequisite IDs in lower case and rejects a null/blank course ID, a capacity below 1, and null/blank prerequisites, configuring nothing; a valid retry then succeeds.
18. A second `configure` is rejected and leaves the course unchanged; `getCourse()` before configuration is rejected.
19. Every `EnrollmentService` method is rejected before a course is configured; a null configuration service is rejected.
20. `getAvailableCourseIds()` is empty before configuration and after a rejected configuration, and lists the configured course in lower case.
21. A course without prerequisites enrolls learners whatever courses they have completed (none, or several), and waitlists them once full.

Concurrency is out of scope and is not tested.

## 10. Project layout

```text
course-waitlist/
├── src/main/java/com/assessment/enrollment/
│   ├── model/
│   │   ├── Course.java
│   │   ├── LearnerDetails.java
│   │   ├── EnrollmentResult.java
│   │   └── DropResult.java
│   ├── service/
│   │   ├── CourseConfigurationService.java
│   │   ├── EnrollmentService.java
│   │   └── Ids.java
│   └── cli/
│       └── EnrollmentCli.java
├── src/test/java/com/assessment/enrollment/service/
│   ├── CourseConfigurationServiceTest.java
│   └── EnrollmentServiceTest.java
├── gradle/wrapper/
├── gradlew
├── gradlew.bat
├── build.gradle
├── settings.gradle
├── DESIGN.md
├── CLAUDE-AI-LOG.md
└── README.md
```

## 11. AI use and verification

AI assistance is recorded in `CLAUDE-AI-LOG.md`, kept separate from this document and from `README.md`. Each task entry records the prompt, what was produced, deviations, the candidate's review and corrections, and Claude's own build and test results. The candidate owns design decisions and final acceptance, reviews every task, and performs the independent build verification once, after all implementation; that verification is the final entry in the log.

## 12. Implementation phases

1. Project skeleton: Java 25, Gradle 9.x wrapper, JUnit Jupiter, package folders.
2. Domain records and enums.
3. Enrollment behaviour and tests.
4. Drop, promotion and tests.
5. Status lookup, available seats and tests.
6. Final verification, assessment-gap review, documentation and log.
7. Interactive CLI (§13), launched with `./gradlew run`.
8. Refactoring: package `com.assessment.enrollment`; course configuration split from enrollment (§4); CLI free of business rules (§13).
9. Available-courses query (§4) and CLI option.
10. Concurrency and persistence (§14) and limitations and follow-up risks (§15) documented.

## 13. Interactive CLI

`com.assessment.enrollment.cli.EnrollmentCli` is a console front end to the two services. It reads input with a `java.util.Scanner`, shows a numbered menu and dispatches the choice with a `switch`. Gradle's `application` plugin sets it as the main class, so `./gradlew run` (`.\gradlew.bat run` on Windows) starts it; the `run` task forwards standard input.

### Responsibilities

The CLI contains **no business rules**; it only reads input, converts it, calls the services and prints what they return.

- `main` wires the objects: one `CourseConfigurationService`, one `EnrollmentService` built on it, and the CLI, which receives both services together with its `Scanner` and `PrintStream`.
- The services enforce every rule: configure once, course validation, ID normalization, eligibility, capacity, waitlisting, promotion and the "no operation before configuration" guard.
- The CLI only asks `isConfigured()` to choose what to show next. A rule refused by a service reaches the user as the exception's message.

| Option | Prompts | Service call | Output |
| --- | --- | --- | --- |
| 1. Create course | course ID, capacity, prerequisites (comma-separated, blank for none) | `configure` | `Course created.`, or `Course not created: <reason>` (the exception's message) |
| 2. Enroll learner | course ID, learner ID, completed courses (comma-separated, blank for none) | `enroll` | `Result: <EnrollmentResult>` |
| 3. Drop learner | learner ID | `drop` | `Result: <DropResult>` |
| 4. Check status | learner ID | `getLearnerStatus` | `Status: <EnrollmentResult>` |
| 5. Available seats | — | `getAvailableSeats` | `Available seats: <n>` |
| 6. Available courses | — | `getAvailableCourseIds` | `Available courses: <ids>`, or `No courses available yet.` |
| 7. Exit | — | — | `Exit.` |

### Flow and input conversion

- Options 2–5 while `isConfigured()` is false print `No course yet. Create a course first.` and go straight into the *Create course* prompts. The chosen option is not carried out; afterwards the menu is shown again.
- Option 6 needs no course: before configuration it prints `No courses available yet.` It only formats the IDs the service returns, so it holds no single-course assumption.
- *Create course* while `isConfigured()` is true prints `A course has already been created. Only one course is allowed.` and asks for nothing. The rule itself is `configure`'s (§4): if a second call reached the service, it would refuse it.
- Capacity text that is not a whole number prints `Capacity must be a whole number.` (a conversion error); range checks belong to `configure`.
- Comma-separated lists are split on `,` and empty entries are skipped; IDs are passed as typed and normalized by the services.
- An unknown menu choice prints `Invalid option. Enter a number from 1 to 7.`
- End of input exits like option 7, without the message.
- Input is read as plain lines with no platform- or shell-specific handling.

The CLI has no unit tests of its own; it holds no rules, and the behaviour it exposes is covered by the service tests. It was checked by running scripted sessions through `./gradlew run`.

## 14. Concurrency and persistence

### Concurrency: not supported

The application is **not thread-safe** and is designed for single-threaded use only (A4). No locks, synchronization or atomic operations are used.

- `EnrollmentService` keeps its state in a plain `HashSet` and `ArrayDeque`. Each operation is a check followed by an update: `enroll` checks for duplicates, eligibility and a free seat, then adds the learner, and `drop` removes a learner and then promotes the head of the queue.
- If two threads ran these at the same time, the enrolled count could exceed the capacity. A learner could also be enrolled or queued twice, or a freed seat could be promoted twice or not at all.
- `CourseConfigurationService.configure` checks "not configured yet" and then stores the course. Two concurrent calls could both pass the check.
- The CLI is a single-threaded loop, so these races cannot occur through it. There are no concurrency tests (§9).

### Persistence: none

All state lives in memory and is lost when the process ends (A7). This covers the configured course, the enrolled set and the waitlist. There is no database, file storage or recovery.

## 15. Limitations and follow-up risks

### Missing semantic validation

Validation covers presence and basic range only: null/blank IDs, capacity of at least 1, and null/blank prerequisites (§4). Semantic validation is not done:

- **No length limit on IDs.** A course ID, prerequisite ID or learner ID can be any length.
- **No limit on the prerequisite list.** A course can have any number of prerequisites, and a learner can list any number of completed courses.
- **No upper bound on capacity.** Any value up to `Integer.MAX_VALUE` is accepted.
- **No format rules for IDs.** Any characters are accepted, for example `CS 201` or `#!`. Only case and surrounding whitespace are normalized. The CLI cannot enter an ID containing a comma, because it splits lists on commas.
- **No consistency checks on prerequisites.** A course can list itself as a prerequisite, which makes every learner ineligible unless they report having completed it. Prerequisite IDs are not checked against any list of real courses, because there is no catalog.
- **Completed courses are self-declared.** The service trusts the learner's completed-course set as given.

### Other limitations

- **Single-threaded and memory only** (§14).
- **One course per configuration service.** It cannot be edited, removed or reconfigured, and a mistake means restarting the application.
- **Case-insensitive matching has limits.** IDs are lower-cased with `Locale.ROOT`, so variants that need full Unicode case folding (for example `ß` and `SS`) are not treated as equal.
- **Waitlist membership checks are linear** in the queue length (`ArrayDeque.contains`).
- **Results are printed as enum names.** The CLI shows the raw result values, such as `INELIGIBLE`.
- **No CLI unit tests.** The CLI has no rules; it was checked with scripted sessions.

### Follow-up risks

- **Concurrent use** would need each `enroll`, `drop` and `configure` to run atomically, for example under one lock per course or in a transaction with a persistent store. Concurrency tests would be needed too.
- **Revocable eligibility.** If completed courses or prerequisites could change after a learner is queued, the prevention policy (§8) would no longer hold. Promotion would then need to re-check eligibility and to skip or remove the learner.
- **Semantic validation** (length limits, list size limits, self-prerequisites) would belong in `CourseConfigurationService` and `EnrollmentService`, not in the CLI.
- **Several courses** would change the configuration service and its `getAvailableCourseIds()`. The CLI only displays that list, so it would need no change for listing.
