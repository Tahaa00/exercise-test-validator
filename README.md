# exercise-test-validator

> Work in progress. Build step 2 of 5: solution, flakiness and alternatives checks work. Mutation testing (PIT) is next.

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

## Usage

```bash
./gradlew installDist
build/install/exercise-test-validator/bin/exercise-test-validator validate exercises/array-max
```

```
array-max  (Java 25, 7 tests, 3 alternatives, 10 random-order runs, base seed 100)

  OK               maxOfNegativeValues()
  OK               maxOfSingleValue()
  OVER-SPECIFIED?  emptyArrayIsRejected()
                     FAIL (max([]) threw the wrong message. Expected: Array must not be empty But was: values has no elements) on alternatives/b-other-message
  OK               maxAtLastPosition()
  OK               maxOfMixedValues()
  OK               nullArrayIsRejected()
  OK               arrayIsNotModified()

Solution: 7/7 pass   Flaky: 0   Fail on an alternative: 1
Result: 1 finding
```

For every variant the tool does what Artemis CI does: copy `tests/`, put the variant's code into `assignment/src`,
run the exercise's own `./gradlew clean test`, read the JUnit XML. The exercise's build files are never changed.

| Status | Meaning |
|---|---|
| `OK` | Passes on the solution, in every random-order run and on every alternative. |
| `REJECTED` | Does not pass on the reference solution. |
| `FLAKY` | Passes in the declared order, but not in every random-order run. The failing seeds are shown. |
| `OVER-SPECIFIED?` | Fails on an alternative solution. Either the test demands more than the task, or the alternative is wrong: you decide. |
| `NOT CHECKED` | A run never reached this test because the test JVM was killed earlier (a timeout in another test). |

Timeouts are shown as `TIMEOUT`, not as `FAIL`. A build that produces no test results at all (compile error,
failed dependency download) is reported as a build problem, not as failing tests.

Options: `--runs <n>` (random-order runs, default 10), `--seed <n>` (replay the same runs),
`--timeout-minutes <n>` (limit per Gradle run, default 10), `--keep-work` (keep work folders and Gradle logs).
Exit code: 0 no findings, 1 findings, 2 the solution could not be run.

## Tests of the tool itself

```bash
./gradlew test             # unit tests, fast and offline
./gradlew integrationTest  # real Gradle runs on the plain sample and a flaky fixture (needs JDK 17)
```
