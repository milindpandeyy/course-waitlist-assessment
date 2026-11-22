# Claude AI Log

Factual record of AI assistance (DESIGN.md §11). The candidate owns design decisions and
final acceptance, and runs the independent build verification once all implementation is done.

## Task 1 — Project setup and domain model (2026-11-22)

**Tool:** Claude Code (desktop app), Claude Opus 5.5.

**Prompt:** Read `DESIGN.md`; create a Java Gradle project, the Domain Model types, the Gradle
wrapper and test dependencies; run the build and tests; log the work here.

**Interaction**
1. `DESIGN.md` was missing at first; Claude stopped and asked instead of inventing a design.
   The candidate added it and asked for a retry.
2. Claude built the skeleton and the §3 records, and flagged that §4 listed `NOT_ENROLLED`
   while §5 status lookup returned `NOT_REGISTERED`.
3. Candidate decision: use `NOT_ENROLLED`. Claude updated `DESIGN.md` §5 and §9 to match.
4. On the candidate's request ("Yes, add enums"), Claude added the §4 enums.
5. Candidate decision: record/POJO tests add no value for this feature. Claude deleted
   `src/test/.../model/DomainModelTest.java`; production model classes unchanged.

**Produced**
- Gradle 9.9.1 wrapper, Groovy `build.gradle` / `settings.gradle`, Java 25
  (`options.release = 25`, `-Xlint:all -Werror`), JUnit Jupiter 5.13.4.
- `model`: records `Course`, `LearnerDetails` (defensively copied, unmodifiable sets);
  enums `EnrollmentResult`, `DropResult` copied verbatim from §4.
- `service`: `package-info.java` only; no business logic.
- No test sources yet; JUnit is configured for the service tests of later phases.

**Deviations / notes for review**
- Setup and domain model (§12 phases 1–2) were combined because Task 1 asked for both.
- `LearnerDetails` allows a null ID / null course set so the service can return
  `MISSING_LEARNER_DETAILS`; `Set.copyOf` rejects a null prerequisite set and null elements.
  No other validation was added.
- `README.md`, `LIMITATIONS.md` and `CourseRegistrationService` are not created yet.

**Verification (run by Claude):** `.\gradlew.bat clean build` on Java 25.0.5 →
`BUILD SUCCESSFUL`, no warnings. 5/5 model tests passed before they were removed; after
removal the build passes with `test NO-SOURCE` (no tests run).

**Candidate review**
- Status-lookup conflict resolved: use `NOT_ENROLLED` (accepted the `DESIGN.md` §5/§9 update).
- Approved adding the §4 enums `EnrollmentResult` and `DropResult`.
- Rejected the model tests: record/POJO tests add no value for this feature; production model
  classes kept unchanged.
- Asked for this log to be kept concise.
- Independent re-run of the build by the candidate: **Verified.**

## Task 2 — Enrollment implementation (2026-11-22)

**Tool:** Claude Code (desktop app), Claude Opus 5.5.

**Prompt:** Read `DESIGN.md`; implement enrollment in `CourseRegistrationService` (eligibility,
capacity, waitlisting, duplicates, invalid input); add focused JUnit tests for enrollment
scenarios and state invariants; run them; update this log.

**Produced**
- `service/CourseRegistrationService.java`: constructor `CourseRegistrationService(Course)`,
  state as specified in §3 (`HashSet` enrolled, `ArrayDeque` waitlist), and `enroll` following
  the §5 rule order 1–7 exactly. Removed the Task 1 `package-info.java` placeholder.
- `service/CourseRegistrationServiceTest.java`: tests (with parameterized cases) covering
  §9 items 1–6, 11, 12, the capacity limit, and the §5 check order.

**Deviations / notes for review**
- Only `enroll` was implemented; `drop`, `getAvailableSeats` and `getLearnerStatus` are left
  for §12 phases 4–5. Without them, tests observe state only through later `enroll` results
  (a free seat is still available; an unstored learner is not reported as `ALREADY_*`).
  Waitlist order and "duplicate does not move queue position" (§9 item 6) cannot be observed
  yet and will be tested with drop/promotion.
- The constructor is not specified in `DESIGN.md`; it rejects a null `Course` with
  `NullPointerException`.
- Course and learner IDs ignore case and surrounding whitespace; inner whitespace still counts
  (`"mary ann"` ≠ `"maryann"`). Learner IDs are stored in normalized (trimmed, lower-case) form,
  so `drop` and `getLearnerStatus` must normalize their input the same way.
- `capacity <= 0` is not validated (not specified); such a course waitlists every eligible learner.

**Candidate review and corrections**

Claude's first version implemented `enroll` as one method with an exact `equals` course-ID
check (25/25 tests passed). The candidate's code review changed it as follows:

| # | Candidate comment | Correction by Claude |
| --- | --- | --- |
| 1 | `CourseRegistrationService` line 36 does an `equals` check on the course ID; input can be in any case, so make it case-insensitive. | Course-ID check changed to ignore case; the `"cs201"` invalid-ID test case replaced by a test that mixed-case IDs are accepted. `DESIGN.md` §5 rule 1 updated. |
| 2 | Refactor `enroll`: check course validity and learner validity separately, put learner validity in its own method, then enroll by adding the learner to the enrolled set or the waitlist queue. | Extracted `isValidCourse`, `isValidLearner` and `enrollOrWaitlist`; `enroll` now calls them in the §5 order (course → learner → duplicates → eligibility → enroll/waitlist). Behaviour otherwise unchanged. |
| 3 | (Answering Claude's question about learner IDs and spaces) Make learner IDs case-insensitive too, and trim any spaces. | Added `normalizeId` (`strip()` + `toLowerCase(Locale.ROOT)`), applied to the course-ID comparison and to every learner ID before it is checked or stored. Added tests for trimmed/mixed-case course and learner IDs and for inner whitespace. `DESIGN.md` §5 updated. |
| 4 | The candidate found that Claude's correction 3 missed prerequisite matching: `enroll` still compared prerequisite and completed course IDs case-sensitively, so a prerequisite `"JAVA"` was not met by a completed `"java"`. The candidate updated `DESIGN.md` (via Claude) to state that input to any method is case-insensitive and must be handled accordingly, then asked Claude to fix the code. | Added §5 *Input handling*, assumption A13 and test item 16 to `DESIGN.md`. Replaced the case-sensitive `containsAll` with `meetsPrerequisites`, which normalizes both the prerequisite and the completed course IDs with `normalizeId` before comparing. Added tests: `"JAVA"` met by `java`/`Java`/`" java "`, mixed-case prerequisites, and non-matching inner text (`"ja va"`, `"javascript"`). |

**Verification (run by Claude):** `.\gradlew.bat clean build` on Java 25.0.5 →
`BUILD SUCCESSFUL`, no warnings. First version 25/25; after corrections 1–2: 28/28; after
correction 3: 38/38; after correction 4: 45 tests, 45 passed, 0 failed, 0 skipped (counts from
`build/test-results/test/*.xml`).
