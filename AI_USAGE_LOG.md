# AI usage log

This log records where an AI assistant was used to produce this repository, so a reviewer
can tell what was generated and what was checked.

| Date | Tool | Task | What the AI produced | How it was verified |
| --- | --- | --- | --- | --- |
| 2026-11-22 | Claude (Claude Code, desktop app) | Scaffold the project | `settings.gradle.kts`, `build.gradle.kts` (Java 21 via `--release`, JUnit 5 BOM), Gradle 9.9.1 wrapper, `.gitignore`, `.gitattributes` | `gradlew build` run locally on Windows with JDK 25 |
| 2026-11-22 | Claude | Domain model with no spec provided | `Course`, `StudentId`, `EnrollmentResult`, `DropResult`, `EnrollmentStatus`; assumptions listed in DESIGN.md | Compiles with `-Xlint:all -Werror` |
| 2026-11-22 | Claude | Unit tests | `CourseTest`, `StudentIdTest` (24 test cases incl. a concurrency test) | All 24 pass locally |
| 2026-11-22 | Claude | Documentation | README.md, DESIGN.md, this file | Human review pending |

## Notes

- The first build failed because `SequencedSet` has no `pollFirst()`; the AI fixed it to use
  `removeFirst()` guarded by `isEmpty()`, and the rebuild passed.
- The requirements were not specified; the domain rules are the AI's proposal and are marked
  **[ASSUMPTION]** in DESIGN.md. They have not yet been confirmed by a human.
- Add a row for each further AI-assisted change, including prompts or intent, and how the
  output was checked.
