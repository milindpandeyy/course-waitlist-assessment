# Course Waitlist

Enrollment, drop and FIFO waitlist promotion for a single course offering. It is a plain Java
service with in-memory state plus an interactive console CLI. The design is in
[`DESIGN.md`](DESIGN.md), and the record of AI assistance is in
[`CLAUDE-AI-LOG.md`](CLAUDE-AI-LOG.md).

## Prerequisites

- **JDK 25.** `java` must be on the `PATH`, or `JAVA_HOME` must point to the JDK. Check with
  `java -version`. The build compiles with `--release 25` and treats every compiler warning as
  an error.
- **Git**, to clone the repository.
- **No Gradle install is needed.** The Gradle 9.9.1 wrapper (`gradlew`, `gradlew.bat`) is
  included and downloads Gradle and JUnit on first use, so the first build needs internet
  access.

## Get the code

The implementation is on the `feature/waitlist-poc` branch, so clone that branch:

```sh
git clone -b feature/waitlist-poc https://github.com/milindpandeyy/course-waitlist-assessment.git
cd course-waitlist-assessment
```

## Build and test

macOS / Linux:

```sh
./gradlew build
./gradlew test --tests com.example.waitlist.service.CourseRegistrationServiceTest
```

Windows (PowerShell or Command Prompt):

```powershell
.\gradlew.bat build
.\gradlew.bat test --tests com.example.waitlist.service.CourseRegistrationServiceTest
```

- `build` compiles the code and runs all 96 JUnit Jupiter tests.
- The second command runs only the service test class.
- The HTML test report is written to `build/reports/tests/test/index.html`.

## Run the interactive CLI

```sh
./gradlew run -q --console=plain        # macOS / Linux
.\gradlew.bat run -q --console=plain    # Windows
```

`-q --console=plain` keeps Gradle's progress output out of the menu; plain `run` also works.
Type each answer and press Enter.

### Menu

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

| Option | Asks for | Prints |
| --- | --- | --- |
| 1. Create course | course ID, capacity, prerequisite course IDs | `Course created.` or `Course not created: <reason>` |
| 2. Enroll learner | course ID, learner ID, completed course IDs | `Result: ENROLLED`, `WAITLISTED`, `INELIGIBLE`, `ALREADY_ENROLLED`, `ALREADY_WAITLISTED`, `INVALID_COURSE_ID` or `MISSING_LEARNER_DETAILS` |
| 3. Drop learner | learner ID | `Result: DROPPED` or `LEARNER_DETAIL_NOT_FOUND` |
| 4. Check status | learner ID | `Status: ENROLLED`, `WAITLISTED`, `NOT_ENROLLED` or `MISSING_LEARNER_DETAILS` |
| 5. Available seats | — | `Available seats: <n>` |
| 6. Exit | — | `Exit.` |

Input rules:

- **A course comes first.** Choosing options 2–5 before one exists prints
  `No course yet. Create a course first.` and starts the *Create course* prompts. Only one
  course can be created per run; choosing option 1 again is refused.
- **Capacity** must be a whole number of at least 1. The course ID must not be blank.
- **Lists** (prerequisites, completed courses) are comma-separated. Leave the line blank for none.
- **IDs ignore case and surrounding spaces:** `CS101`, `cs101` and ` Cs101 ` are the same.

### Sample enrollment and waitlist workflow

The course is `CS201`, with capacity 1 and prerequisite `CS101`.

| # | Option | Input | Output | What happened |
| --- | --- | --- | --- | --- |
| 1 | 1 | `CS201`, `1`, `CS101` | `Course created.` | One seat; `CS101` is required. |
| 2 | 2 | `CS201`, `alice`, `cs101` | `Result: ENROLLED` | alice takes the only seat. |
| 3 | 2 | `CS201`, `bob`, `CS101` | `Result: WAITLISTED` | The course is full, so bob joins the waitlist. |
| 4 | 2 | `CS201`, `carol`, *(blank)* | `Result: INELIGIBLE` | carol lacks `CS101` and is not stored. |
| 5 | 2 | `CS201`, `bob`, `cs101` | `Result: ALREADY_WAITLISTED` | Duplicate request; bob keeps his place. |
| 6 | 5 | — | `Available seats: 0` | |
| 7 | 3 | `Alice` | `Result: DROPPED` | The seat is freed and bob, first in line, is promoted. |
| 8 | 4 | `BOB` | `Status: ENROLLED` | |
| 9 | 5 | — | `Available seats: 0` | bob now holds the seat. |
| 10 | 3 | `alice` | `Result: LEARNER_DETAIL_NOT_FOUND` | A repeated drop changes nothing. |
| 11 | 6 | — | `Exit.` | |

## Limitations

- **Memory only.** All state is lost when the CLI exits; there is no persistence.
- **One course per run.** There is no course catalog, and a course cannot be edited or deleted
  once created.
- **Single-threaded.** The service is not thread-safe.
- **No CLI unit tests.** The CLI only reads input and prints results. The behaviour it exposes
  is covered by the service tests, and the CLI was checked with scripted sessions.
- **Results are enum names.** The CLI prints the service's result values as they are, such as
  `INELIGIBLE`, not friendlier messages.
- **ID matching is case-insensitive, with limits.** IDs are compared lower-cased with
  `Locale.ROOT`, so case variants that need full Unicode case folding (for example `ß` and `SS`)
  are not treated as equal. Spaces inside an ID matter (`mary ann` ≠ `maryann`), and an ID
  cannot contain a comma.
- **Waitlist lookup is linear** in the queue length, which is fine for this exercise.
- **Piping input from Windows PowerShell 5.1** adds a byte-order mark to the first line, so the
  first menu choice is rejected as invalid. Typing at the prompt is unaffected, and so is
  piping from POSIX shells.

See [`DESIGN.md`](DESIGN.md) §7 for the design assumptions and §8 for the waitlist eligibility
policy.
