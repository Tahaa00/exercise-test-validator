# exercise-test-validator

Helps tutors review the unit tests of a Java programming exercise before they grade students.
It runs the suite and reports evidence for three kinds of problems.

- **False fail:** a correct solution fails a test (for example, a test that demands one exact error message).
  Found by running the tests against several different correct solutions.
- **False pass:** a wrong solution passes the tests (for example, a test that only checks "something was thrown").
  Try a tutor-written wrong solution to see which tests catch it. Mutation testing with [PIT](https://pitest.org)
  can also suggest gaps.
- **Flaky tests:** a test that gives different results on repeated runs.

It works on exercises laid out like an [Artemis](https://github.com/ls1intum/Artemis) programming exercise
(`template/`, `solution/`, `tests/`) and runs the exercise's own Gradle build, so it does not care how the tests are written.
No API key or LLM is needed.

## Repository layout

```
exercises/
  array-max/        Artemis Gradle build with Ares, plain JUnit 5 tests + reflection helpers (style A), Gradle 8.12 + Java 17
  array-max-ares/   Artemis Gradle build with Ares, Ares API tests: test.json + @StrictTimeout (style B), Gradle 9.8 + Java 25
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
buggy/*/src/        Optional tutor-written wrong solutions, ideally one mistake each
tests/              The test repository: build files + test/ (the suite being checked)
```

All sample exercises are self-written. The validator is meant to be run on real course exercises **locally**;
never commit real course material here (the `local-exercises/` folder is git-ignored for that).

## Requirements

- JDK 17 to build and run the tool. Gradle downloads itself through the wrapper.
- The JDK each exercise declares in its toolchain: **17 and 25** for the samples here.
  The exercises' builds mirror Artemis and do not auto-download JDKs, so these must be installed locally.
- The exercise's Gradle must be able to start on the JDK the tool runs with. Gradle 8.12 (`array-max`) does not
  start on JDK 25. Running the tool on JDK 17 works for every sample; otherwise pass `--java-home <JDK 17>`.

## Usage

```bash
./gradlew installDist
build/install/exercise-test-validator/bin/exercise-test-validator validate exercises/array-max --seed 100
```

```
array-max  (Java 17, 7 tests, 3 alternatives, 10 random-order runs, base seed 100)

Tests on the reference, shuffled reruns and correct alternatives

  PASSED CHECKS    com.example.arraymax.ArrayMaxTest#maxOfNegativeValues()
                     Passed on the solution. Random-order runs passed: 10. Correct alternatives passed: 3.
  PASSED CHECKS    com.example.arraymax.ArrayMaxTest#maxOfSingleValue()
                     Passed on the solution. Random-order runs passed: 10. Correct alternatives passed: 3.
  OVER-SPECIFIED?  com.example.arraymax.ArrayMaxTest#emptyArrayIsRejected()
                     FAIL (max([]) threw the wrong message. Expected: Array must not be empty But was: values has no elements) on alternatives/b-other-message
  PASSED CHECKS    com.example.arraymax.ArrayMaxTest#maxAtLastPosition()
                     Passed on the solution. Random-order runs passed: 10. Correct alternatives passed: 3.
  PASSED CHECKS    com.example.arraymax.ArrayMaxTest#maxOfMixedValues()
                     Passed on the solution. Random-order runs passed: 10. Correct alternatives passed: 3.
  PASSED CHECKS    com.example.arraymax.ArrayMaxTest#nullArrayIsRejected()
                     Passed on the solution. Random-order runs passed: 10. Correct alternatives passed: 3.
  PASSED CHECKS    com.example.arraymax.ArrayMaxTest#arrayIsNotModified()
                     Passed on the solution. Random-order runs passed: 10. Correct alternatives passed: 3.

  WARNING  Test inventory uses observed executions only. Tests absent from every run require an independent expected list.

Tutor-written buggy variants

  MISSED  buggy/missing-null-guard  (all 7 tests passed)
    Requirement: Throw IllegalArgumentException when the input is null.
    Witness input (tutor): max(null)
    Expected (tutor): IllegalArgumentException
    Actual (tutor): NullPointerException
    Intended test: nullArrayIsRejected()  PASS

  CAUGHT  buggy/sorts-input
    Requirement: Return the maximum and leave the caller's array unchanged.
    Witness input (tutor): max([2, 1])
    Expected (tutor): Returns 2, array remains [2, 1].
    Actual (tutor): Returns 2, array becomes [1, 2].
    Intended test: arrayIsNotModified()  FAIL
    Caught by: com.example.arraymax.ArrayMaxTest#arrayIsNotModified()  FAIL  max must not change the array it is given. Expected: [5, 1, 3] But was: [1, 3, 5]

Mutation check (PIT on the solution, STRONGER mutators)

  11 mutants: 9 killed, 0 timed out, 0 run errors, 2 survived, 0 not covered

  SURVIVED     ArrayMax.java:9   max()  removed conditional - replaced equality check with true
                 if (values == null || values.length == 0) {
  SURVIVED     ArrayMax.java:14  max()  changed conditional boundary
                 if (values[i] > max) {

  These are hints, not errors. SURVIVED: tests ran the changed line and still passed, so a test
  may be missing or too weak, or the mutant is equivalent (same behaviour as the original).
  NOT COVERED: no test runs that line.

Solution: 7/7 pass   Flaky: 0   Fail on an alternative: 1   Buggy variants missed: 1   Mutants not detected: 2
Result: 2 findings
```

For every variant the tool does what Artemis CI does: copy `tests/`, put the variant's code into `assignment/src`,
run the exercise's own `./gradlew clean test`, read the JUnit XML. The exercise's build files are never changed.

| Status | Meaning |
|---|---|
| `PASSED CHECKS` | This invocation passed on the reference and in the listed reruns and correct alternatives. A weak assertion can still pass all of them. |
| `REJECTED` | Does not pass on the reference solution. |
| `FLAKY` | Passes in the declared order, but not in every random-order run. The failing seeds are shown. |
| `OVER-SPECIFIED?` | Fails on an alternative solution. Either the test demands more than the task, or the alternative is wrong: you decide. |
| `NOT CHECKED` | A required run lacks an attributable result. The report identifies skipped, missing, duplicate or incomplete executions. |

Timeouts are shown as `TIMEOUT`, not as `FAIL`. A build that produces no test results at all (compile error,
failed dependency download) is reported as a build problem, not as failing tests.

### Checking which tests ran

The report identifies each invocation by its JUnit XML class and name. Two classes can both have a `oneHour()` test and receive different results. By default the validator can list only the tests it observed. To detect a test excluded from every run, supply the tests you intended to grade.

```bash
build/install/exercise-test-validator/bin/exercise-test-validator validate path/to/exercise \
  --expected-tests expected-tests.txt --decisions baseline.csv
```

Use one UTF-8 line per expected invocation, for example `clock.ConversionTest#oneHour()`. Use the exact name the exercise's JUnit XML reports. When several invocations grade one criterion, append a literal tab and the same class-qualified grading ID to each member line. Every member has to pass the requested checks to earn a baseline `OK`. Without a list, the export has one row per observed invocation.

The CSV contains `exercise,test,decision`. Use a fresh output path outside the exercise's source and test folders. Keep the text report alongside it for the evidence. The exercise ID is the exercise folder's name.

- `OK` records a baseline pass on the checks you ran. Its reliability as a reason to skip manual review has yet to be measured.
- `REVIEW` flags a failure on the reference, a shuffled run or a correct alternative. The text report names the failing test and where it failed.
- `INCOMPLETE` means evidence is missing or ambiguous. It takes precedence over other decisions in a group. A failed required build, an unexpected invocation outside the list, or a requested PIT check that could not run makes the whole export incomplete. The text report retains individual observations.

A test can pass while a requirement goes untested. In the sample above, `nullArrayIsRejected()` passed every correct implementation **and** accepted the wrong exception on `buggy/missing-null-guard`. The buggy-variant section shows that gap.

### Tutor-written buggy variants

Put a wrong implementation in `buggy/<name>/src/`, using the same layout as `solution/src/`.
Pick a single mistake and an input that demonstrates it. The validator runs the grading suite once on
each variant. Read the named failures to see which tests reacted, including any outside the intended task.

An optional `bug.properties` next to `src/` can describe the `requirement`, `input`, `expected` and
`actual` behavior, and name the `intendedTest`. Those descriptions come from the tutor. The validator
reads the intended test’s outcome from the grading run. See the [missing null guard](exercises/array-max/buggy/missing-null-guard/bug.properties)
and [in-place sort](exercises/array-max/buggy/sorts-input/bug.properties) examples.

- `MISSED` adds one finding for a known wrong variant that passed the complete suite.
- `CAUGHT` identifies a test that passed reliably on the reference and failed on the variant.
- `INCONCLUSIVE` records a run problem, such as a failed build, missing test or ambiguous result.

### Mutation check

After the solution runs, [PIT](https://pitest.org) plants small bugs (mutants) in the solution and reruns the tests
for each. A mutant no test notices is listed with its line. In the sample above:

- Line 9, null check removed: `nullArrayIsRejected()` accepts any `RuntimeException`, so the
  `NullPointerException` that follows still passes. This is the weak test.
- Line 14, `>` changed to `>=`: the method still returns the same maximum. No test can catch this
  (an equivalent mutant), which is why survivors are hints and neither count as findings nor change the exit code.

PIT is added through a Gradle init script and runs with the test task's JVM arguments (Ares agent included),
so the exercise's build files stay untouched. It only runs when every test passes on the solution.

- A method no test runs at all is one line with its line range, not one line per mutant.
- A `main(String[])` no test runs (the usual demo entry point) is left out of the counts and named in one line.
- Older exercises on Ares 1 run the tests under a security manager that blocks PIT. For the PIT run only, the tool
  tells Ares to trust the solution's classes and PIT, and the report says so. The graded runs stay sandboxed.

Options: `--runs <n>` (random-order runs, default 10), `--seed <n>` (replay the same runs),
`--no-mutation` (skip the mutation check),
`--timeout-minutes <n>` (limit per Gradle run, default 10), `--java-home <path>` (JDK to start the exercise's Gradle with),
`--keep-work` (keep work folders and Gradle logs).
Exit codes are 0 for no findings in the checks run, 1 for findings or incomplete grading evidence, 2 for input, file or fatal reference errors, and 3 when an attempted PIT run fails to produce a usable report. If the reference suite is unfit for PIT, the report gives the reason for skipping it and the reference findings determine the exit code.

## Tests of the tool itself

```bash
./gradlew test             # unit tests, fast and offline
./gradlew integrationTest  # real Gradle runs: plain sample, flaky fixture, Ares 1 fixture (needs JDK 17)
```
