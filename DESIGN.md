# Design

## Status

No written specification was provided. Everything below marked **[ASSUMPTION]** is a
decision made to get a working PoC, and should be confirmed or replaced once a spec exists.

## Domain model

| Type | Role |
| --- | --- |
| `Course` | Aggregate holding `capacity`, the ordered set of enrolled students and the ordered waitlist. All behaviour lives here. |
| `StudentId` | Value object wrapping a non-blank, trimmed string. Equality by value. |
| `EnrollmentResult` | `ENROLLED`, `WAITLISTED`, `ALREADY_ENROLLED`, `ALREADY_WAITLISTED`. |
| `DropResult` | `outcome` (`LEFT_COURSE`, `LEFT_WAITLIST`, `NOT_REGISTERED`) plus `promoted`, the student moved off the waitlist, if any. |
| `EnrollmentStatus` | `ENROLLED`, `WAITLISTED`, `NOT_REGISTERED`, for queries. |

## Rules

1. A course has a fixed capacity of at least 1. **[ASSUMPTION]** capacity cannot change after creation.
2. `enroll` gives the student a seat if one is free, otherwise appends them to the waitlist.
3. The waitlist is strictly FIFO. **[ASSUMPTION]** no priorities (seniority, major, etc.).
4. **[ASSUMPTION]** The waitlist is unbounded.
5. Enrolling a student who is already enrolled or already waitlisted is a no-op that reports
   which case applied; a waitlisted student keeps their position. **[ASSUMPTION]** idempotent
   results rather than exceptions, because duplicate submissions are normal in registration UIs.
6. `drop` of an enrolled student frees the seat and **immediately** promotes the head of the
   waitlist. **[ASSUMPTION]** promotion is automatic; there is no "offer and accept within N hours" step.
7. `drop` of a waitlisted student removes them from the waitlist; everyone behind moves up one.
8. `drop` of an unknown student is a no-op reporting `NOT_REGISTERED`.
9. A student who drops and enrolls again is treated as new: they go to the back of the queue
   if the course is full. **[ASSUMPTION]** no reinstatement of previous position.
10. **[ASSUMPTION]** A student is identified only by `StudentId`; there are no eligibility
    checks (prerequisites, timetable clashes, credit limits).

## Invariants

Checked with Java `assert` after each mutation (Gradle runs tests with `-ea`):

- enrolled count never exceeds capacity;
- the waitlist is non-empty only while the course is full;
- no student is both enrolled and waitlisted.

## Implementation notes

- Enrolled students and the waitlist are both `LinkedHashSet`s (as `SequencedSet`): O(1)
  membership, insertion order preserved, O(1) removal of an arbitrary student.
  `waitlistPosition` is O(n), which is fine at course scale.
- Every public method on `Course` is `synchronized`, so one `Course` instance can be shared
  across threads. A concurrency test enrolls 200 students from 16 threads into 10 seats.
- Query methods return immutable snapshots (`List.copyOf`), never live views.
- Compiled with `--release 21`; uses only `java.base`.

## Out of scope for the PoC

- Persistence, a REST/CLI interface, or any multi-course registry/service layer.
- Notifications to promoted students.
- Capacity changes, waitlist caps, time-boxed offers, priority rules.
- Audit history of enrolment events.

## Open questions for the requester

1. Is there a written spec or acceptance criteria, and what is the expected interface (library, REST API, CLI)?
2. Should promotion be automatic, or should the next student receive an offer they must accept?
3. Can capacity change after creation, and if it increases, should waitlisted students be promoted?
4. Is there a waitlist size limit, or any priority ordering?
5. Does state need to persist, and across how many courses?
