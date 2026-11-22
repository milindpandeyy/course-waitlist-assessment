# Claude AI Log
## AI and Candidate Contributions

**Candidate**
- Reviewed the assessment requirements and made the final design decisions with AI assistance.
- Reviewed the generated code, tests, and deliverables after each implementation step.
- Identified defects, requested corrections, and initiated refactoring to improve separation of concerns and code quality.
- Reviewed the JUnit tests and independently ran the CLI to verify workflows and corner cases.
- Followed the README instructions to validate the application.

**AI Assistance**
- ChatGPT assisted with requirement analysis, design discussions, documentation, and task planning.
- Claude Code implemented the approved tasks, generated unit tests, performed refactoring, and updated documentation.
- Claude ran automated tests and reported results for review.
Factual record of AI assistance (DESIGN.md §11). Tool for requirement tasks: ChatGPT , development tasks: Claude Code (desktop app) , 
Claude Opus 5.5. The candidate owns design decisions and final acceptance, and reviews every task.

Two kinds of verification are recorded separately:
- **Claude verification** is Claude's own build, test and CLI runs during each task.
- **Candidate verification** is the candidate's independent check, recorded in Task 1 and in the final section.

## Task 0 — Requirement Analysis and Design (ChatGPT)

**AI assistance:** Used ChatGPT to analyze the assessment requirements, discuss implementation approaches, and prepare the initial `DESIGN.md`.

**Candidate decisions:**
- Selected plain Java, Gradle, and in-memory storage.
- Chose a minimal implementation for a single course offering.
- Discussed the domain model, service APIs, prerequisite validation, FIFO waitlist, and duplicate handling.
- Reviewed and refined the proposed design, keeping unnecessary abstractions out of the initial implementation.
- Decided to implement the feature incrementally using Claude Code.

**Outcome:** Produced the initial design specification and divided implementation into focused tasks.

**Verification:** Reviewed the proposed design against the assessment requirements before starting implementation.

**AI interaction record:** Design discussions took place in ChatGPT. This entry summarizes those interactions rather than reproducing the full conversation.

**Note:** The design was changed when implementation was ongoing when I(candidate) found the gaps. Claude was used to correct such issues.

## Task 1 — Project setup and domain model (2026-11-22)

**Prompt:** Read `DESIGN.md`; create a Java Gradle project, the Domain Model types, the Gradle
wrapper and test dependencies; run the build and tests; log the work here.

**Produced:** Gradle 9.9.1 wrapper, Groovy build, Java 25 (`-Xlint:all -Werror`), JUnit Jupiter;
records `Course`, `LearnerDetails`; enums `EnrollmentResult`, `DropResult`.

**Notes:** `DESIGN.md` was missing at first; Claude asked for it instead of inventing a design.
Claude flagged a §4/§5 conflict (`NOT_ENROLLED` vs `NOT_REGISTERED`). Phases 1–2 combined at the
candidate's request.

**Claude verification:** `BUILD SUCCESSFUL`, no warnings.

**Candidate review**
- Status-lookup conflict resolved: use `NOT_ENROLLED`.
- Approved adding the enums `EnrollmentResult` and `DropResult`.
- Rejected Claude's model tests: record/POJO tests add no value; removed.
- Candidate verification: independent re-run of the build. **Verified.**

## Task 2 — Enrollment implementation (2026-11-22)

**Prompt:** Read `DESIGN.md`; implement enrollment in `CourseRegistrationService` (eligibility,
capacity, waitlisting, duplicates, invalid input); add focused JUnit tests for enrollment
scenarios and state invariants; run them; update this log.

**Produced:** `enroll` in the `DESIGN.md` §5 rule order, with enrollment tests.

**Claude verification:** `BUILD SUCCESSFUL`, no warnings; 45/45 tests passed after the
corrections.

**Candidate review and corrections**

| # | Candidate comment | Correction by Claude |
| --- | --- | --- |
| 1 | The course-ID `equals` check must be case-insensitive. | Made case-insensitive; test added. |
| 2 | Check course and learner validity separately, in their own methods, then enroll or waitlist. | Extracted `isValidCourse`, `isValidLearner`, `enrollOrWaitlist`. |
| 3 | Make learner IDs case-insensitive too, and trim spaces. | Added `normalizeId` (strip + lower-case) for every ID. |
| 4 | **Defect found by the candidate:** prerequisites were still matched case-sensitively (`"JAVA"` not met by `"java"`). | `meetsPrerequisites` normalizes both sides; `DESIGN.md` states that all input is case-insensitive. |

## Task 3 — Drop and FIFO waitlist promotion (2026-11-22)

**Prompt:** Read `DESIGN.md`; implement `drop()` with FIFO waitlist promotion; handle enrolled,
waitlisted, invalid and repeated drops; add focused JUnit tests for promotion, FIFO ordering and
state invariants; run them; update this log.

**Produced:** `drop` (enrolled → remove and promote the queue head; waitlisted → remove;
null/blank/unknown → `LEARNER_DETAIL_NOT_FOUND`) with 19 tests. Claude flagged the "ineligible
learner at the front of the waitlist" case as open.

**Claude verification:** `BUILD SUCCESSFUL`, no warnings; 64/64 tests passed.

**Candidate review**
- Accepted as delivered.

## Task 4 — Status lookup and available seats (2026-11-22)

**Prompt:** Read `DESIGN.md`; implement `getLearnerStatus()` and `getAvailableSeats()`; add
focused JUnit tests for enrolled, waitlisted, promoted, dropped and unregistered learners and
for available seat counts; run them; update this log.

**Produced:** `getAvailableSeats()` and `getLearnerStatus()` (read-only) with 16 tests.

**Claude verification:** `BUILD SUCCESSFUL`, no warnings; 80/80 tests passed.

**Candidate review and corrections**

| # | Candidate comment | Correction by Claude |
| --- | --- | --- |
| 1 | By design, no ineligible learner is ever added to the waitlist (answers the open case). | `DESIGN.md` §8 rewritten as the prevention policy. No behaviour change. |

## Task 5 — Final verification and documentation (2026-11-22)

**Prompt:** Read `DESIGN.md`; review the implementation against the assessment requirements and
design invariants; report missing edge cases, coverage gaps and potential defects before making
changes; run the full suite; finalize `README.md`, `DESIGN.md` and the limitations
documentation; make this log concise without modifying recorded prompts.

**Findings reported (before changes):**
- All requirements and invariants are met and tested.
- **Defect:** `Course` was not validated. A null course ID made `enroll` throw
  `NullPointerException`, and a capacity below 1 was accepted.
- Two test gaps.

**Claude verification:** `BUILD SUCCESSFUL`, no warnings; 96/96 tests passed after the
corrections.

**Candidate review and corrections**

| # | Candidate comment | Correction by Claude |
| --- | --- | --- |
| 1 | `DESIGN.md` must read as a design document, not a prompt. | Rewrote it as plain design rules and assumptions. |
| 2 | A candidate review in every task; pending candidate verification last. | Added both to this log. |
| 3 | `README.md` and limitations to be revisited later. | Deferred. |
| 4 | Answering the `Course` defect: "No"; instead, a public method that creates the course, stores all input in lower case and creates nothing on invalid input, called from the constructor. | Added `createCourse` (throws `IllegalArgumentException` on invalid input); the constructor routes through it; 14 tests. |
| 5 | Add the two gap tests. | Added. |
| 6 | No method may work before the course exists; add a guard called from every public method. | Added `requireCourse()`. At this point it could not be reached through the public API, so it was not unit-tested. |

## Task 6 — Interactive CLI (2026-11-22)

**Prompt:** Read `DESIGN.md` and the implementation; implement a simple interactive CLI with
`Scanner`, a numbered menu and `switch`; support Create Course, Enroll Learner, Drop Learner,
Check Status, Available Seats and Exit; keep the CLI to input/output, reusing the service; make
`./gradlew run` launch it; add run instructions to `README.md`; document the CLI in its own
`DESIGN.md` section; run the application and tests; update this log.

**Produced:** `WaitlistCli`, Gradle `application` plugin (`./gradlew run`), `DESIGN.md` §13 and
`README.md` run instructions. No CLI unit tests: the CLI has no rules of its own.

**Claude verification:** `BUILD SUCCESSFUL`, no warnings; 96/96 tests passed; scripted sessions
through `./gradlew run` matched the expected output after each correction.

**Candidate review and corrections.** All three issues were found by the candidate; Claude's
own testing had not caught them.

| # | Candidate comment | Correction by Claude |
| --- | --- | --- |
| 1 | The byte-order-mark check Claude added (an invisible U+FEFF literal) is not acceptable; the CLI must be platform-agnostic. | Removed. |
| 2 | **Defect:** choosing *Create course* again replaced the course and lost its registrations; it must not be allowed. | A second create is refused; a failed create can be retried. |
| 3 | **Defect:** choosing *Enroll* before a course exists returned to the menu silently; it should prompt to create a course first. | Options 2–5 without a course now start the *Create course* prompts. |

## Task 7 — README documentation (2026-11-22)

**Prompt:** Read `DESIGN.md` and inspect the implementation; create/update `README.md` so a new
user can clone, build, test and run the interactive CLI, with prerequisites and setup, exact
commands, CLI menu options with a sample enrollment/waitlist workflow, and relevant
limitations; verify the documented commands where possible; do not invent repository URLs or
unverified instructions; update this log concisely without modifying recorded prompts; do not
change application code.

**Produced:** `README.md` with prerequisites, clone/build/test/run commands, the CLI menu, a
sample workflow and limitations. The clone URL comes from `git remote -v`. Claude found that
`gradlew` was committed without the executable bit.

**Claude verification:** in a fresh anonymous clone, the build and test commands succeeded
(Git Bash and PowerShell), and the README workflow through `./gradlew run` printed the
documented outputs.

**Candidate review and corrections**

| # | Candidate comment | Correction by Claude |
| --- | --- | --- |
| 1 | Approved fixing the `gradlew` file mode. | Set to `100755`; README workaround removed. |

## Task 8 — Refactoring (2026-11-22)

**Prompt:** Branch out from the current branch for a refactoring. Proposals:
- rename `com.example.waitlist` to `com.assessment.enrollment`;
- rename `WaitlistCli` to `EnrollmentCli`;
- reduce `CourseRegistrationService` to an `EnrollmentService` containing only enrollment, drop,
  status and capacity;
- add a `CourseConfigurationService` to configure the course once.

Review this first for good coding practices such as Separation of Concerns, DRY and YAGNI.
Then: "GoAhead. lets do it cleanly. Lets keep cli free from business rules."

**Review reported (before changes):** the split separates two mixed concerns. Course creation
lived on the registration service, and the "configure once" rule lived in the CLI, untested.
Claude recommended:
- `EnrollmentService` reads the course from the configuration service, giving one validated
  source;
- one shared ID helper (DRY);
- no interfaces, catalog or reconfiguration (YAGNI).

**Produced:**
- **Packages and names:** `com.assessment.enrollment`; `EnrollmentCli`.
- **`CourseConfigurationService`:** configure once (`IllegalStateException` on a second call),
  validation, lower-case IDs.
- **`EnrollmentService`:** enroll, drop, status, seats. The `requireCourse` guard is now
  reachable and tested.
- **`Ids`:** shared normalization and blank check, replacing duplicated code.
- **`EnrollmentCli`:** no business rules; `main` wires the services.
- **Tests:** split into `EnrollmentServiceTest` and `CourseConfigurationServiceTest`.

**Claude verification:** `BUILD SUCCESSFUL`, no warnings; 101/101 tests passed; CLI output
unchanged from before the refactor.

**Candidate review**
- The design change was the candidate's proposal, applying Separation of Concerns, DRY and
  YAGNI. Claude reviewed it and implemented it.
- Accepted as delivered.

## Task 9 — Available courses and no-prerequisite check (2026-11-22)

**Prompt:** The candidate verified that a wrong course ID returns `INVALID_COURSE_ID` and asked
whether showing the valid course ID in the output is worth doing. Claude proposed a CLI-only
hint. The candidate rejected that: it would tie the CLI to one course, against the single
responsibility principle. Then: open a new method and add a CLI option to see the available
courses. Also: when a course is configured without prerequisites, no learner would be added to
the waitlist; a learner giving 3 completed courses for a course without prerequisites should be
allowed to enroll. Check this test case.

**Produced:** `CourseConfigurationService.getAvailableCourseIds()` and CLI option 6 *Available
courses* (*Exit* moved to option 7), with tests. No-prerequisite check: **no defect**. The new
test (a learner with 3 completed courses is `ENROLLED`, and later learners are `WAITLISTED`)
passed before any code change.

**Claude verification:** `BUILD SUCCESSFUL`, no warnings; 105/105 tests passed.

**Candidate review**
- User friendliness: `INVALID_COURSE_ID` alone does not tell the user which course ID is valid.
- Rejected Claude's CLI-only hint (single responsibility principle). The course list comes from
  a service method that the CLI only displays.
- Accepted as delivered.

## Task 10 — Assessment review and limitations (2026-11-22)

**Prompt:** Read the assessment PDF, act as a reviewer of the assessment and of the application,
and check whether all deliverables are present or anything is pending. Then: remove
`LIMITATIONS.md` and add 2 separate sections to `DESIGN.md` with the application's limitations,
such as concurrency not supported and missing semantic validation. Examples: no validation on
the length of the course ID, and no limit on the prerequisite list. The application is not
thread-safe.

**Review reported (before changes):** all 7 acceptance criteria are met and all deliverables are
present. Open items:
- the code is not on `main`;
- `DESIGN.md` lists a missing `LIMITATIONS.md`;
- the concurrency approach is scattered across the document;
- follow-up risks are not stated.

**Produced:** `DESIGN.md` only. `LIMITATIONS.md` removed from the layout; new §14 *Concurrency
and persistence* and §15 *Limitations and follow-up risks* (including missing semantic
validation).

**Claude verification:** each listed limitation was checked against the code.

**Candidate review**
- The candidate specified the two sections and the limitations to record.

## Candidate verification

I reviewed the deliverables after each implementation step, including the generated code and unit tests. During these reviews, I identified bugs and requested corrections. I also initiated refactoring where the implementation violated clean coding principles, particularly separation of concerns.

For final verification, I followed the README instructions, cloned the repository mentioned, ran the application through the CLI, and manually validated the supported workflows and corner cases. I also reviewed the JUnit test cases to check their coverage of the requirements.

The issues identified during my reviews were corrected, and I manually verified the final application. The known limitations are documented in `DESIGN.md`.
