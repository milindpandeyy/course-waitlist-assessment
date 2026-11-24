# Assessment Interview Preparation — Lead Java Developer

Twenty-five questions a Principal Java Engineer is likely to ask about the Course Waitlist
assessment, ranked by importance for a Lead Java Developer interview in financial services. Every
answer is grounded in the code on `assessment/enrollment-poc` (`2fe7710`). There are no DSA exercises.

Each question lists: **expected answer**, **likely follow-ups**, **relevant code**, and **what a
Lead Engineer should demonstrate** (the signal the interviewer is scoring).

| # | Topic | Theme |
| --- | --- | --- |
| 1 | Making the service safe under concurrent requests | Concurrency |
| 2 | Atomicity of compound invariants across two collections | Concurrency |
| 3 | From in-memory PoC to a production, DB-backed service | Production readiness |
| 4 | The prevention policy for ineligible learners | Design decisions |
| 5 | The unstated "waitlist non-empty ⇒ full" invariant | Invariants |
| 6 | Splitting configuration from enrollment; no interfaces | Architecture |
| 7 | Result enums vs exceptions | API design |
| 8 | `ArrayDeque` vs `LinkedHashSet` for the waitlist | Collections |
| 9 | `configure` races and the Java Memory Model | Java internals / concurrency |
| 10 | Idempotency and client retries | Production readiness |
| 11 | Test strategy and what "105 tests" really proves | Testing |
| 12 | Records, compact constructors and defensive copies | Java internals |
| 13 | ID normalization: `strip`, `Locale.ROOT`, storage-time canonical form | Java internals |
| 14 | Complexity of every operation | Complexity |
| 15 | `HashSet` and `ArrayDeque` internals that the code relies on | Java internals |
| 16 | Testing concurrency | Testing / concurrency |
| 17 | Capacity change as a new requirement | Change request |
| 18 | Multi-course support and the API break | Change request |
| 19 | Overloaded `EnrollmentResult` (commands vs status) | API design |
| 20 | Edge cases at the input boundary | Edge cases |
| 21 | Trusting self-declared completions | Production readiness / security |
| 22 | Is the CLI truly free of business rules? | Architecture |
| 23 | Verifying AI-generated code and tests | Engineering process |
| 24 | Java 25 features: used, unused and why | Java 25 |
| 25 | Observability, audit and operability | Production readiness |

---

## 1. How would you make `EnrollmentService` safe under concurrent requests?

**Relevant code:** `EnrollmentService.enroll`, `enrollOrWaitlist`, `drop`, `promoteFromWaitlist`;
DESIGN.md §14 (A4).

**Expected answer.**
- Today it is explicitly single-threaded (A4). State is a plain `HashSet<String>` and an
  `ArrayDeque<String>`, and every mutation is check-then-act: `enrollOrWaitlist` checks
  `enrolledLearners.size() < course.capacity()` and then calls `add`, and `drop` calls `remove`
  and then promotes. The CLI is a single-threaded loop, so the races cannot happen through the
  shipped entry point.
- The unit of atomicity is **one whole operation on one course**. The simplest correct fix is
  `synchronized` on all four public methods, so one monitor per service, which is one per course.
  Reads must be locked too: `getLearnerStatus` reads two collections and needs a consistent
  snapshot, and without a lock there is no happens-before edge.
- Alternatives: a `ReentrantReadWriteLock` (reads are cheap but contended writes dominate here,
  so the gain is small); a single-writer actor per course; or, in production, push atomicity
  to the database (Q3).
- On Java 25, `synchronized` no longer pins virtual threads (JEP 491, JDK 24), so a monitor is a
  reasonable choice even with virtual-thread request handling.

**Likely follow-ups.**
- Why not `ConcurrentHashMap.newKeySet()` and `ConcurrentLinkedDeque`? (→ Q2)
- What happens to throughput for a hot course at registration open?
- Where would you document the thread-safety contract (`@ThreadSafe`, Javadoc)?

**What a Lead should demonstrate:** identifying the *invariant* as the unit of atomicity, not the
data structure; choosing the simplest correct mechanism first; knowing the Java 25 virtual-thread
implication.

## 2. Why don't concurrent collections fix the race?

**Relevant code:** `enrollOrWaitlist`, `drop`; DESIGN.md §6 invariants 1–3.

**Expected answer.**
- Concurrent collections make each *call* atomic. The invariants span calls and collections:
  - capacity: `size()` and then `add`;
  - "not both enrolled and waitlisted";
  - FIFO promotion: `remove` on the set, then `poll` on the queue.
- Example with concurrent collections: capacity 30, 29 enrolled. Two threads both read size 29
  and both add, so 31 are enrolled and `getAvailableSeats()` returns −1.
- Drop and enroll interleaved: an enroll can run between `enrolledLearners.remove` and
  `promoteFromWaitlist` and take the seat ahead of the queue head. That is a FIFO violation, or
  capacity + 1.
- The same learner enrolling twice at once: both threads pass `waitlistedLearners.contains`, and
  `ArrayDeque` allows duplicates, so invariant 2 breaks.
- With the plain collections it is worse: a concurrent `HashSet.add` can lose updates, and
  `ArrayDeque` can be corrupted.

**Likely follow-ups.**
- Could you make it lock-free (CAS on an immutable state snapshot via `AtomicReference`)? What
  does that cost per operation?
- Which invariant breaks first under load?

**What a Lead should demonstrate:** precise reasoning about interleavings, not "use thread-safe
classes".

## 3. Take this PoC to production. What changes?

**Relevant code:** in-memory state in `EnrollmentService` and `CourseConfigurationService`;
DESIGN.md §14–§15.

**Expected answer.**
- **Persistence and atomicity.** Use a `course` table and a `registration` table:
  - `registration(course_id, learner_id, status, queue_seq)` with a unique constraint on
    `(course_id, learner_id)`;
  - the DB then enforces no duplicates and "one status per learner";
  - `queue_seq` comes from a sequence, not a timestamp (ties, clock skew).
- **Transactions.** `enroll`, and `drop` with its promotion, each run in one transaction:
  - pessimistic: `SELECT … FOR UPDATE` on the course row;
  - or a conditional update: `UPDATE … WHERE enrolled < capacity`, then check the rows affected.
  - Use optimistic versioning only if contention is low. A hot course at 9:00 favours a row lock
    or a per-course single writer, such as a Kafka partition keyed by course ID.
- **Horizontal scaling.** In-memory state per JVM cannot scale out without partitioning by
  course, so the DB or the partitioning becomes the source of truth.
- **API layer.** Add REST with validation and authentication. A learner may enroll only
  themselves, and their completions come from a trusted system (Q21).
- **Operational.** Add idempotency keys (Q10), audit events (Q25), metrics, and a transactional
  outbox for promotion notifications.
- **Code structure.** Add repository ports, and keep today's tests running against an in-memory
  adapter. This is when the YAGNI "no interfaces" decision is revisited.

**Likely follow-ups.**
- Which isolation level?
- How do you migrate the waitlist order if `queue_seq` has gaps?
- How do you keep the in-memory tests and the DB behaviour from diverging?

**What a Lead should demonstrate:** a transaction boundary that matches the invariant, and
trade-offs between locking styles under real traffic shapes.

## 4. Justify not re-checking prerequisites at promotion.

**Relevant code:** `EnrollmentService.promoteFromWaitlist`, `meetsPrerequisites`; DESIGN.md §8,
A1–A3.

**Expected answer.**
- The assessment asks for a policy for "an ineligible learner at the front of the waitlist". The
  policy chosen is **prevention**:
  - rule 5 rejects ineligible learners before they are stored anywhere (`INELIGIBLE`);
  - completion is permanent (A1);
  - the API cannot change a queued learner's completions.
- So the head of the queue is always eligible, and a re-check would be dead code.
- Claude flagged the case as open in Task 3, and the candidate closed it in Task 4.
- Re-checking is also *impossible* today: the queue stores only normalized IDs, not
  `LearnerDetails`.
- If completions or prerequisites could change, eligibility would need a source of truth, and
  promotion would loop: poll, re-check, apply a skip or remove policy, and still promote at most
  one learner per freed seat.

**Likely follow-ups.**
- Skip-and-keep or remove-and-notify, and why?
- What if every queued learner becomes ineligible?
- Does revocation also affect already-enrolled learners?

**What a Lead should demonstrate:** that a design decision rests on stated assumptions, and
knowing exactly which change invalidates it.

## 5. `enrollOrWaitlist` never checks whether the queue is empty. Why is FIFO still fair?

**Relevant code:** `enrollOrWaitlist`, `drop`, `promoteFromWaitlist`.

**Expected answer.**
- The fairness rests on an invariant that the code maintains but DESIGN.md §6 does not list:
  **waitlist non-empty ⇒ enrolled == capacity**.
- Three facts keep it true:
  - learners are queued only when the course is full;
  - dropping an enrolled learner promotes the queue head immediately;
  - dropping a waitlisted learner frees no seat.
- So whenever a seat is free the queue is empty, and a newcomer may take it.
- Any change that frees a seat without promoting breaks this silently: a capacity increase,
  asynchronous promotion, or an admin removal. Newcomers would then jump the queue.
- Fix: document the invariant and test it, or check `waitlistedLearners.isEmpty()` defensively
  in `enrollOrWaitlist`.

**Likely follow-ups.**
- Write the test that would catch the regression.
- Assertion or defensive check?

**What a Lead should demonstrate:** finding implicit invariants that code depends on, the kind
of thing that breaks in the next feature.

## 6. Why two services, and why no interfaces?

**Relevant code:** `CourseConfigurationService`, `EnrollmentService`; AI log Task 8.

**Expected answer.**
- Originally a single `CourseRegistrationService` mixed course creation with registration, and
  "configure once" existed only in the CLI, untested.
- The candidate proposed the split (Task 8), applying Separation of Concerns, DRY and YAGNI:
  - `CourseConfigurationService` owns validation, normalization and configure-once;
  - `EnrollmentService` owns enrollment, drop, status and seats, and reads the course from the
    configuration service on every call;
  - the shared `Ids` helper removed duplicated normalization code.
- No interfaces, because there is one implementation. Both classes are `final`, and tests use
  real objects (in memory, fast, no mocks).
- `EnrollmentService` depending on a concrete class is a DIP trade-off. It needs only
  `getCourse()`, so a narrow `CourseProvider` interface is the extraction point when a second
  source (a database) appears.

**Likely follow-ups.**
- Isn't that a DIP violation?
- When exactly do you introduce the interface?
- `Supplier<Course>` vs a named interface?

**What a Lead should demonstrate:** pragmatic SOLID, knowing when an abstraction pays and
being able to name the trigger for adding it.

## 7. Why do some failures throw and others return enums?

**Relevant code:** `CourseConfigurationService.configure` (throws), `EnrollmentService.enroll`
and `drop` (return values), `requireCourse`.

**Expected answer.**
- **Expected domain outcomes are return values the caller must handle:** full course,
  ineligible, duplicate request, unknown learner.
- **Misuse or a broken precondition throws:**
  - `IllegalStateException` for a second `configure`, or for any operation before a course
    exists;
  - `IllegalArgumentException` for invalid configuration;
  - `NullPointerException` for a null configuration service.
- The asymmetry: invalid *learner* input is returned (`MISSING_LEARNER_DETAILS`,
  `INVALID_COURSE_ID`) while invalid *course* input throws. That is defensible because learner
  requests are end-user traffic and configuration is an admin precondition, but it should be
  stated explicitly.

**Likely follow-ups.**
- How do these map to HTTP status codes?
- Checked exceptions?
- Would a `Result` type or sealed hierarchy be better?

**What a Lead should demonstrate:** a consistent, explainable error model across an API.

## 8. `ArrayDeque` or `LinkedHashSet` for the waitlist?

**Relevant code:** `EnrollmentService.waitlistedLearners`; `enroll` (`contains`), `drop`
(`remove`), `getLearnerStatus`; DESIGN.md §3, §15.

**Expected answer.**
- **`ArrayDeque`:**
  - expresses FIFO intent (`Queue`), with O(1) `add` and `poll`;
  - `contains` and `remove(Object)` are O(n);
  - duplicates are allowed, so uniqueness depends on the `ALREADY_WAITLISTED` check.
- **`LinkedHashSet`:**
  - keeps insertion order, with O(1) `contains` and `remove` and uniqueness by construction;
  - since Java 21 it is a `SequencedSet`, so promotion is `removeFirst()` (it throws when empty,
    and there is no `poll` variant);
  - it costs more memory per entry and states queue intent less clearly.
- The linear cost was an accepted limitation for a bounded exercise (DESIGN.md §15). For real
  queue sizes, switch.

**Likely follow-ups.**
- How would you return a waitlist position in O(log n)? (Use a sequence number with a
  `TreeMap`, or rank in the database.)
- Why not a `PriorityQueue`? (It is not stable for equal priorities without a tie-breaker.)

**What a Lead should demonstrate:** choosing structures by operation mix and invariants, and
knowing the newer `SequencedCollection` API.

## 9. Is `CourseConfigurationService.configure` safe if two admins call it at once?

**Relevant code:** `CourseConfigurationService.configure`, `course` field, `getCourse`,
`isConfigured`.

**Expected answer.**
- No. `if (course != null) throw` followed by `course = …` is check-then-act, so both callers can
  pass. The second silently overwrites the first, which is the CLI defect from Task 6 reappearing
  under concurrency.
- `course` is a plain field, so there is no happens-before edge: an `EnrollmentService` on
  another thread may never see the configured course, or (in theory) may see a stale reference.
- Fix: `AtomicReference<Course>` with `compareAndSet(null, newCourse)`. That gives atomic
  configure-once and safe publication in one step.
  - Validate first, then CAS; a losing thread throws `IllegalStateException`.
  - `Course` is an immutable record with final fields, and its set is an unmodifiable copy, so
    it is safe to read without locks once it is published.
- `volatile` alone fixes visibility but not the race.

**Likely follow-ups.**
- What do final-field semantics guarantee for records?
- Why is `Set.copyOf` important for safe publication here?

**What a Lead should demonstrate:** a working knowledge of the Java Memory Model
(happens-before, safe publication), not just "add synchronized".

## 10. A client times out and retries `enroll`. What happens?

**Relevant code:** `enroll` (the `ALREADY_*` checks), `drop`; DESIGN.md §6, A8.

**Expected answer.**
- The design gives **state-level** idempotency: a repeated request changes nothing.
- The response differs:
  - `ENROLLED` first, then `ALREADY_ENROLLED`;
  - `DROPPED` first, then `LEARNER_DETAIL_NOT_FOUND`.
- So a retrying client cannot tell "my call succeeded" from "someone else enrolled me".
- Dangerous case: a retried `drop` that arrives after the learner has re-enrolled drops the
  *new* registration.
- Production fix: an idempotency key stored with the response, scoped to the learner, with a
  TTL. Or address a registration ID instead of a learner ID.

**Likely follow-ups.**
- Is `ALREADY_ENROLLED` a success or a failure? (A success for the user's intent, so a 200
  response.)
- Where do you store the keys, and for how long?

**What a Lead should demonstrate:** distinguishing state idempotency from response idempotency;
a payments-grade mindset about retries.

## 11. What does "105 passing tests" actually prove?

**Relevant code:** `EnrollmentServiceTest` (`@Nested` groups), `CourseConfigurationServiceTest`,
`build.gradle`.

**Expected answer.**
- **What the suite covers:**
  - 105 JUnit Jupiter tests, counting each parameterized invocation, in two classes;
  - they cover every scenario the assessment lists (mapped in DESIGN.md §9);
  - they test behaviour through the public API only. For example, "not stored" is shown by a
    later `enroll` returning a fresh result instead of `ALREADY_*`.
- **What it doesn't prove:**
  - no coverage is measured (JaCoCo is not configured);
  - there is no mutation testing, so assertion strength is unknown;
  - there are no concurrency tests and no CLI tests;
  - one test (Task 9) passed before any code change; it is a characterization test that was
    never shown to fail.
- **Missing scenarios:**
  - a null element in the completed-course set (`NullPointerException` from `Set.copyOf` in
    `LearnerDetails`);
  - a self-prerequisite;
  - CLI capacity overflow (`"99999999999"` prints a misleading "whole number" message);
  - a property-based test of the §6 invariants over random sequences, replacing the single
    hand-picked `invariantsHoldAfterMixedOperations` run.

**Likely follow-ups.**
- Which mutant would survive?
- How would you test the CLI? (The constructor already takes a `Scanner` and a `PrintStream`.)

**What a Lead should demonstrate:** honesty about what tests do and do not prove; a concrete
plan with JaCoCo, PIT and jqwik.

## 12. Records here: what do the compact constructors do, and why no validation?

**Relevant code:** `Course`, `LearnerDetails`; `CourseConfigurationService.configure`;
A12, A15.

**Expected answer.**
- Records are shallowly immutable. A `Set` component would leak mutability, so each compact
  constructor reassigns the parameter to `Set.copyOf(...)`. The copy is unmodifiable, rejects
  null elements, and avoids copying an input that is already an unmodifiable set.
- `LearnerDetails` keeps a null set as null on purpose: missing input has to reach the service so
  it can return `MISSING_LEARNER_DETAILS` (A6, A12).
- `Course` does not validate (A15), so validation and normalization stay in one place
  (`configure`).
- The weakness: `new Course("x", -5, Set.of())` is constructible. That is safe only because
  `EnrollmentService` never accepts a `Course` from outside.
- Generated `equals`, `hashCode` and `toString` are produced via `ObjectMethods` bootstrap
  (invokedynamic); `equals` compares all components.

**Likely follow-ups.**
- `Collections.unmodifiableSet` vs `Set.copyOf`?
- Would you validate in the record if `Course` became an API input?
- Are records good JPA entities? (No: no no-arg constructor and no mutability. Use them as DTOs
  or value objects.)

**What a Lead should demonstrate:** knowing where invariants belong, and the cost of making
illegal states representable.

## 13. Explain `Ids.normalize` line by line, and its limits.

**Relevant code:** `Ids.normalize`, `Ids.normalizeAll`, `Ids.isBlank`; DESIGN.md §5.

**Expected answer.**
- `strip()` removes Unicode whitespace (`Character.isWhitespace`). `trim()` removes only
  characters up to U+0020. Neither removes a no-break space (U+00A0).
- `toLowerCase(Locale.ROOT)` avoids the Turkish dotted/dotless-I problem with the default
  locale.
- IDs are stored in canonical form at the boundary, so lookups are plain `HashSet.contains`
  rather than `equalsIgnoreCase` everywhere. The Task 2 defect (prerequisites compared
  case-sensitively) shows the cost of applying this by convention.
- Limits:
  - no full case folding (`ß` vs `SS`);
  - no Unicode normalization (NFC vs NFD);
  - inner whitespace is significant;
  - no length limit.
- Better long-term: `CourseId` and `LearnerId` value records that normalize in their compact
  constructors, so a raw ID cannot be used by mistake.

**Likely follow-ups.**
- Why didn't `LearnerDetails` normalize its ID? (A blank ID must stay representable.)
- What breaks if normalization changes after IDs are persisted? (Existing keys; a migration is
  needed.)

**What a Lead should demonstrate:** internationalization awareness and canonicalization at the
boundary.

## 14. Give the time and space complexity of each operation.

**Relevant code:** `enroll`, `drop`, `getLearnerStatus`, `getAvailableSeats`,
`meetsPrerequisites`.

**Expected answer.**

Notation: W waitlisted, P prerequisites, C completed courses.

| Operation | Time |
| --- | --- |
| `enroll` | O(W) for `waitlistedLearners.contains`, plus O(C + P) to build the normalized set and run `containsAll` |
| `drop`, enrolled learner | O(1): `HashSet.remove` and `ArrayDeque.poll` |
| `drop`, waitlisted learner | O(W): `remove(Object)` |
| `getLearnerStatus` | O(W) worst case |
| `getAvailableSeats` | O(1) |

- Space is O(E + W) IDs. `meetsPrerequisites` allocates a new `HashSet` on every call.
- With a `LinkedHashSet` waitlist, everything except the prerequisite check becomes O(1).

**Likely follow-ups.**
- Which way does `containsAll` iterate? (Over the prerequisites, looking each up in the
  learner's hash set.)
- What does the allocation in `meetsPrerequisites` cost at scale?

**What a Lead should demonstrate:** precise, code-level complexity, and knowing which parts
matter at production volumes.

## 15. Which `HashSet` and `ArrayDeque` internals does this code rely on?

**Relevant code:** `promoteFromWaitlist` (`poll()` null check), `enrolledLearners`,
`waitlistedLearners`.

**Expected answer.**
- `ArrayDeque` **rejects null elements**, so `poll()` returning `null` unambiguously means
  "empty". `promoteFromWaitlist` depends on this. With a `LinkedList`, which allows nulls, the
  check would be ambiguous.
- `ArrayDeque` is a resizable circular array. `poll` is O(1); `remove(Object)` shifts elements,
  which is O(n).
- `HashSet` is backed by a `HashMap` (values are a dummy `PRESENT`). It relies on
  `String.hashCode`, which is cached in the `String` object. Bins become trees once they pass 8
  entries (with a large table), so the worst case is O(log n). Its iterators are fail-fast
  (`ConcurrentModificationException` only on a best-effort basis), which is not a concurrency
  guarantee.
- `Set.copyOf` and `Set.of` return `ImmutableCollections` sets whose iteration order is
  randomized per JVM run (SALT). Prerequisite order in output is therefore not stable, which
  matters for display or snapshot tests.

**Likely follow-ups.**
- Why can an unsynchronized `HashMap` resize lose entries?
- Why `ArrayDeque` over `LinkedList` in general? (Locality and fewer allocations.)

**What a Lead should demonstrate:** knowing the internals that correctness depends on, not
trivia.

## 16. How would you test the concurrent version?

**Relevant code:** `EnrollmentService` after locking is added.

**Expected answer.**
- First, write a test that **fails today**:
  - capacity N, with many virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`)
    released together by a `CountDownLatch`, each enrolling a distinct learner;
  - assert exactly N `ENROLLED` results, the rest `WAITLISTED`, `getAvailableSeats() == 0`, and
    one status per learner.
- Add mixed enroll and drop runs, and check the §6 invariants at the end.
- Use `@RepeatedTest`, because races are probabilistic.
- For rigour, use jcstress for small interleavings, or a linearizability checker (Lincheck).
- A passing stress test is evidence, not proof. The locking argument must still be made by
  reasoning.

**Likely follow-ups.**
- Why might the test pass on your laptop and fail in CI?
- How do you keep it from being flaky?

**What a Lead should demonstrate:** test-first concurrency work, and humility about
nondeterminism.

## 17. New requirement: admins can change capacity on an open course.

**Relevant code:** `CourseConfigurationService.configure` (configure once), `Course` (immutable),
`enrollOrWaitlist`, `promoteFromWaitlist`, `getAvailableSeats`.

**Expected answer.**
- Add a validated `changeCapacity` that replaces the immutable `Course`.
- **Increase by k:** in the same atomic step, promote up to k learners from the head of the
  queue. Otherwise the Q5 invariant breaks and newcomers overtake the queue. This means the
  change must go through, or notify, `EnrollmentService`. Configuration alone cannot do it.
- **Decrease below the enrolled count:** never evict silently. Either reject the change, or
  accept it and stop promoting until the course is below capacity. `getAvailableSeats()` would
  go negative today, so clamp it or define "over capacity" explicitly.
- Tests:
  - raise by 2 with 3 waiting: the first two are promoted in order;
  - lower below enrolled: no one is removed, and the next drop promotes no one.

**Likely follow-ups.**
- Should capacity changes be audited?
- What if an increase and a drop arrive concurrently?

**What a Lead should demonstrate:** tracing a requirement change through every invariant it
touches.

## 18. New requirement: many courses.

**Relevant code:** `CourseConfigurationService.getAvailableCourseIds`, `EnrollmentService`
(`drop(String)`, `getLearnerStatus(String)`, `getAvailableSeats()`).

**Expected answer.**
- The configuration service becomes a catalog: `Map<String, Course>`, configure-once per ID.
- `getAvailableCourseIds()` already returns a set and the CLI only displays it. This is why the
  candidate rejected a CLI-only hint in Task 9.
- `EnrollmentService` holds `Map<String, CourseRoster>`, where `CourseRoster` holds the enrolled
  set and the queue. The roster is also the natural per-course lock.
- **API break:** `drop`, `getLearnerStatus` and `getAvailableSeats` take no course ID today and
  would need one.
- New rules to decide:
  - can a learner be waitlisted on several courses at once?
  - can prerequisites be validated against the catalog? (Today a course can list itself as a
    prerequisite.)

**Likely follow-ups.**
- How do you version the API change?
- Lock per course or a global lock? What about cross-course rules?

**What a Lead should demonstrate:** spotting API breaks early, and designing the aggregate
boundary.

## 19. `EnrollmentResult` serves both `enroll` and `getLearnerStatus`. Problem?

**Relevant code:** `EnrollmentResult`, `getLearnerStatus`; AI log Task 1.

**Expected answer.**
- Status returns only 4 of the 8 values. `NOT_ENROLLED` is never an `enroll` result, and
  `ALREADY_*` and `INELIGIBLE` are never a status.
- An exhaustive `switch` therefore handles impossible cases, and the type mixes "outcome of a
  command" with "state of a learner".
- It was a deliberate minimal choice from the design: the candidate resolved the `NOT_ENROLLED`
  vs `NOT_REGISTERED` conflict that Claude raised.
- Cleaner: a separate `RegistrationStatus { ENROLLED, WAITLISTED, NOT_ENROLLED }`, with a blank
  ID rejected at the boundary.
- A related inconsistency: a blank ID gives `LEARNER_DETAIL_NOT_FOUND` from `drop` but
  `MISSING_LEARNER_DETAILS` from status lookup.

**Likely follow-ups.**
- Is this a breaking change? When would you make it?
- Could a sealed interface replace both enums?

**What a Lead should demonstrate:** type design that makes impossible states unrepresentable,
and timing breaking changes before release.

## 20. Walk through the edge cases at the input boundary.

**Relevant code:** `isThisCourse`, `isValidLearner`, `configure`, `EnrollmentCli.createCourse`,
`splitIds`.

**Expected answer.**

| Input | Result |
| --- | --- |
| `enroll(null, …)` or `enroll("   ", …)` | `INVALID_COURSE_ID`. The course ID is checked before learner details. |
| Prerequisites `{"Java", "JAVA"}` | Merged into one. |
| A completed course `"  "` | Normalizes to `""`; harmless. |
| A course that lists itself as a prerequisite | Accepted, and no learner can enroll unless they claim to have completed it. Silent; DESIGN.md §15. |
| Capacity `Integer.MAX_VALUE` | Accepted. |
| Capacity ≤ 0 | `IllegalArgumentException`. |
| CLI capacity `"99999999999"` | `NumberFormatException`, reported as "must be a whole number", which is misleading. |
| An ID containing a comma | Cannot be entered through the CLI. |
| Input piped from PowerShell 5.1 | A byte-order mark breaks the first menu choice. This is documented; the candidate rejected a hack (Task 6). |

**Likely follow-ups.**
- Which edge case is the highest production risk? (The silent self-prerequisite.)
- Where does semantic validation belong? (In the services, never the CLI; DESIGN.md §15.)

**What a Lead should demonstrate:** systematic boundary thinking, and prioritising by risk.

## 21. The service trusts the learner's completed courses. Acceptable?

**Relevant code:** `LearnerDetails.completedCourseIds`, `meetsPrerequisites`; DESIGN.md §15
("Completed courses are self-declared").

**Expected answer.**
- It's acceptable for the assessment, and documented. In production it is a security flaw: any
  caller can claim any completion.
- Fix: eligibility comes from an authoritative system (a transcript service) by learner ID.
  `enroll` then takes only the learner identity, ideally taken from the authenticated principal,
  not from a request field.
- That also enables re-checking at promotion (Q4).
- Learner IDs may be personal data, so be careful with logging, encryption at rest and
  retention.

**Likely follow-ups.**
- What if the transcript service is down at promotion time? (Fail closed and retry, keeping the
  seat held.)
- How do you prevent one learner from enrolling another?

**What a Lead should demonstrate:** a trust-boundary and security mindset, which is expected in
a regulated firm.

## 22. Is the CLI really free of business rules?

**Relevant code:** `EnrollmentCli.createCourse`, `courseReady`, `splitIds`, `main`; DESIGN.md §13.

**Expected answer.**
- Largely, yes. It converts input and prints enum names. Configure-once, validation and
  normalization are enforced in the services. The CLI's "already created" message is UX only;
  `configure` would refuse anyway.
- Points worth criticising:
  - `courseReady()` reads like a query but runs the create prompts, a side effect. A name such
    as `ensureCourseOrCreate` would be more honest.
  - `splitIds` defines an input format rule: no commas in IDs.
  - `createCourse` catches `IllegalStateException`, which the guard makes unreachable.
- `main` is the composition root.
- It is testable today through the injected `Scanner` and `PrintStream`.

**Likely follow-ups.**
- Write a CLI test.
- Would you put a REST controller in front of the same services unchanged?

**What a Lead should demonstrate:** a clean boundary between adapter and domain, and honest
self-review.

## 23. How did you verify AI-generated code and tests?

**Relevant code:** `CLAUDE.md`, `DESIGN.md`, `CLAUDE-AI-LOG.md`.

**Expected answer.**
- `DESIGN.md` was the approved baseline. `CLAUDE.md` forbids adding features or resolving
  ambiguities on the AI's own, and requires working phase by phase and logging every task.
- Defects caught by review:
  - prerequisites compared case-sensitively (Task 2);
  - recreating the course wiped its registrations, and enrolling before a course existed failed
    silently (Task 6). Claude's own scripted runs had missed both.
- AI proposals rejected:
  - the invisible byte-order-mark hack (not platform-agnostic);
  - tests of records and POJOs (no behaviour to test);
  - a CLI-only course hint (against single responsibility).
- The AI was useful too: Claude flagged the `NOT_ENROLLED` conflict and the open "ineligible
  head" case, and it reported the missing `Course` validation *before* changing code.
- The candidate did the final verification independently, from a clean clone.
- Gap: AI tests were reviewed by reading them, not proven by mutation testing.

**Likely follow-ups.**
- How would you set AI-usage standards for your team?
- What should never be delegated to AI?

**What a Lead should demonstrate:** ownership of the result, a review discipline, and the
ability to scale that practice to a team.

## 24. Which Java 25 features do you use, which don't you, and why?

**Relevant code:** `Course`, `LearnerDetails` (records), `EnrollmentCli.MENU` (text block),
`run` (arrow `switch`), `build.gradle` (`options.release = 25`).

**Expected answer.**
- **Used:** records; text blocks; arrow `switch`; `Set.of` and `Set.copyOf`; `String.strip`.
- **Build:** `--release 25` with `-Xlint:all -Werror`, so every warning fails the build. That is
  a cheap gate on AI-generated code. The `sourceCompatibility` block is redundant with
  `release`.
- **Not used yet, and where they would fit:**
  - sealed interfaces and pattern-matching `switch` for richer results, such as
    `Waitlisted(int position)` with exhaustive handling and no `default`;
  - `SequencedSet.removeFirst()` (Q8);
  - virtual threads with a monitor that no longer pins them (Q1).
- None of these were needed for the bounded scope (YAGNI).

**Likely follow-ups.**
- Show the sealed-hierarchy version of `enroll`.
- What breaks at compile time when you add a new permitted subtype?

**What a Lead should demonstrate:** current Java knowledge applied with judgement, not feature
tourism.

## 25. What would you add for observability, audit and operations?

**Relevant code:** all services (no logging, metrics or events today).

**Expected answer.**
- **Audit:** an immutable event for every state change: `Enrolled`, `Waitlisted`, `Dropped`,
  `Promoted`, `CourseConfigured`. Record who, when, the before and after state, and a request
  ID. A financial firm will expect this for any allocation of a scarce resource. Publish events
  via an outbox in the same transaction as the change.
- **Metrics:** enrollments, waitlist length per course, promotion latency, rejected requests by
  reason, and lock wait time.
- **Logging:** structured, with correlation IDs, and no personal data in clear text.
- **Operations:** health checks; feature flags for policy changes (such as skip vs remove on
  promotion); runbooks for "course over capacity" and "stuck waitlist".
- **Today:** none of this exists. Results are only printed as enum names by the CLI.

**Likely follow-ups.**
- Event sourcing or state with an audit log?
- How would you prove after the fact that FIFO was honoured?

**What a Lead should demonstrate:** thinking beyond correctness to how the system is run,
audited and supported.

---

## Quick answers (spoken form)

Short answers to rehearse, written after the mock interview.

### How do you make the application thread-safe?

> "Every operation is check-then-act across two collections, so the whole operation has to be
> atomic, not each collection call. In one JVM I'd make `enroll`, `drop`, `getLearnerStatus` and
> `getAvailableSeats` `synchronized`, giving one lock per course, with reads locked too for
> visibility. For `configure` I'd use `AtomicReference.compareAndSet(null, course)`. Concurrent
> collections alone don't help. With several instances, JVM locks are useless, so the atomicity
> moves to the database: one transaction per operation, locking the course row with
> `SELECT … FOR UPDATE`, plus a unique constraint on course and learner."

### How do you defend the `ArrayDeque` decision?

Phrase it as a scope decision:

> "It was a deliberate trade-off for a bounded exercise. `ArrayDeque` states FIFO intent clearly,
> and add and poll are O(1). The cost is O(n) `contains` and `remove`, which doesn't matter at
> assessment scale. I documented it as a limitation in the design. For real queue sizes I'd
> switch to `LinkedHashSet`, which gives O(1) lookup, keeps arrival order, and makes duplicates
> impossible."

This matches DESIGN.md §3 and §15, and shows the cost was known when the choice was made.

### With a database, do the Set and Queue stay?

No. They stop being the source of truth, and tables replace them:

| Today (in memory) | With a database |
| --- | --- |
| `Course` record | `course` table: `course_id` (PK), `capacity` |
| prerequisites `Set` | `course_prerequisite` table: (`course_id`, `prerequisite_id`) |
| `LearnerDetails` completed courses | `learner` table, plus `learner_completion` (`learner_id`, `course_id`), ideally owned by a transcript system |
| `HashSet` enrolled | `registration` rows with `status = 'ENROLLED'` |
| `ArrayDeque` waitlist | `registration` rows with `status = 'WAITLISTED'`, ordered by an integer `queue_seq` |

- **The Set's job (no duplicates, fast lookup)** is done by the primary key on
  (`course_id`, `learner_id`).
- **The Queue's job (FIFO order)** is done by `ORDER BY queue_seq`, with an index so the head
  is found instantly.
- **In Java**, the service loads only what one request needs (one course, one registration, the
  queue head) inside a transaction, typically as entities or records from a repository. It no
  longer keeps all learners in memory. A cache could still exist for speed, but the database is
  the truth.
