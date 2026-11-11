# exercise-test-validator

> Work in progress. Build step 1 of 5: sample exercises only, no checks yet.

Checks whether the unit tests of a Java programming exercise are correct **before** they grade students.
It runs the tests instead of judging them:

- **False fail:** a correct solution fails a test (for example, a test that demands one exact error message).
  Found by running the tests against several different correct solutions.
- **False pass:** a wrong solution passes the tests (for example, a test that only checks "something was thrown").
  Found by mutation testing with [PIT](https://pitest.org).
- **Flaky tests:** a test that gives different results on repeated runs.

It works on exercises laid out like an [Artemis](https://github.com/ls1intum/Artemis) programming exercise
(`template/`, `solution/`, `tests/`) and runs the exercise's own Gradle build, so it does not care how the tests are written.
No API key or LLM is needed.

## Repository layout

```
exercises/
  array-max/        Artemis Gradle build with Ares, plain JUnit 5 tests + reflection helpers (style A)
  array-max-ares/   Artemis Gradle build with Ares, Ares API tests: test.json structure tests + @StrictTimeout (style B)
  array-max-plain/  Plain Gradle build without Ares, Java 17 (baseline for PIT)
src/                The validator (Java 17, Gradle 9.8.0)
DECISIONS.md        Design decisions and where they differ from the original plan
```

Each exercise folder contains:

```
problem-statement.md
template/src/       Student starter code (should score 0%)
solution/src/       Reference solution
alternatives/*/src/ Other correct solutions, used to find over-specified tests
tests/              The test repository: build files + test/ (the suite being checked)
```

All sample exercises are self-written. The validator is meant to be run on real course exercises **locally**;
never commit real course material here (the `local-exercises/` folder is git-ignored for that).

## Requirements

- JDK 17 to run the build. Gradle downloads itself through the wrapper.
- The JDK version each exercise declares in its toolchain: **17 and 25** for the samples here.
  The exercises' builds mirror Artemis and do not auto-download JDKs, so these must be installed locally.

## Trying a sample by hand (until step 2)

```bash
scripts/run-variant.sh exercises/array-max solution
scripts/run-variant.sh exercises/array-max alternatives/b-other-message
```

This does what Artemis CI does: copy `tests/`, put the variant's code into `assignment/src`, run `./gradlew clean test`.
