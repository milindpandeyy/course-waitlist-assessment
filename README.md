# Course Waitlist

Enrollment, drop and FIFO waitlist promotion for a single course offering: a plain Java service
with in-memory state and an interactive console CLI. See `DESIGN.md` for the design.

## Prerequisites

- JDK 25 on the `PATH` (or `JAVA_HOME` set to it).
- No Gradle install is needed; the Gradle 9.9.1 wrapper is included.

## Build and test

```sh
./gradlew build          # compile (warnings fail the build) and run all tests
./gradlew test --tests com.example.waitlist.service.CourseRegistrationServiceTest
```

On Windows use `.\gradlew.bat` instead of `./gradlew`. The test report is written to
`build/reports/tests/test/index.html`.

## Run the CLI

```sh
./gradlew run -q --console=plain
```

`-q --console=plain` keeps Gradle's progress output out of the menu; plain `./gradlew run`
also works.

```text
=== Course Waitlist ===
1. Create course
2. Enroll learner
3. Drop learner
4. Check status
5. Available seats
6. Exit
Choose an option (1-6):
```

Create a course first (option 1); the other options need one. IDs are case-insensitive and
surrounding spaces are ignored. Lists such as prerequisites and completed courses are entered
comma-separated, or left blank for none.

Example session (capacity 1, prerequisite `CS101`):

| Option | Input | Output |
| --- | --- | --- |
| 1 | `CS201`, `1`, `CS101` | `Course created.` |
| 2 | `CS201`, `alice`, `cs101` | `Result: ENROLLED` |
| 2 | `CS201`, `bob`, `CS101` | `Result: WAITLISTED` |
| 2 | `CS201`, `carol`, *(blank)* | `Result: INELIGIBLE` |
| 3 | `Alice` | `Result: DROPPED` (bob is promoted) |
| 4 | `BOB` | `Status: ENROLLED` |
| 5 | — | `Available seats: 0` |
| 6 | — | `Exit.` |

Only one course can be created per run; choosing option 1 again is refused. State is in memory
only and is lost when the CLI exits.
