# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Source of truth

`DESIGN.md` is the approved design baseline. Do not add features, change decisions or resolve
its ambiguities on your own: report conflicts and ask before deviating. Implementation goes
in the phases of DESIGN.md section 12; do not combine phases unless the candidate asks.

Every piece of AI work gets a factual entry in `CLAUDE-AI-LOG.md` (prompt, what was produced,
deviations, real command output). Never invent results; mark unchecked items
**Pending verification**.

## Commands (Windows / PowerShell)

- Build + all tests: `.\gradlew.bat build`
- One test class: `.\gradlew.bat test --tests com.example.waitlist.service.CourseRegistrationServiceTest`
- Tests target observable service behaviour; do not add tests for records, enums or POJOs.
- Test report: `build/reports/tests/test/index.html`
- Interactive CLI: `.\gradlew.bat run -q --console=plain`

## Build setup

- JDK 25 targeted with `options.release = 25` (no Gradle toolchain block); Gradle 9.9.1 wrapper.
- `-Xlint:all -Werror`: any compiler warning fails the build.
- Groovy DSL only (`build.gradle`, `settings.gradle`); no `.kts` files.
- Regenerating the wrapper here needs `gradle wrapper --no-validate-url`.

## Code layout

- `com.example.waitlist.model` — records `Course`, `LearnerDetails`; enums `EnrollmentResult`
  (also the status-lookup result; `NOT_ENROLLED` means neither enrolled nor waitlisted) and `DropResult`.
- `com.example.waitlist.service` — `CourseRegistrationService`, which owns the in-memory
  state (a `HashSet` of enrolled IDs and an `ArrayDeque` FIFO waitlist). No status map,
  repository or interface split; single-threaded, no locks.
- `com.example.waitlist.cli` — `WaitlistCli` (Gradle `application` main class): Scanner menu,
  input/output only; every rule stays in the service (DESIGN.md §13).

## Git

- Work on `feature/waitlist-poc` (tracks `origin/feature/waitlist-poc`); `main` is the base.
- Ask before pushing, opening PRs or creating remote repositories.
