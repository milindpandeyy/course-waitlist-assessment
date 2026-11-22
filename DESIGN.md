# Course Waitlist — Design Specification

**Status:** Candidate-approved design baseline, prior to implementation  
**Source:** `MILIND PANDEY_AI_Assessment.pdf` — *Focused AI-Assisted Engineering Assessment: Course Waitlist*  
**Scope:** One course offering, plain Java service, in-memory state.

> **Instruction to AI coding assistants:** Treat this document as the approved design baseline. Do not silently add features, change decisions, or resolve ambiguities. Report conflicts with the assessment and ask for approval before deviating. Preserve a separate, factual `CLAUDE-AI-LOG.md` of actual AI work and verification evidence.

## 1. Assessment requirements (source-derived)

The assessment requires a bounded feature supporting enrollment, drop, and FIFO waitlist promotion for one offering. Eligible learners enroll when a seat exists or join the waitlist when full, without duplicate entries. A drop opens a seat and promotes the earliest eligible waiting learner. Ineligible learners must not be enrolled or promoted. Invalid requests and repeated drops must return clear results without corrupting state. Focused automated tests must cover normal, full-capacity, duplicate, prerequisite, FIFO, and invalid/repeated-drop cases. Deliverables include runnable code, tests, a proportionate design specification, setup/run instructions, limitations, and an honest AI prompt/use record with independent verification. No starter was supplied; a small in-memory Java service is permitted.

The assessment also asks for an explicit policy for an ineligible learner reaching the front of the waitlist. Our currently approved assumption makes that state unreachable; see §8 for the unresolved compliance risk. This document does **not** claim that this acceptance criterion has been fully satisfied.

## 2. Mandatory project prerequisites and scope decisions

- **JDK 25** and **Gradle 9.x** are mandatory project prerequisites (candidate decisions, not assessment requirements).
- Use a **plain Java service**.
- Manage **exactly one course offering per service instance**.
- State is **in-memory only** and lost on restart.
- Single-threaded usage only. **Concurrent access is not supported**; do not introduce locks or claim thread safety.
- Use **JUnit Jupiter (JUnit 5)** for focused automated tests; 
- Put Java records, POJOs, and enums under `com.example.waitlist.model`; service logic under `com.example.waitlist.service`.
- Keep `CLAUDE-AI-LOG.md` separate from this design and from `README.md`.

## 3. Domain model

### `Course` (Java record in `model`)

- `String courseId`
- `int capacity` — maximum enrolled learners
- `Set<String> prerequisiteCourseIds` — IDs of courses required before this offering

### `LearnerDetails` (Java record in `model`)

- `String learnerId`
- `Set<String> completedCourseIds`

Use immutable/defensively copied sets to protect domain invariants. No course catalog or completed-course management service is required.

### In-memory registration state (owned by service)

- `Set<String> enrolledLearners = new HashSet<>()`
- `Queue<String> waitlistedLearners = new ArrayDeque<>()`

Do **not** add a separate status map, waitlist membership map, or repository abstraction. Waitlist membership lookup may use `ArrayDeque.contains` (linear time), acceptable for the bounded exercise.

## 4. Service boundary and public API

One concrete class `CourseRegistrationService` in the `service` package (no interface/implementation split required).

```java
EnrollmentResult enroll(String courseId, LearnerDetails learnerDetails);
DropResult drop(String learnerId);
int getAvailableSeats();
EnrollmentResult getLearnerStatus(String learnerId);
```

`getAvailableSeats()` returns `course.capacity() - enrolledLearners.size()` and requires no course ID because each service instance owns one offering.

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

`NOT_ENROLLED` is used for status lookup, not a successful enrollment result.

### `DropResult` enum

```java
DROPPED,
LEARNER_DETAIL_NOT_FOUND
```

These are the return types themselves; do **not** introduce separate `EnrollmentStatus` / `DropStatus` wrappers.

## 5. Operation rules

### Input handling (all methods)

Candidate decision, 2026-11-22: every identifier passed to any method is **case-insensitive** and its **surrounding whitespace is ignored**, and each method must handle its input accordingly. This applies to:

- the `courseId` argument of `enroll`, compared with `Course.courseId`;
- learner IDs in `enroll`, `drop` and `getLearnerStatus`;
- course IDs in `Course.prerequisiteCourseIds` and `LearnerDetails.completedCourseIds` when checking eligibility — e.g. a prerequisite `"JAVA"` is satisfied by a completed course `"java"` (or `" Java "`).

The service compares IDs in a normalized form (whitespace stripped from both ends, lower-cased with `Locale.ROOT`) and stores learner IDs in that form, so `" Alice "` and `"alice"` are the same learner. Inner whitespace is significant (`"mary ann"` ≠ `"maryann"`). The domain records keep the values they were given; normalization happens in the service.

### Enrollment

1. Validate that `courseId` matches the sole offering (compared as described in *Input handling*); otherwise return `INVALID_COURSE_ID` without changing state.
2. If `learnerDetails` is null, learner ID is null/blank, or completed-course set is null, return `MISSING_LEARNER_DETAILS` without changing state. An empty completed-course set is valid.
3. If already enrolled, return `ALREADY_ENROLLED` without mutation.
4. If already waitlisted, return `ALREADY_WAITLISTED` without mutation or moving their position.
5. Require every prerequisite course ID to appear among the learner's completed course IDs, both compared in normalized form (*Input handling*); otherwise return `INELIGIBLE` without adding the learner anywhere.
6. If seats are available, add learner ID to enrolled set and return `ENROLLED`.
7. Otherwise append learner ID to FIFO waitlist and return `WAITLISTED`.

Validation order above is a proposed deterministic implementation rule; flag any problem before changing it.
### Drop

- If learner is enrolled: remove enrollment, then promote the earliest waitlisted learner (if any), return `DROPPED`.
- If learner is waitlisted: remove from queue, do **not** promote, return `DROPPED`.
- If learner is neither enrolled nor waitlisted (including null/blank ID): return `LEARNER_DETAIL_NOT_FOUND` with no mutation.
- Repeated drops must never free another seat or trigger another promotion.

### Promotion

- FIFO: take the oldest waiting learner and enroll them when a seat is freed.
- Current approved assumption: all waitlisted learners were prerequisite-eligible when queued, and course completion is permanent; therefore the supported flow cannot produce an ineligible waitlist entry. **Do not silently implement a skip/remove/retain policy** without approval. See §8.

### Status lookup

- Null/blank learner ID: `MISSING_LEARNER_DETAILS`.
- In enrolled set: `ENROLLED`.
- In waitlist queue: `WAITLISTED`.
- Otherwise: `NOT_ENROLLED` (candidate decision, 2026-11-22: matches the §4 enum).
- This method is read-only and does not maintain a separate status index.

## 6. Invariants and idempotency

1. `0 <= enrolledLearners.size() <= course.capacity()`.
2. No learner ID occurs more than once in the waitlist.
3. No learner ID is both enrolled and waitlisted.
4. Only learners meeting **all** prerequisites enter enrollment or waitlist through the supported API.
5. Queue order is insertion order for currently waiting learners; promotion is FIFO.
6. Duplicate enroll requests produce no state mutation; they may return `ALREADY_ENROLLED` or `ALREADY_WAITLISTED` instead of the initial result.
7. Repeated drops produce no further state mutation and may return `LEARNER_DETAIL_NOT_FOUND` after an initial `DROPPED`.
8. A learner who drops may later enroll again. This is a **new attempt**: prerequisites are checked again and, if full, they join the **end** of the waitlist; their former position is not restored.
9. Status lookups do not mutate state.

This is **state-level idempotency**, not request-key replay or identical-response idempotency. No idempotency-key store is in scope.

## 7. Design assumptions (not explicitly required by the assessment)

- **A1:** Completed courses are permanent: once completed, a course cannot become incomplete.
- **A2:** Prerequisites are represented by sets of course IDs, and all prerequisites must be completed.
- **A3:** Only eligible learners can join the waitlist; the public API cannot produce an ineligible waitlist entry.
- **A4:** Single-threaded operation; concurrency is not handled.
- **A5:** One offering per service instance.
- **A6:** Learner ID and completed-course set are required; an empty completed-course set is valid.
- **A7:** In-memory state is ephemeral.
- **A8:** Duplicate enroll/drop requests must not create additional state changes, although their returned enum values may differ.
- **A9:** Re-enrollment after drop is a new attempt; waitlist position is not preserved.
- **A10:** A waitlisted learner may drop to cancel their waiting entry.
- **A11:** Registration status is derived from the enrolled set and FIFO queue, not maintained in a separate map.
- **A12:** `INVALID_LEARNER_DETAILS` is intentionally omitted; missing/blank IDs and missing completed-course sets map to `MISSING_LEARNER_DETAILS`.
- **A13:** All IDs given to any method (course, learner, prerequisite and completed-course IDs) are case-insensitive and ignore surrounding whitespace (§5 *Input handling*; candidate decision).

## 8. Explicit limitation / unresolved assessment conflict

The assessment requests a documented policy for an **ineligible learner at the front of the waitlist** and says ineligible learners must not be promoted. Under A1–A3, such an entry cannot be created through supported operations, so the approved design currently **does not revalidate prerequisites at promotion or define skip-versus-retain behavior for externally corrupted state**. This is a **potential acceptance-criteria gap**, not something Claude should silently fix. Surface it for candidate review before final submission. An eligibility flag or revalidation policy would require a separate approved design change.

## 9. Testing plan

Use JUnit Jupiter. Focus on observable behavior and invariants:

1. Eligible learner enrolls with available seat.
2. Eligible learner waitlists when full.
3. Missing prerequisite returns `INELIGIBLE` and does not occupy seat or queue position.
4. No prerequisites / empty completed-course set can be valid.
5. Duplicate enrolled request returns `ALREADY_ENROLLED` without mutation.
6. Duplicate waiting request returns `ALREADY_WAITLISTED` without moving queue position.
7. Dropping enrolled learner promotes oldest waitlisted learner.
8. Dropping waitlisted learner removes them without affecting capacity.
9. Invalid/repeated drops return `LEARNER_DETAIL_NOT_FOUND` without extra promotion.
10. Re-enrollment after drop joins end of queue if full.
11. Null/blank learner ID, null learner details, and null completed-course set return `MISSING_LEARNER_DETAILS`.
12. Unknown course ID returns `INVALID_COURSE_ID` without mutation.
13. `getAvailableSeats()` reflects enrollments, drops, and promotions.
14. `getLearnerStatus()` reports enrolled, waitlisted, not enrolled (`NOT_ENROLLED`), and missing ID; promotion/drop transitions are reflected.
15. Check capacity, uniqueness, and enrolled/waitlisted disjointness after relevant operations.
16. IDs differing only in case or surrounding whitespace are treated as equal in every method, including prerequisite matching (`"JAVA"` prerequisite satisfied by `"java"` completed).

No concurrency test is promised because concurrency is explicitly unsupported; disclose that limitation.

## 10. Expected project layout

```text
course-waitlist/
├── src/main/java/com/example/waitlist/
│   ├── model/
│   │   ├── Course.java
│   │   ├── LearnerDetails.java
│   │   ├── EnrollmentResult.java
│   │   └── DropResult.java
│   └── service/
│       └── CourseRegistrationService.java
├── src/test/java/com/example/waitlist/service/
│   └── CourseRegistrationServiceTest.java
├── gradle/wrapper/
├── gradlew
├── gradlew.bat
├── build.gradle
├── settings.gradle
├── DESIGN.md
├── CLAUDE-AI-LOG.md
├── README.md
└── LIMITATIONS.md
```

## 11. AI use and verification

`CLAUDE-AI-LOG.md` is a **separate, chronological factual record**, containing actual prompts, relevant Claude response excerpts/summaries, files affected, candidate accept/edit/reject decisions, and independently verified test/build results. Never invent conversations, decisions, commands, or successful tests. If an entry awaits candidate verification, mark it **Pending verification**. The candidate owns architecture decisions and final acceptance.

## 12. Implementation sequencing

1. Project skeleton only: Java 25, Gradle 9.x wrapper, JUnit Jupiter, package folders, README prerequisites, empty log template. **No business logic.**
2. Domain records and enums after candidate review.
3. Enrollment behavior and focused tests.
4. Drop, promotion, and focused tests.
5. Status lookup and remaining tests.
6. Independent verification, explicit assessment-gap review, documentation, and final log.

Do not combine these phases without candidate authorization.
