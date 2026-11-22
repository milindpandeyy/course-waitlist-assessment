# course-waitlist-assessment

A small proof of concept for a course waitlist: a course has a fixed number of seats,
students enroll until it is full, later students join a first-come-first-served waitlist,
and when an enrolled student drops, the head of the waitlist is promoted automatically.

It is a plain Java library (no framework, no persistence, no UI). The only dependency is
JUnit 5 for tests.

## Requirements

- A JDK, version 21 or newer, on the `PATH` or in `JAVA_HOME`. The code is compiled with
  `--release 21`, so a newer JDK (it was built here with JDK 25) produces Java 21 bytecode.
- Nothing else: the Gradle wrapper (Gradle 9.9.1) downloads itself on first use.

## Build and test

```powershell
.\gradlew.bat build
```

On macOS/Linux use `./gradlew build`. Test reports land in `build/reports/tests/test/index.html`.

## Usage

```java
Course course = new Course("CS101", "Intro to CS", 2);

course.enroll(StudentId.of("alice"));   // ENROLLED
course.enroll(StudentId.of("bob"));     // ENROLLED
course.enroll(StudentId.of("carol"));   // WAITLISTED
course.waitlistPosition(StudentId.of("carol")); // OptionalInt[1]

DropResult r = course.drop(StudentId.of("alice"));
r.outcome();   // LEFT_COURSE
r.promoted();  // Optional[carol]
course.statusOf(StudentId.of("carol")); // ENROLLED
```

## Layout

```
build.gradle, settings.gradle   Gradle build (Groovy DSL)
src/main/java/com/example/waitlist/
  Course.java            course state, enroll / drop / queries
  StudentId.java         validated student identifier
  EnrollmentResult.java  outcome of enroll()
  DropResult.java        outcome of drop(), including who was promoted
  EnrollmentStatus.java  ENROLLED / WAITLISTED / NOT_REGISTERED
src/test/java/com/example/waitlist/
  CourseTest.java, StudentIdTest.java
```

## Further reading

- [DESIGN.md](DESIGN.md): domain rules, assumptions made in the absence of a spec, and what is out of scope.
- [AI_USAGE_LOG.md](AI_USAGE_LOG.md): how AI assistance was used to produce this repository.
