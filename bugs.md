# Known bugs and design weaknesses

A short list to raise in the interview. For each: what happens, and the fix in one line.
Pick 2–3 and mention them before the interviewer finds them.

## Code bugs (the code does something wrong today)

| # | Bug | What happens | Fix |
| --- | --- | --- | --- |
| 1 | **Self-prerequisite accepted** | `configure("CS201", 30, {"CS201"})` succeeds; every learner then gets `INELIGIBLE`, silently | reject it in `configure` |
| 2 | **Not thread-safe** | concurrent enrolls exceed capacity; a drop racing an enroll lets a newcomer skip the queue; duplicate waitlist entries | `synchronized` per course |
| 3 | **`configure` race and visibility** | two admins both succeed and the second overwrites the first; other threads may not see the course | `AtomicReference.compareAndSet(null, course)` |
| 4 | **`null` inside the completed-courses set** | `LearnerDetails` throws `NullPointerException` (`Set.copyOf`) instead of the service returning `MISSING_LEARNER_DETAILS` | handle nulls before the copy |
| 5 | **Blank ID handled inconsistently** | `drop("  ")` → `LEARNER_DETAIL_NOT_FOUND`, but `getLearnerStatus("  ")` → `MISSING_LEARNER_DETAILS` | add `MISSING_LEARNER_ID` to `DropResult` |
| 6 | **Stale retried drop** | a retried drop that arrives after the learner re-enrolled removes the new registration | idempotency key, or drop by registration ID |
| 7 | **CLI capacity overflow** | `99999999999` prints "Capacity must be a whole number", which is misleading | catch the overflow and print "too large" |
| 8 | **`courseReady()` has a side effect** | its name sounds like a check, but it runs the *Create course* prompts | rename it to `ensureCourseOrCreate()` |

## Design weaknesses (accepted, but own them)

| # | Weakness | One-line answer |
| --- | --- | --- |
| 1 | **Self-declared completions** | anyone can claim a prerequisite; in production, look them up in the transcript system |
| 2 | **Hidden invariant** "waitlist non-empty ⇒ course full" | not documented or tested; a capacity increase or async promotion would let newcomers skip the queue |
| 3 | **Linear waitlist** (`ArrayDeque`) | O(n) `contains`/`remove`, and uniqueness depends on a check, not the structure; use `LinkedHashSet` |
| 4 | **Queue stores only IDs** | eligibility can't be re-checked at promotion if the rules change |
| 5 | **`EnrollmentResult` reused for status** | status returns only 4 of the 8 values; a separate `RegistrationStatus` would be cleaner |
| 6 | **Single-course API** | `drop`, status and seats take no course ID, so multi-course support is a breaking change |
| 7 | **`Course` doesn't validate itself** | `new Course("x", -5, …)` is constructible; safe only because enrollment gets courses from `configure` |
| 8 | **State-level idempotency only** | retries change nothing, but the client can't tell its own success from someone else's |
| 9 | **No persistence, no audit trail** | state is lost on exit, and a complaint couldn't be proven |
| 10 | **No semantic validation** | no ID length or format limits, no cap on capacity or list sizes (DESIGN.md §15) |
| 11 | **Case-insensitivity limits** | `Locale.ROOT` lower-casing doesn't fold `ß`/`SS` or normalize accented characters |
| 12 | **Test gaps** | no coverage measurement, no mutation testing, no CLI or concurrency tests, invariants checked on one fixed sequence |

**Start with:** bugs 1 and 2, and weaknesses 1 and 2. They show the most judgement.
