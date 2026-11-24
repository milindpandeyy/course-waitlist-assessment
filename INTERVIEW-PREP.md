# Interview Preparation — Course Waitlist Assessment

Probable questions from a Principal Java Engineer, grounded in this repository as committed on
`assessment/enrollment-poc` (`2fe7710`). The questions are ranked by how likely an interviewer is to ask them:
the first ones challenge decisions an interviewer sees within minutes of opening the code.

**Legend.** *Area* numbers match the requested coverage:
1 Architecture/SOLID/refactoring · 2 Enrollment rules and invariants · 3 Java 25, collections,
complexity · 4 Concurrency · 5 Testing · 6 AI-assisted development · 7 Change requests.
Questions tagged **SCENARIO** are the five scenario-based questions (concurrency, race
conditions or changed requirements).

| # | Question (short) | Area | Difficulty |
| --- | --- | --- | --- |
| 1 | Is `EnrollmentService` thread-safe, and how would you make it so? | 4 | Hard |
| 2 | **SCENARIO** Two learners request the last seat at the same time | 4 | Hard |
| 3 | Why `ArrayDeque` for the waitlist and not `LinkedHashSet`? | 3 | Medium |
| 4 | Why are prerequisites not re-checked at promotion? | 2 | Medium |
| 5 | **SCENARIO** Prerequisites can now be revoked after queueing | 7 | Hard |
| 6 | Why two services, and why no interfaces? | 1 | Medium |
| 7 | Result enums vs exceptions: why both? | 1 | Medium |
| 8 | What did *you* catch that the AI missed? | 6 | Medium |
| 9 | Walk me through the `enroll` check order | 2 | Medium |
| 10 | **SCENARIO** Concurrent drop and enroll — who gets the freed seat? | 4 | Hard |
| 11 | The hidden invariant that keeps the queue fair | 2 | Hard |
| 12 | **SCENARIO** Support many courses | 7 | Hard |
| 13 | Records: why does `Course` not validate itself? | 3 | Medium |
| 14 | `EnrollmentResult` is used for both commands and status lookup | 1 | Medium |
| 15 | ID normalization: `strip`, `Locale.ROOT`, and the limits | 3 | Medium |
| 16 | What test coverage do you actually have? | 5 | Medium |
| 17 | Which important test scenarios are missing? | 5 | Medium |
| 18 | How would you test concurrency? | 4/5 | Hard |
| 19 | Database-backed production design | 4 | Hard |
| 20 | `EnrollmentService` reads the course on every call — why? | 1/4 | Medium |
| 21 | Is depending on concrete `CourseConfigurationService` a DIP violation? | 1 | Medium |
| 22 | **SCENARIO** The admin can now change capacity | 7 | Hard |
| 23 | Time complexity of each operation | 3 | Medium |
| 24 | Idempotency: what kind do you actually provide? | 2 | Medium |
| 25 | Primitive obsession: `String` IDs vs value objects | 1/3 | Medium |
| 26 | Return the waitlist position from `enroll` (Java 25 types) | 3/7 | Medium |
| 27 | Is the CLI really free of business rules? | 1 | Medium |
| 28 | Why did you reject the AI's tests for records? | 5/6 | Medium |
| 29 | How do you know the AI-written tests can fail? | 5/6 | Medium |
| 30 | Immutability and defensive copies in this code | 3 | Medium |
| 31 | `configure` under concurrency and memory visibility | 4 | Hard |
| 32 | Edge cases at the input boundary | 2 | Medium |
| 33 | `drop` semantics: blank ID and waitlisted learners | 2 | Medium |
| 34 | Make eligibility rules extensible (OCP) | 1/7 | Medium |
| 35 | Add a seat-offer window (accept within 24 h) | 7 | Hard |
| 36 | Re-enrollment after drop goes to the back of the queue — fair? | 2 | Medium |
| 37 | Build choices: `--release 25`, `-Xlint:all -Werror` | 3 | Medium |
| 38 | What would you refactor next, given one more day? | 1 | Medium |
| 39 | How did you keep the AI inside the design? | 6 | Medium |
| 40 | Add persistence without breaking the services | 7 | Hard |

---

## 1. Is `EnrollmentService` thread-safe? How would you make it so?

- **Area:** 4 · **Difficulty:** Hard
- **Relevant code:** `EnrollmentService` (fields `enrolledLearners`, `waitlistedLearners`;
  `enroll`, `drop`, `enrollOrWaitlist`, `promoteFromWaitlist`); DESIGN.md §14.

**Expected answer.** No, and that is a documented decision (A4, DESIGN.md §14). State is a plain
`HashSet` plus an `ArrayDeque`, and every mutating operation is check-then-act:
`enrollOrWaitlist` checks `enrolledLearners.size() < course.capacity()` and then adds;
`drop` removes and then calls `promoteFromWaitlist()`. The CLI is a single-threaded loop, so the
races cannot happen through the shipped entry point.

To make it safe in-process, the invariants span **two** collections (capacity, "not both
enrolled and waitlisted", FIFO), so swapping in `ConcurrentHashMap.newKeySet()` and
`ConcurrentLinkedDeque` does **not** help: each call would be atomic, but the compound operation
would not. The right unit of atomicity is the whole operation on one course:

- simplest: make `enroll`, `drop`, `getLearnerStatus`, `getAvailableSeats` `synchronized`
  (one monitor per course — contention is bounded by one offering);
- or a `ReentrantLock`/`ReentrantReadWriteLock` (reads take the read lock because status reads
  both collections and needs a consistent snapshot);
- or a single-writer design (one thread/actor per course consuming commands from a queue).

**Likely follow-up:** "Why not just use concurrent collections?" — answer above; also note
`ArrayDeque` is not thread-safe at all and concurrent `add` can corrupt it.

## 2. SCENARIO — Two learners request the last seat at the same time

- **Area:** 4 · **Difficulty:** Hard
- **Relevant code:** `EnrollmentService.enrollOrWaitlist`, `enroll`.

**Scenario.** Capacity 30, 29 enrolled. Threads A (alice) and B (bob) call `enroll` together.

**Expected answer.** Both can read `enrolledLearners.size() == 29`, both pass `< 30`, both `add`
→ 31 enrolled; invariant 1 of DESIGN.md §6 breaks and `getAvailableSeats()` returns −1. Worse,
concurrent `HashSet.add` can corrupt the table or lose an element, so one learner may get
`ENROLLED` and not be stored. Same learner on two threads: both pass the
`waitlistedLearners.contains` check and `ArrayDeque` (which allows duplicates) holds alice twice,
breaking invariant 2. Fix: one lock around the full `enroll` body (check + mutation), as in Q1. In
a DB design: lock the course row (`SELECT … FOR UPDATE`) or a conditional update
(`UPDATE course SET enrolled = enrolled + 1 WHERE id = ? AND enrolled < capacity`, check rows
affected) plus a unique constraint on `(course_id, learner_id)`.

**Likely follow-up:** "Can you reproduce it in a test?" → Q18.

## 3. Why `ArrayDeque` for the waitlist and not `LinkedHashSet`?

- **Area:** 3 · **Difficulty:** Medium
- **Relevant code:** `EnrollmentService.waitlistedLearners`; `enroll` (`contains`), `drop`
  (`remove`), `getLearnerStatus`; DESIGN.md §3, §15.

**Expected answer.** `ArrayDeque` states the intent (a FIFO `Queue`) and gives O(1) `add`/`poll`,
but `contains` and `remove(Object)` are O(n), and it does not prevent duplicates — uniqueness
relies on the `ALREADY_WAITLISTED` check in `enroll`. This was a deliberate "bounded exercise"
trade-off, recorded as a limitation (DESIGN.md §15).

`LinkedHashSet` would give insertion order, O(1) `contains`/`remove`, and uniqueness by
construction. Since Java 21 it is a `SequencedSet`, so promotion becomes
`waitlist.isEmpty() ? null : waitlist.removeFirst()` (there is no `pollFirst()` on
`LinkedHashSet`, and `removeFirst` throws `NoSuchElementException` when empty). The cost is weaker intent (`Set`, not
`Queue`) and more memory per entry. For a real course with thousands waiting I would switch.

**Likely follow-up:** "What if you also need the position in the queue?" → O(n) either way,
unless you keep a sequence number per entry (`TreeMap<Long, String>` + `HashMap<String, Long>`)
or compute rank in the DB.

## 4. Why are prerequisites not re-checked at promotion?

- **Area:** 2 · **Difficulty:** Medium
- **Relevant code:** `EnrollmentService.promoteFromWaitlist`; DESIGN.md §8, A1–A3.

**Expected answer.** The assessment asks for a policy for "an ineligible learner at the front of
the waitlist". The chosen policy is **prevention**: `enroll` checks prerequisites before a learner
can be stored anywhere (rule 5 → `INELIGIBLE`), completion is permanent (A1), and the API offers
no way to change a queued learner's completed courses. So the head of the queue is always
eligible and re-checking would be dead code. The policy was the candidate's decision after
Claude flagged the case as open in Task 3.

A sharper point: re-checking is not even *possible* today — the service stores only the
normalized learner ID, not `LearnerDetails`. Any re-validation needs a source of truth for
completions.

**Likely follow-up:** "When does prevention stop being enough?" → Q5.

## 5. SCENARIO — Prerequisites can now be revoked after queueing

- **Area:** 7 · **Difficulty:** Hard
- **Relevant code:** `promoteFromWaitlist`, `meetsPrerequisites`, `waitlistedLearners`.

**Scenario.** Product says: "A learner's completion can be revoked (failed audit), and admins may
add a prerequisite to an open course."

**Expected answer.** A1 and §8 no longer hold, so the prevention policy breaks (DESIGN.md §15
already names this risk). Changes:

1. Eligibility must come from a source of truth (`CompletionService`/repository), not from the
   `LearnerDetails` the learner passed in, because the queue stores only IDs.
2. `promoteFromWaitlist` becomes a loop: poll the head, re-check, enroll the first eligible
   learner; for each ineligible one apply an explicit policy — remove and notify (simplest),
   or keep their place but skip (fairer, but then the head can be ineligible forever and a
   "skip" counter or expiry is needed).
3. The loop must still promote **at most one** learner per freed seat.
4. Tests: ineligible head is skipped, the next eligible one is promoted, an all-ineligible queue
   leaves the seat free, FIFO among eligible learners is preserved.

**Likely follow-up:** "Do you re-check enrolled learners too?" — that is a separate business
decision (revoking an existing seat); I would not do it silently.

## 6. Why two services, and why no interfaces?

- **Area:** 1 · **Difficulty:** Medium
- **Relevant code:** `CourseConfigurationService`, `EnrollmentService`; AI log Task 8.

**Expected answer.** Originally one `CourseRegistrationService` did creation and registration, and
the "configure once" rule lived only in the CLI, untested. In Task 8 I split it on
Separation of Concerns: `CourseConfigurationService` owns course creation, validation,
normalization and configure-once; `EnrollmentService` owns enrollment, drop, status and seats and
reads the course from the configuration service. No interfaces because there is one
implementation and no second consumer — YAGNI; both classes are `final`, and tests use the real
objects without mocks because they are in-memory and fast. An interface is cheap to extract the
day a second implementation (e.g. persistent) appears.

**Likely follow-up:** "When would you introduce the interface?" — at the first second
implementation or at a module boundary (e.g. a `CourseCatalog` port backed by a DB).

## 7. Result enums vs exceptions: why both?

- **Area:** 1 · **Difficulty:** Medium
- **Relevant code:** `CourseConfigurationService.configure` (throws), `EnrollmentService.enroll`/
  `drop` (return enums), `requireCourse`.

**Expected answer.** Expected business outcomes — full course, ineligible, duplicate, unknown
learner — are normal results that a caller must handle, so they are return values
(`EnrollmentResult`, `DropResult`). Exceptions mark misuse or a broken precondition: configuring
twice (`IllegalStateException`), invalid configuration (`IllegalArgumentException`), calling
`EnrollmentService` before a course exists (`IllegalStateException`), a null configuration
service (`NullPointerException`). The line is "caller error vs domain outcome".

The weak spot: invalid *learner* input (`MISSING_LEARNER_DETAILS`, `INVALID_COURSE_ID`) is
returned, not thrown, while invalid *course* input throws. That is defensible — learner requests
come from end users, configuration from an admin — but I would say so explicitly.

**Likely follow-up:** "How would this look in a REST API?" — enums map to 200/409/422 bodies;
`IllegalStateException` before configuration maps to 409 or 503.

## 8. What did *you* catch that the AI missed?

- **Area:** 6 · **Difficulty:** Medium
- **Relevant code:** `CLAUDE-AI-LOG.md` Tasks 2, 5, 6, 9.

**Expected answer (from the log).**

- **Task 2:** prerequisites were still matched case-sensitively (`"JAVA"` not met by `"java"`)
  after the course and learner IDs had been made case-insensitive → `meetsPrerequisites` now
  normalizes both sides.
- **Task 6:** choosing *Create course* again silently replaced the course and lost its
  registrations; choosing *Enroll* before a course existed returned silently; and Claude had
  added an invisible U+FEFF byte-order-mark check, which I rejected as platform-specific. The log
  records that Claude's own scripted CLI runs had not caught these.
- **Task 5:** the null-course-ID `NullPointerException` and capacity < 1 were reported by Claude
  in its review; the *fix design* (no `Course` construction on invalid input, one place that
  validates) was mine.
- **Task 9:** I rejected a CLI-only "valid course is X" hint because it tied the CLI to one
  course; the list comes from `getAvailableCourseIds()` instead.

**Likely follow-up:** "Why did the AI's tests not catch the case bug?" → it tested the cases it
was told about; the test only exists once someone thinks of the rule. Hence review of the
*rules*, not just of the green build.

## 9. Walk me through the `enroll` check order

- **Area:** 2 · **Difficulty:** Medium
- **Relevant code:** `EnrollmentService.enroll`; DESIGN.md §5.

**Expected answer.** `requireCourse()` → course ID (`INVALID_COURSE_ID`) → learner details
(`MISSING_LEARNER_DETAILS`) → already enrolled → already waitlisted → prerequisites
(`INELIGIBLE`) → seat or queue. First match wins; results 1–5 change nothing.

Why this order: the course ID scopes the request, so it comes first
(`courseIdIsCheckedBeforeLearnerDetails`). Duplicates are checked **before** prerequisites, so a
learner who already holds a seat or a place gets `ALREADY_*` even if they now send an empty
completed-course set (`enrolledLearnerIsReportedBeforePrerequisitesAreRechecked`). That is
correct under A1 and makes repeated requests idempotent on identity. Missing details are checked
before duplicates, so a blank ID never matches a stored learner
(`missingDetailsAreReportedBeforeWaitlistDuplicateCheck`).

**Likely follow-up:** "Duplicate check before eligibility leaks state — a caller learns alice is
enrolled." True; with authentication the caller can only ask about themselves, so it is fine.

## 10. SCENARIO — Concurrent drop and enroll: who gets the freed seat?

- **Area:** 4 · **Difficulty:** Hard
- **Relevant code:** `drop` → `enrolledLearners.remove` then `promoteFromWaitlist`;
  `enrollOrWaitlist`.

**Scenario.** Course full, bob first in the queue. Thread A: `drop("alice")`. Thread B:
`enroll("carol")` at the same moment.

**Expected answer.** Without a lock, B can run between `enrolledLearners.remove` and
`promoteFromWaitlist`: it sees a free seat and takes it, then A promotes bob → capacity + 1.
Or, interleaved differently, carol jumps ahead of bob — a FIFO violation even if capacity
happens to hold. Two concurrent `drop("alice")` calls can both see `remove` return `true` on a
corrupted `HashSet` and promote twice. The fix is again one critical section per course covering
remove + promote, and the enroll check + add. With a DB, `drop` and promotion must be **one
transaction** — never "drop now, promote in a later async job" unless new enrollments are also
blocked while the queue is non-empty (see Q11).

**Likely follow-up:** "What if promotion sends an email?" — commit first, then publish an event
(transactional outbox); never send inside the lock.

## 11. The hidden invariant that keeps the queue fair

- **Area:** 2 · **Difficulty:** Hard
- **Relevant code:** `enrollOrWaitlist`, `drop`, `promoteFromWaitlist`.

**Question.** `enrollOrWaitlist` only checks for a free seat; it never checks whether the queue
is empty. Why does a newcomer never jump the queue?

**Expected answer.** Because of an invariant the code maintains but DESIGN.md §6 does not state:
**waitlist non-empty ⇒ course full.** Learners are queued only when full; dropping an enrolled
learner immediately promotes the head; dropping a waitlisted learner frees no seat. So whenever a
seat is free, the queue is empty and the newcomer is correctly enrolled. Any change that frees a
seat without promoting — capacity increase, async promotion, admin removal — breaks it silently
and newcomers would overtake waiting learners. I would either add it to §6 and a test, or make
`enrollOrWaitlist` check `waitlistedLearners.isEmpty()` defensively.

**Likely follow-up:** "Write the test." — after a capacity increase (Q22) a new learner must be
`WAITLISTED` behind the existing queue or the queue must be promoted first.

## 12. SCENARIO — Support many courses

- **Area:** 7 · **Difficulty:** Hard
- **Relevant code:** `CourseConfigurationService.getAvailableCourseIds`, `EnrollmentService`
  (single `course`, `drop(String)`, `getLearnerStatus(String)`, `getAvailableSeats()`).

**Expected answer.**

- `CourseConfigurationService` becomes a catalog: `Map<String, Course>` keyed by normalized ID;
  "configure once" becomes "configure once per ID". `getAvailableCourseIds()` already returns a
  `Set` and the CLI only displays it — that was why I chose a list over a CLI hint (Task 9).
- `EnrollmentService` keeps per-course state: `Map<String, CourseRoster>` where `CourseRoster`
  holds the enrolled set and the queue. Extracting `CourseRoster` is also the natural lock unit.
- **API break:** `drop`, `getLearnerStatus` and `getAvailableSeats` take no course ID today; they
  need one. `enroll`'s `INVALID_COURSE_ID` then means "not in catalog".
- New rules to decide: can a learner be waitlisted on several courses? Are prerequisites
  satisfied by enrollments in this system (a catalog lets us validate prerequisite IDs — today a
  course may even list itself, DESIGN.md §15)?

**Likely follow-up:** "Would you then add interfaces?" — a repository port, yes.

## 13. Records: why does `Course` not validate itself?

- **Area:** 3 · **Difficulty:** Medium
- **Relevant code:** `Course`, `LearnerDetails` (compact constructors),
  `CourseConfigurationService.configure`; A12, A15.

**Expected answer.** The compact constructors only take an unmodifiable copy (`Set.copyOf`).
Validation lives in `CourseConfigurationService` (A15) so there is exactly one place that
validates *and* normalizes and returns a clear `IllegalArgumentException` message.
`LearnerDetails` must *not* validate, because a blank ID or null set must reach the service to be
reported as `MISSING_LEARNER_DETAILS` (A6, A12).

The honest counter-argument: `Course` is public, so `new Course("x", -5, Set.of())` is
representable; a record validating itself makes illegal states unrepresentable. It is safe here
only because `EnrollmentService` never accepts a `Course` from outside — it reads it from the
configuration service. If `Course` became an API input, I would move the range checks into its
compact constructor.

**Likely follow-up:** "What happens with `new LearnerDetails("a", setWithNull)`?" —
`Set.copyOf` throws `NullPointerException` in the record, before the service sees it; there is
no test for it (Q17).

## 14. `EnrollmentResult` is used for both commands and status lookup

- **Area:** 1 · **Difficulty:** Medium
- **Relevant code:** `EnrollmentResult`, `getLearnerStatus`; AI log Task 1.

**Expected answer.** `getLearnerStatus` returns `ENROLLED`, `WAITLISTED`, `NOT_ENROLLED` or
`MISSING_LEARNER_DETAILS` — four of eight values. `NOT_ENROLLED` is never returned by `enroll`,
and `ALREADY_*`/`INELIGIBLE` are never a status. A caller's exhaustive `switch` has to handle
impossible cases. It was a conscious minimal choice (DESIGN.md said status reuses the enum; I
resolved the `NOT_ENROLLED` vs `NOT_REGISTERED` conflict Claude raised), but it mixes "outcome of
a command" with "state of a learner". Cleaner: a separate `RegistrationStatus { ENROLLED,
WAITLISTED, NOT_ENROLLED }` and throw or return `Optional` for a blank ID.

**Likely follow-up:** "Would you change it now?" — yes if the API is published; it is a breaking
change, so before release.

## 15. ID normalization: `strip`, `Locale.ROOT`, and the limits

- **Area:** 3 · **Difficulty:** Medium
- **Relevant code:** `Ids.normalize`, `Ids.normalizeAll`, `Ids.isBlank`.

**Expected answer.** `id.strip().toLowerCase(Locale.ROOT)`.

- `strip()` (Java 11) removes Unicode whitespace by `Character.isWhitespace`, unlike `trim()`,
  which removes chars ≤ U+0020. Neither removes a no-break space (U+00A0).
- `Locale.ROOT` avoids the Turkish-locale bug where `"I".toLowerCase()` becomes dotless `ı`, so
  `"JAVA"` and `"java"` would not match on a Turkish JVM.
- Normalization happens once at the service boundary and IDs are **stored** normalized, so
  lookups are plain `HashSet.contains` — cheaper and less error-prone than `equalsIgnoreCase`
  everywhere.
- Limits (DESIGN.md §15): no full case folding (`ß` vs `SS`), inner whitespace matters, no
  length limit, no Unicode normalization (NFC vs NFD `é`).

**Likely follow-up:** "Fix `ß`/`SS`?" — `toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT)` or
ICU case folding plus `Normalizer.normalize(id, NFKC)`.

## 16. What test coverage do you actually have?

- **Area:** 5 · **Difficulty:** Medium
- **Relevant code:** `EnrollmentServiceTest`, `CourseConfigurationServiceTest`, `build.gradle`.

**Expected answer.** 105 JUnit Jupiter tests (counting parameterized invocations) in two classes,
grouped with `@Nested` (`SeatsAndWaitlist`, `Prerequisites`, `DuplicateRequests`, `InvalidInput`,
`DropAndPromotion`, `BeforeConfiguration`, `StatusAndSeats`). They cover every scenario the
assessment lists, mapped in DESIGN.md §9. Tests observe state only through the public API — no
getters for internal collections — e.g. "not stored" is proven by a later `enroll` returning a
fresh result instead of `ALREADY_*`.

What I cannot claim: a coverage percentage. JaCoCo is not configured, so line/branch coverage is
not measured; and there is no mutation testing. I would add the `jacoco` plugin and PIT before
quoting a number. The CLI has no automated tests (scripted manual runs only).

**Likely follow-up:** "Which line is uncovered?" — honestly unknown without the report; likely
candidates are CLI paths.

## 17. Which important test scenarios are missing?

- **Area:** 5 · **Difficulty:** Medium
- **Relevant code:** test classes; `EnrollmentCli`; `LearnerDetails`.

**Expected answer.**

- **Concurrency** — none (out of scope, DESIGN.md §9).
- **CLI** — no tests for `splitIds`, menu flow, EOF mid-prompt, or capacity overflow:
  `Integer.parseInt("99999999999")` prints "Capacity must be a whole number", which is misleading.
  The CLI's constructor already takes `Scanner`/`PrintStream`, so it is testable with
  `ByteArrayInputStream`/`ByteArrayOutputStream`.
- **Null element in a set.** A `HashSet` containing `null` passed as completed courses makes the
  `LearnerDetails` constructor throw `NullPointerException` (`Set.copyOf`), so the service never
  gets to answer `MISSING_LEARNER_DETAILS`. The same set passed as prerequisites to `configure`
  is caught by the blank-ID loop and gives `IllegalArgumentException`. Neither path is tested.
- **Self-prerequisite** course, **`Integer.MAX_VALUE` capacity**, a **large waitlist**
  (performance of linear `contains`).
- **Property/model-based tests**: `invariantsHoldAfterMixedOperations` is one hand-picked
  sequence; a random operation sequence checked against a simple reference model (jqwik) would
  test the §6 invariants far more broadly.
- The "waitlist non-empty ⇒ full" invariant (Q11) is not asserted directly.

**Likely follow-up:** "Pick one to write now." — a jqwik property test or the CLI overflow case.

## 18. How would you test concurrency?

- **Area:** 4/5 · **Difficulty:** Hard
- **Relevant code:** `EnrollmentService` (after adding locking).

**Expected answer.** First write a test that fails today: capacity N, start 10·N virtual threads
(`Executors.newVirtualThreadPerTaskExecutor()`) released together by a `CountDownLatch`, each
enrolling a distinct learner; assert exactly N `ENROLLED`, the rest `WAITLISTED`,
`getAvailableSeats() == 0`, and every learner has exactly one status. Repeat many times
(`@RepeatedTest`) because races are probabilistic. Add mixed enroll/drop runs and assert the §6
invariants at the end. For deterministic proof, use `jcstress` for small interleavings, or
Lincheck-style linearizability checks. A green stress test is evidence, not proof — the lock
design must be argued by reasoning too.

**Likely follow-up:** "Why does a passing stress test before your fix not mean it is safe?" —
races may not manifest on your hardware/JIT; absence of failure ≠ correctness.

## 19. Database-backed production design

- **Area:** 4 · **Difficulty:** Hard
- **Relevant code:** state in `EnrollmentService`; DESIGN.md §14–§15.

**Expected answer.** Tables `course(id, capacity, version)`,
`registration(course_id, learner_id, status, queued_seq, created_at)` with a unique constraint
on `(course_id, learner_id)` — the DB enforces "no duplicates" and "not both enrolled and
waitlisted" (one row, one status). Queue order is `queued_seq` from a sequence, not timestamps
(ties, clock skew). Each `enroll`/`drop` is one transaction:

- pessimistic: `SELECT … FROM course WHERE id = ? FOR UPDATE`, count enrolled, insert;
  `drop` deletes and promotes `MIN(queued_seq)` in the same transaction;
- optimistic: version column / conditional update and retry on conflict — good when contention
  is low, poor for a hot "registration opens at 9:00" course, where a per-course lock or a
  single-writer queue (Kafka partition keyed by course ID) is better.

Idempotency for client retries needs an idempotency key, which today's state-level idempotency
does not provide (Q24).

**Likely follow-up:** "Isolation level?" — `READ COMMITTED` + row lock is enough; relying on
`SERIALIZABLE` alone means handling serialization failures with retries.

## 20. `EnrollmentService` reads the course on every call — why?

- **Area:** 1/4 · **Difficulty:** Medium
- **Relevant code:** `EnrollmentService.requireCourse`, constructor; DESIGN.md §4.

**Expected answer.** The `EnrollmentService` can be created before the course is configured
(`main` wires both first; the CLI configures later). Reading through `configuration.getCourse()`
each time gives one validated, normalized source of truth and makes the "no operation before
configuration" guard free: `getCourse()` throws `IllegalStateException`. Cost is a field read.
`operationsWorkOnceTheCourseIsConfiguredAndRejectedAttemptsLeftNoState` tests the late
configuration.

The trade-off: temporal coupling — the object is constructible in an unusable state. Alternative:
build `EnrollmentService` from a `Course` once it exists (a factory), which removes the guard but
moves the "not yet" state into the wiring/CLI. Under concurrency the `course` field is not
`volatile`, so another thread may not see it (Q31).

**Likely follow-up:** "Which do you prefer?" — for a long-lived multi-course service, the
catalog lookup (as now) generalizes better.

## 21. Is depending on concrete `CourseConfigurationService` a DIP violation?

- **Area:** 1 · **Difficulty:** Medium
- **Relevant code:** `EnrollmentService(CourseConfigurationService)`.

**Expected answer.** Strictly, yes: a high-level policy depends on a concrete class. In practice
it is the YAGNI choice recorded in DESIGN.md §4: one implementation, same package, no I/O, and
both are `final`. Tests use the real configuration service, which is simpler and more faithful
than a mock. What `EnrollmentService` actually needs is narrow — "give me the course or throw" —
so if a second source appeared I would extract a small `CourseProvider` interface
(`Course getCourse()`), which is ISP-friendly too, rather than mirror the whole configuration
API.

**Likely follow-up:** "Then why not `Supplier<Course>`?" — workable and lighter, but a named
interface documents the contract (throws when not configured).

## 22. SCENARIO — The admin can now change capacity

- **Area:** 7 · **Difficulty:** Hard
- **Relevant code:** `CourseConfigurationService.configure` (configure once), `Course` (immutable
  record), `enrollOrWaitlist`, `promoteFromWaitlist`.

**Scenario.** "Admins can raise or lower capacity on an open course."

**Expected answer.** `Course` is an immutable record and `configure` refuses a second call, so
add `changeCapacity(int)` that validates (≥ 1) and replaces the record. Then:

- **Increase by k:** promote up to k learners from the queue head **in the same atomic step**,
  otherwise the Q11 invariant breaks and newcomers overtake the queue. This means
  `EnrollmentService` must react — either the change goes through `EnrollmentService`, or the
  configuration service notifies it. The cleanest is a single entry point on the enrollment side
  that changes capacity and promotes.
- **Decrease below enrolled:** never evict silently; either reject, or allow and stop promoting
  until enrolled < capacity. `getAvailableSeats()` would go negative today — clamp it or define
  "over-capacity" explicitly.
- Tests: raise by 2 with 3 waiting → first two promoted in order; lower below enrolled → no one
  removed, no promotion on the next drop until under capacity.

**Likely follow-up:** "Where does the rule live?" — in the services, not the CLI (DESIGN.md §15).

## 23. Time complexity of each operation

- **Area:** 3 · **Difficulty:** Medium
- **Relevant code:** `enroll`, `drop`, `getLearnerStatus`, `getAvailableSeats`,
  `meetsPrerequisites`.

**Expected answer.** With *E* enrolled, *W* waiting, *P* prerequisites, *C* completed courses:

- `enroll`: O(W) for `waitlistedLearners.contains` + O(C + P) to normalize and `containsAll`.
- `drop`: enrolled learner O(1) (`HashSet.remove` + `poll`); waitlisted learner O(W)
  (`ArrayDeque.remove(Object)`).
- `getLearnerStatus`: O(W) worst case; `getAvailableSeats`: O(1).

`meetsPrerequisites` builds a new `HashSet` each call (`Ids.normalizeAll`) — fine for a handful of
courses. With a `LinkedHashSet` waitlist (Q3) everything except prerequisite checking becomes
O(1).

**Likely follow-up:** "`containsAll` direction?" — it iterates the prerequisites and looks each up
in the learner's normalized `HashSet`: O(P) after the O(C) build.

## 24. Idempotency: what kind do you actually provide?

- **Area:** 2 · **Difficulty:** Medium
- **Relevant code:** `enroll` (`ALREADY_*`), `drop` (`LEARNER_DETAIL_NOT_FOUND`); DESIGN.md §6, A8.

**Expected answer.** **State-level** idempotency: repeating `enroll` or `drop` causes no further
state change. The *response* differs — the first `enroll` returns `ENROLLED`, a retry returns
`ALREADY_ENROLLED`; the first `drop` returns `DROPPED`, a retry `LEARNER_DETAIL_NOT_FOUND`.
DESIGN.md states this distinction explicitly. A client that times out and retries cannot tell
"my request succeeded" from "someone else enrolled me". Response-level idempotency needs an
idempotency key and a stored response per key.

A subtle trap: a drop retried after the learner re-enrolled would drop the *new* registration.
An idempotency key, or a registration ID instead of a learner ID, prevents that.

**Likely follow-up:** "Is `ALREADY_ENROLLED` a success or a failure for the client?" — success
for the user's intent; that is why I would map it to 200, not 409.

## 25. Primitive obsession: `String` IDs vs value objects

- **Area:** 1/3 · **Difficulty:** Medium
- **Relevant code:** `Ids`, `Course.courseId`, `LearnerDetails.learnerId`.

**Expected answer.** IDs are raw `String`s and normalization is a convention enforced by calling
`Ids.normalize` at the right places — the Task 2 prerequisite bug is exactly this convention
being missed in one place. A `record LearnerId(String value)` whose compact constructor strips and
lower-cases would make un-normalized IDs unrepresentable and stop course and learner IDs being
mixed up. Trade-off: `LearnerDetails` must represent a blank ID to report
`MISSING_LEARNER_DETAILS` (A6/A12), so the value object would move "blank" detection to the
boundary (CLI/API parsing). For an assessment, a shared helper was the proportionate step (DRY,
Task 8); for production I would introduce `CourseId` and `LearnerId`.

**Likely follow-up:** "What would it change in the tests?" — case/whitespace tests move to the
value object; service tests get simpler.

## 26. Return the waitlist position from `enroll` (Java 25 types)

- **Area:** 3/7 · **Difficulty:** Medium
- **Relevant code:** `EnrollmentResult`, `enroll`, `enrollOrWaitlist`.

**Expected answer.** An enum cannot carry data. Model the outcome as a sealed hierarchy:

```java
sealed interface EnrollmentOutcome permits Enrolled, Waitlisted, Rejected {}
record Enrolled() implements EnrollmentOutcome {}
record Waitlisted(int position) implements EnrollmentOutcome {}
record Rejected(Reason reason) implements EnrollmentOutcome {}
```

Callers use an exhaustive pattern-matching `switch` (no `default`), so adding a new outcome
breaks compilation where it is not handled. Position = `waitlistedLearners.size()` right after
`add` (O(1)); for `ALREADY_WAITLISTED` you need an index scan (O(W)) or a sequence number.
The CLI currently prints enum names, so it would need a small formatting change.

**Likely follow-up:** "Would you do this now?" — only when a consumer needs the data (YAGNI).

## 27. Is the CLI really free of business rules?

- **Area:** 1 · **Difficulty:** Medium
- **Relevant code:** `EnrollmentCli.createCourse`, `courseReady`, `splitIds`, `run`.

**Expected answer.** Mostly. It converts input (`Integer.parseInt`, comma splitting) and prints
results; configure-once, validation and normalization are enforced by the services, and the CLI's
own "already created" message is a UX shortcut — `configure` would refuse anyway. Points an
interviewer can push on:

- `courseReady()` reads like a query but has a side effect (runs the *Create course* prompts).
  `ensureCourseOrCreate()` would be a more honest name.
- `splitIds` decides that a comma cannot appear in an ID — an input format rule, documented.
- `createCourse` catches `IllegalStateException`, which the `isConfigured()` guard makes
  unreachable through the menu — harmless, defensive.
- `main` wires concrete classes: fine for an application entry point (composition root).

**Likely follow-up:** "How would you unit-test it?" — inject a `Scanner` over a string and a
`PrintStream` over a `ByteArrayOutputStream`; the constructor already allows it.

## 28. Why did you reject the AI's tests for records?

- **Area:** 5/6 · **Difficulty:** Medium
- **Relevant code:** AI log Task 1; `Course`, `LearnerDetails`; CLAUDE.md test rule.

**Expected answer.** Tests for accessors, `equals` and `hashCode` of records and enum constants
test the compiler, not our behaviour, and they add maintenance cost and inflate the test count.
The one behaviour the records do have — the defensive unmodifiable copy — is observable through
the services. The rule "tests target observable service behaviour" is now in `CLAUDE.md` so the
AI does not regress to it.

**Likely follow-up:** "But the defensive copy *is* behaviour — is it tested?" — not directly.
A test that mutates the caller's set after `configure` and checks enrollment still uses the old
prerequisites would be worth adding.

## 29. How do you know the AI-written tests can fail?

- **Area:** 5/6 · **Difficulty:** Medium
- **Relevant code:** `EnrollmentServiceTest`; AI log Task 9.

**Expected answer.** A green suite proves little if assertions are weak or tautological. What I
did: reviewed each test's assertion against the rule it claims to test, and the Task 2 defect
showed that a missing case goes unnoticed until a reviewer names the rule. In Task 9 the new
no-prerequisite test **passed before any code change** — that is a characterization test that
documents behaviour, but I did not demonstrate it can fail. The systematic answer is mutation
testing (PIT): if flipping `<` to `<=` in `enrollOrWaitlist` or removing `promoteFromWaitlist()`
leaves tests green, a test is missing.

**Likely follow-up:** "Which mutant do you expect to survive?" — candidates: replacing
`containsAll` with a check that only the first prerequisite is met in a test that has only one
prerequisite (only `allPrerequisitesAreRequired` guards it), or the defensive `Set.copyOf` in the
records, which no test mutates the caller's set to observe. Without running PIT, I cannot say.

## 30. Immutability and defensive copies in this code

- **Area:** 3 · **Difficulty:** Medium
- **Relevant code:** `Course`/`LearnerDetails` compact constructors,
  `getAvailableCourseIds()`, `Ids.normalizeAll`.

**Expected answer.** Records are shallowly immutable; the `Set` components would leak mutability,
so the compact constructors replace them with `Set.copyOf` (unmodifiable, rejects nulls; returns
the same instance if already an unmodifiable copy). `getCourse()` can safely return the stored
`Course`. `getAvailableCourseIds()` returns `Set.of(...)`. The services' internal collections are
never exposed. `Ids.normalizeAll` returns a mutable `HashSet`, but it is package-private and its
result is either wrapped by `Course` or used locally.

Caveats: `Set.copyOf` iteration order is unspecified (only matters for display);
`LearnerDetails` deliberately keeps a `null` set as `null`.

**Likely follow-up:** "Why not `Collections.unmodifiableSet`?" — that is a *view*; the caller can
still mutate the original.

## 31. `configure` under concurrency and memory visibility

- **Area:** 4 · **Difficulty:** Hard
- **Relevant code:** `CourseConfigurationService.configure`, `course` field, `getCourse`.

**Expected answer.** `configure` is check-then-act on `course != null`: two threads can both pass
and the second silently overwrites the first — exactly the "configure twice loses state" defect
fixed in the CLI, reappearing under concurrency. And `course` is a plain field, so a thread
calling `EnrollmentService.enroll` may never see a course configured by another thread (no
happens-before). Fix: `AtomicReference<Course>` with
`compareAndSet(null, newCourse)` — atomic configure-once and safe publication in one step; throw
`IllegalStateException` if it returns `false`. Validation runs before the CAS, so a losing thread
still gets the right exception. Since `Course` is an immutable record with final fields, once
published it is safe to read without locks.

**Likely follow-up:** "Could `volatile` alone fix it?" — it fixes visibility, not the
check-then-act race.

## 32. Edge cases at the input boundary

- **Area:** 2 · **Difficulty:** Medium
- **Relevant code:** `isThisCourse`, `isValidLearner`, `configure`, `EnrollmentCli.createCourse`.

**Expected answer (what actually happens).**

- `enroll(null, …)` and `enroll("   ", …)` → `INVALID_COURSE_ID` (null guard in
  `isThisCourse`; blank normalizes to `""`).
- Completed set containing `"  "` → normalized to `""`, harmless.
- Prerequisites `{"Java", "JAVA"}` → merged into one (`mergesPrerequisitesThatDifferOnlyInCase`).
- A course listing itself as a prerequisite → accepted; no learner is eligible unless they claim
  to have completed it (DESIGN.md §15).
- Capacity `Integer.MAX_VALUE` → accepted; capacity `0`/negative → `IllegalArgumentException`.
- CLI capacity `"99999999999"` → `NumberFormatException` → "must be a whole number"
  (misleading).
- PowerShell 5.1 piping adds a BOM to the first line → first menu choice invalid; documented
  rather than hacked around (Task 6).

**Likely follow-up:** "Which would you fix first?" — the self-prerequisite, since it silently
makes a course unusable.

## 33. `drop` semantics: blank ID and waitlisted learners

- **Area:** 2 · **Difficulty:** Medium
- **Relevant code:** `EnrollmentService.drop`, `getLearnerStatus`.

**Expected answer.** `drop` tries the enrolled set first (remove + promote), then the queue
(remove, no promotion — no seat was freed, A10), else `LEARNER_DETAIL_NOT_FOUND`. A blank ID also
returns `LEARNER_DETAIL_NOT_FOUND`, while `getLearnerStatus` returns `MISSING_LEARNER_DETAILS` for
the same input — an inconsistency: "you sent nothing" and "no such learner" are conflated in
`drop`. Defensible for a two-value `DropResult` the design specified, but I would add
`MISSING_LEARNER_ID` if the API were public. Also, `drop` takes no course ID — fine for one
course, a breaking change for many (Q12).

**Likely follow-up:** "Should dropping a waitlisted learner be called DROPPED?" — arguably
`WAITLIST_CANCELLED` is clearer for the client.

## 34. Make eligibility rules extensible (OCP)

- **Area:** 1/7 · **Difficulty:** Medium
- **Relevant code:** `EnrollmentService.meetsPrerequisites`.

**Scenario.** New rules: minimum grade, "any 2 of these 3 courses", department restrictions.

**Expected answer.** Today eligibility is one private static method — right for one rule (A2:
all prerequisites). With several rules, extract an `EligibilityPolicy` (strategy) —
`EligibilityResult check(Course, LearnerProfile)` — composed with `and`/`or`, injected into
`EnrollmentService`. `INELIGIBLE` may then need a reason (Q26). Also, eligibility data must come
from a trusted source; completed courses are self-declared today (DESIGN.md §15).

**Likely follow-up:** "Why not do it now?" — YAGNI; one rule does not justify the abstraction.

## 35. Add a seat-offer window (accept within 24 h)

- **Area:** 7 · **Difficulty:** Hard
- **Relevant code:** `promoteFromWaitlist`, `EnrollmentResult`, state collections.

**Expected answer.** Promotion stops being instant: a freed seat becomes an `OFFERED` reservation
for the queue head with an expiry. State needs a third collection (or a status per registration):
offered seats count against capacity. On accept → `ENROLLED`; on decline/expiry → offer to the
next head. Time must be injected (`java.time.Clock`) for testability, and expiry needs a
scheduler or lazy evaluation on each call. The Q11 invariant becomes "waitlist non-empty ⇒
enrolled + offered = capacity". Concurrency: accept vs expiry race → same per-course lock or a
conditional DB update.

**Likely follow-up:** "How do you test expiry?" — a fixed/mutable `Clock`, no `sleep`.

## 36. Re-enrollment after drop goes to the back of the queue — fair?

- **Area:** 2 · **Difficulty:** Medium
- **Relevant code:** `drop`, `enrollOrWaitlist`; DESIGN.md §6 item 8, A9; tests
  `reEnrollmentAfterDropJoinsEndOfWaitlist`, `reEnrollmentAfterDropRechecksPrerequisites`.

**Expected answer.** Yes by design: a drop ends the registration; a new `enroll` is a new attempt
that re-checks prerequisites and, if the course is full, joins the end (A9). Preserving the old
position would let a learner drop and rejoin to game the queue, and would need history we do not
keep. It also keeps the model simple — status is derived from two collections, no history map
(A11).

**Likely follow-up:** "A learner drops by mistake — support undo?" — a short grace period with a
held seat, i.e. the offer-window mechanism from Q35.

## 37. Build choices: `--release 25`, `-Xlint:all -Werror`

- **Area:** 3 · **Difficulty:** Medium
- **Relevant code:** `build.gradle`.

**Expected answer.** `options.release = 25` compiles against the Java 25 platform API and
bytecode, which is stricter than `sourceCompatibility`/`targetCompatibility` (it also restricts
the API to that release). No toolchain block, so the JDK running Gradle must be 25 — documented
in the README. `-Xlint:all -Werror` makes every warning (raw types, unchecked, deprecation,
missing `serialVersionUID`) a build failure — cheap quality gate, especially for AI-generated
code. Groovy DSL, Gradle 9.9.1 wrapper, JUnit BOM 5.13.4.

Honest nit: the `java { sourceCompatibility … }` block is redundant with `release`.

**Likely follow-up:** "Which Java 25 features does the code use?" — records, text blocks (`MENU`),
arrow `switch`, `Set.copyOf`/`Set.of`, `strip`; no sealed types or pattern matching yet — Q26
is where they would earn their place.

## 38. What would you refactor next, given one more day?

- **Area:** 1 · **Difficulty:** Medium
- **Relevant code:** whole service package.

**Expected answer (ranked).**

1. Waitlist to `LinkedHashSet` (O(1), uniqueness by construction) — Q3.
2. Separate `RegistrationStatus` from `EnrollmentResult` — Q14.
3. Extract a `CourseRoster` (enrolled + queue) as the future per-course lock and multi-course
   unit — Q12.
4. Add the "waitlist non-empty ⇒ full" invariant to DESIGN.md §6 with a test — Q11.
5. JaCoCo + PIT, and a jqwik model-based test of the §6 invariants — Q16/Q17.
6. Rename `courseReady()` and add CLI I/O tests — Q27.

**Likely follow-up:** "Which is risky?" — 2 is a public-API change; the others are internal.

## 39. How did you keep the AI inside the design?

- **Area:** 6 · **Difficulty:** Medium
- **Relevant code:** `DESIGN.md`, `CLAUDE.md`, `CLAUDE-AI-LOG.md`.

**Expected answer.** `DESIGN.md` was the approved baseline (drafted with ChatGPT, decided by me);
`CLAUDE.md` tells Claude not to add features or resolve ambiguities on its own, to work phase by
phase, and to log every task. Evidence it worked: Claude asked for `DESIGN.md` when missing
instead of inventing one, flagged the `NOT_ENROLLED`/`NOT_REGISTERED` conflict and the
"ineligible head" case for me to decide, and reported defects in Task 5 *before* changing code.
Evidence it was needed: the BOM hack, the record tests and the CLI-only hint were all AI
proposals I rejected. Each task entry separates Claude's verification from mine; I did the final
independent verification myself from a clean clone.

**Likely follow-up:** "What would you do differently?" — ask for failing tests first for every
rule (test-first prompting), and add mutation testing so "tests pass" means more.

## 40. Add persistence without breaking the services

- **Area:** 7 · **Difficulty:** Hard
- **Relevant code:** `EnrollmentService` collections, `CourseConfigurationService.course`.

**Expected answer.** Introduce ports where the state lives: a `CourseRepository` and a
`RosterRepository` (or one `CourseRoster` aggregate repository) with an in-memory implementation
that keeps today's tests running, and a JDBC/JPA one. Rules stay in the services. The key design
point is the **transaction boundary**: load the aggregate, apply `enroll`/`drop`, save — inside
one transaction with the locking from Q19, otherwise the in-memory invariants do not carry over.
This is the moment the YAGNI "no interfaces" decision is revisited — there is now a second
implementation. Run the same behaviour tests against both implementations (abstract test class
or `@ParameterizedTest` over factories), plus Testcontainers for the real DB.

**Likely follow-up:** "Event sourcing instead?" — the domain is small and command-driven;
appending `Enrolled`/`Waitlisted`/`Dropped`/`Promoted` events gives an audit trail and
replayable queue order, at the cost of projections. Worth it only if audit is a requirement.
