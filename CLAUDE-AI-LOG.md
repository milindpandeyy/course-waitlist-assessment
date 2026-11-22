# Claude AI Log

Factual record of AI assistance (DESIGN.md §11). Tool for all tasks: Claude Code (desktop app),
Claude Opus 5.5. The candidate owns design decisions and final acceptance, reviews every task,
and runs the independent build verification once all implementation is done (last section).

## Task 1 — Project setup and domain model (2026-11-22)

**Prompt:** Read `DESIGN.md`; create a Java Gradle project, the Domain Model types, the Gradle
wrapper and test dependencies; run the build and tests; log the work here.

**Produced:** Gradle 9.9.1 wrapper, Groovy build, Java 25 (`--release 25`, `-Xlint:all -Werror`),
JUnit Jupiter 5.13.4; records `Course`, `LearnerDetails` (unmodifiable set copies; null learner
ID / course set allowed for `MISSING_LEARNER_DETAILS`); enums `EnrollmentResult`, `DropResult`.

**Notes:** `DESIGN.md` was missing at first; Claude asked for it instead of inventing a design.
Claude flagged a §4/§5 conflict (`NOT_ENROLLED` vs `NOT_REGISTERED`). Phases 1–2 combined at the
candidate's request.

**Claude verification:** `BUILD SUCCESSFUL`, no warnings; 5/5 model tests, later removed.

**Candidate review**
- Status-lookup conflict resolved: use `NOT_ENROLLED` (accepted the `DESIGN.md` §5/§9 update).
- Approved adding the §4 enums `EnrollmentResult` and `DropResult`.
- Rejected the model tests: record/POJO tests add no value for this feature; production model
  classes kept unchanged.
- Asked for this log to be kept concise.
- Independent re-run of the build by the candidate: **Verified.**

## Task 2 — Enrollment implementation (2026-11-22)

**Prompt:** Read `DESIGN.md`; implement enrollment in `CourseRegistrationService` (eligibility,
capacity, waitlisting, duplicates, invalid input); add focused JUnit tests for enrollment
scenarios and state invariants; run them; update this log.

**Produced:** `CourseRegistrationService(Course)` with the §3 state and `enroll` in the §5 rule
order; enrollment tests for §9 items 1–6, 11, 12, capacity and check order.

**Notes:** Only `enroll` existed, so tests read state through later `enroll` results. Capacity
below 1 is not validated.

**Candidate review and corrections**

| # | Candidate comment | Correction by Claude |
| --- | --- | --- |
| 1 | `CourseRegistrationService` line 36 does an `equals` check on the course ID; input can be in any case, so make it case-insensitive. | Course-ID check made case-insensitive; test added; `DESIGN.md` §5 updated. |
| 2 | Check course and learner validity separately, put learner validity in its own method, then add the learner to the enrolled set or the waitlist. | Extracted `isValidCourse`, `isValidLearner`, `enrollOrWaitlist`; §5 order kept. |
| 3 | Make learner IDs case-insensitive too, and trim any spaces. | Added `normalizeId` (strip + lower-case), used for course IDs and for every learner ID before it is checked or stored; tests added. |
| 4 | Candidate found prerequisites were still matched case-sensitively (`"JAVA"` not met by `"java"`); had `DESIGN.md` state that input to any method is case-insensitive, and asked for the fix. | Added §5 *Input handling*, A13 and test item 16; `meetsPrerequisites` normalizes both sets; tests added. |

**Claude verification:** `BUILD SUCCESSFUL`, no warnings; 25 → 28 → 38 → 45 tests across the
corrections, all passing.

## Task 3 — Drop and FIFO waitlist promotion (2026-11-22)

**Prompt:** Read `DESIGN.md`; implement `drop()` with FIFO waitlist promotion; handle enrolled,
waitlisted, invalid and repeated drops; add focused JUnit tests for promotion, FIFO ordering and
state invariants; run them; update this log.

**Produced:** `drop` per §5 (enrolled → remove and promote the queue head; waitlisted → remove;
null/blank/unknown → `LEARNER_DETAIL_NOT_FOUND`), normalized IDs; 19 tests for promotion, FIFO
order, waitlisted/invalid/repeated drops, re-enrollment at the end of the queue, and capacity.

**Notes:** Promotion does not re-check prerequisites; Claude flagged the §8 case as open.

**Claude verification:** `BUILD SUCCESSFUL`, no warnings; 64/64 tests passed.

**Candidate review**
- Accepted `drop()`, FIFO promotion and the 19 tests as delivered, with no corrections.

## Task 4 — Status lookup and available seats (2026-11-22)

**Prompt:** Read `DESIGN.md`; implement `getLearnerStatus()` and `getAvailableSeats()`; add
focused JUnit tests for enrolled, waitlisted, promoted, dropped and unregistered learners and
for available seat counts; run them; update this log.

**Produced:** `getAvailableSeats()` (capacity − enrolled); `getLearnerStatus()` (null/blank →
`MISSING_LEARNER_DETAILS`, else `ENROLLED` / `WAITLISTED` / `NOT_ENROLLED`, read-only,
normalized ID); 16 tests for statuses, transitions, seat counts and invariants.

**Notes:** A negative capacity would give negative available seats (not validated).

**Claude verification:** `BUILD SUCCESSFUL`, no warnings; 80/80 tests passed.

**Candidate review and corrections**

| # | Candidate comment | Correction by Claude |
| --- | --- | --- |
| 1 | By design, no ineligible learner is ever added to the waitlist (answer to the open §8 case). | Rewrote `DESIGN.md` §8 as the prevention policy; updated §1, §5 *Promotion* and the `promoteFromWaitlist` Javadoc. No behaviour change. |

## Task 5 — Final verification and documentation (2026-11-22)

**Prompt:** Read `DESIGN.md`; review the implementation against the assessment requirements and
design invariants; report missing edge cases, coverage gaps and potential defects before making
changes; run the full suite; finalize `README.md`, `DESIGN.md` and the limitations
documentation; make this log concise without modifying recorded prompts.

**Findings reported (before changes):** all assessment requirements and §6 invariants are met
and tested. Potential defect: `Course` is not validated — a null course ID makes `enroll` throw
`NullPointerException`; capacity below 1 waitlists everyone or gives negative available seats.
Test gaps: waitlisted learner re-requesting with missing prerequisites; re-enrollment after a
drop when a seat is free; null `Course` in the constructor. Edge cases for limitations: Unicode
case folding (e.g. `ß`), blank prerequisite IDs, null set elements, linear waitlist lookup,
single-threaded and in-memory only. `README.md` / `LIMITATIONS.md` missing; `DESIGN.md` wording
outdated.

**Produced:** `DESIGN.md` rewritten as a plain design document reflecting the implementation
(constructor in §4, A14–A15, actual test organization in §9, verification process in §11); this
log condensed (prompts unchanged); `createCourse` (see correction 4).

**Claude verification:** `BUILD SUCCESSFUL`, no warnings; 80/80 tests at review; 94/94 after
correction 4; 96/96 after corrections 5 and 6.

**Candidate review and corrections**

| # | Candidate comment | Correction by Claude |
| --- | --- | --- |
| 1 | Correct `DESIGN.md` wording: it should be a design document, not a prompt. | Removed AI instructions and directive wording; recorded decisions as plain design rules and assumptions. |
| 2 | Keep pending candidate verification as the last entry, and a candidate review in every step. | Added a *Candidate review* section to every task and a final *Candidate verification* section; `DESIGN.md` §11 describes this. |
| 3 | `README.md` and limitations will be revisited later. | Not written yet. |
| 4 | (Answering the `Course` validation finding) No; instead create a public method that creates a course with its prerequisites, saving all inputs in lower case whatever case was entered; invalid input should create no course; call it from the constructor to keep them in sync. | Added `public static Course createCourse(courseId, capacity, prerequisiteCourseIds)`: trims and lower-cases the course ID and prerequisites; throws `IllegalArgumentException` (no course created) for a null/blank ID, capacity below 1, null prerequisite set or null/blank prerequisite. The constructor routes the given `Course` through it. `isValidCourse` / `meetsPrerequisites` now use the stored lower-case values. 14 `CourseCreation` tests added; `DESIGN.md` §4, §5, §6, A15 and §9 item 17 updated. |
| 5 | Add the two remaining gap tests too. | Added `waitlistedLearnerIsReportedBeforePrerequisitesAreRechecked` (waitlisted learner re-requesting with missing prerequisites gets `ALREADY_WAITLISTED`, stays waitlisted) and `reEnrollmentAfterDropTakesAFreeSeat` (dropped learner re-enrolls into a free seat). Test-only change. |
| 6 | No method should work unless the course is created first; add a validation method and call it from all public methods. | Added private `requireCourse()` (throws `IllegalStateException` if `course` is null), called first in `enroll`, `drop`, `getAvailableSeats` and `getLearnerStatus`. The static `createCourse` is excluded because it is what creates the course. Not unit-tested: the constructor already rejects a null/invalid course and `course` is `final`, so the guard cannot be reached through the public API. `DESIGN.md` §4 updated. |

## Task 6 — Interactive CLI (2026-11-22)

**Prompt:** Read `DESIGN.md` and the implementation; implement a simple interactive CLI with
`Scanner`, a numbered menu and `switch`; support Create Course, Enroll Learner, Drop Learner,
Check Status, Available Seats and Exit; keep the CLI to input/output, reusing the service; make
`./gradlew run` launch it; add run instructions to `README.md`; document the CLI in its own
`DESIGN.md` section; run the application and tests; update this log.

**Produced:** `cli/WaitlistCli` (menu loop, one prompt per input, prints the service's enum
result; `Scanner`/`PrintStream` injected through the constructor); Gradle `application` plugin
with `mainClass` and `run { standardInput = System.in }`; `DESIGN.md` §13 (menu-to-API table,
input rules) plus §2, §10 and §12 phase 7; `README.md` with prerequisites, build/test and run
instructions and an example session.

**Notes:** Claude made these CLI decisions; they are recorded in §13 for review:
- Choosing *Create course* again replaced the course and discarded its registrations. The
  candidate found this was a bug (row 2).
- Options 2–5 before a course exists printed a hint and returned to the menu. The candidate
  found this was a bug (row 3).
- A non-numeric capacity is rejected by the CLI; range checks are left to `createCourse`.
- End of input exits the CLI.
- A leading byte-order mark was stripped: PowerShell 5.1 added one to piped input, which made
  the first menu choice invalid in the first scripted run. Removed after review (row 1).
- No CLI unit tests were added: the CLI adds no rules, and the service tests cover the
  behaviour it exposes.

**Claude verification:** `BUILD SUCCESSFUL`, no warnings; 96/96 tests passed. Scripted
sessions through `.\gradlew.bat run -q --console=plain` gave the expected output: no-course
hint, `ENROLLED`, `WAITLISTED`, `INELIGIBLE`, `ALREADY_ENROLLED`, `INVALID_COURSE_ID`,
`DROPPED` with FIFO promotion (status `ENROLLED`, seats 0), repeated drop
`LEARNER_DETAIL_NOT_FOUND`, non-numeric capacity, blank course ID and capacity 0 rejected,
invalid option, exit.

**Candidate review and corrections**

| # | Candidate comment | Correction by Claude |
| --- | --- | --- |
| 1 | The byte-order-mark check in `prompt()` (an invisible U+FEFF literal in the source) is not acceptable; the CLI must be platform-agnostic. | Removed the check, so `prompt()` reads plain lines. Removed the rule from `DESIGN.md` §13. Re-verified with input piped from a POSIX shell, which adds no byte-order mark. |
| 2 | Candidate found a bug: selecting *Create course* again must not be allowed. | Once a course exists, *Create course* now prints `A course has already been created. Only one course is allowed.`, asks for nothing and leaves the course and its registrations unchanged. A failed creation can still be retried. `DESIGN.md` §13 and `README.md` updated. |
| 3 | Candidate found a bug: choosing *Enroll* before any course exists returns to the menu silently; it should prompt the user to create a course first. | The one-line hint was printed but immediately followed by the redrawn menu. Options 2–5 without a course now print `No course yet. Create a course first.` and run the *Create course* prompts; the chosen option is not carried out. `DESIGN.md` §13 updated. |

All three issues were found by the candidate while reviewing the CLI; Claude's own testing had
not caught them. Claude made the corrections. After each one: `BUILD SUCCESSFUL`, no warnings,
96/96 tests passed, and a scripted CLI session confirmed the fix.

## Candidate verification

Independent build and test run by the candidate after all implementation: **Pending candidate
verification.**
