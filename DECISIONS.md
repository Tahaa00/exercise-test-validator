# Decisions

This file overrides `project-brief-test-validator.md` wherever they differ. One line of reason per decision.

## Where the brief is outdated

| Brief says | Actually | Source |
|---|---|---|
| Ares uses a security manager | **Ares 2** has none. It weaves AspectJ checks into the student classes at build time (`io.freefair.aspectj.post-compile-weaving`) and runs tests with a Java agent plus `--add-opens` flags. | Artemis template, two real course test repos (11.11.2026) |
| Java 21 | **Per exercise.** The tests build declares its own toolchain; current Artemis template and real course repos use 25. Template/solution repos may declare 17, but Artemis grades with the tests build. | Real course repos |
| Gradle by default / Maven example | **Gradle first**, because the course uses Gradle (wrapper 9.8.0, `./gradlew clean test`). Maven can be added behind the same interface later. | Real course repos |
| "With or without Ares" | The build **always** weaves Ares and adds the agent. What varies is the test style: **A** plain JUnit 5 + reflection helpers, **B** Ares API (test.json structural tests, `@Public`, `@StrictTimeout`). | Real course repos |

## Design decisions

| # | Decision | Reason |
|---|---|---|
| D1 | Input is an Artemis-shaped folder: `template/`, `solution/`, `tests/`, plus `alternatives/*/` added by the user. | Works on a real Artemis checkout without converting it. |
| D2 | Per variant: copy `tests/`, put the variant's `src/` into `assignment/src`, run the exercise's own `./gradlew clean test`, read `build/test-results/test/*.xml`. | Same steps as Artemis CI, so the result is what students would get; independent of test style. |
| D3 | Never edit the exercise's build files. Anything the tool adds (random test order, PIT) goes in through a Gradle init script on the command line. | We must measure the build Artemis runs, not a modified one. |
| D4 | Never hardcode a Java version for exercises; the toolchain in the tests build decides. | Tutors pick 17, 21 or 25 per exercise. |
| D5 | The tool itself targets Java 17 and uses the Gradle 9.8.0 wrapper. | 17 is the oldest JDK in use; Gradle 9.8.0 matches the course and runs on JDK 17 to 25. |
| D6 | JUnit 5.14.4 for the tool and the plain sample, not JUnit 6. | pitest-junit5-plugin 1.2.3 (May 2025) predates JUnit 6. Ares 2.2.1 brings its own JUnit. |
| D7 | Samples: `array-max` (Artemis build, style A), `array-max-ares` (Artemis build, style B), `array-max-plain` (no Ares, Java 17). | Covers both real test styles on the real build, plus a no-Ares baseline for PIT and a Java 17 case for CI. |
| D8 | Sample build files are rendered from the **public** Artemis template (ls1intum/Artemis), not copied from course repos; the Artemis Maven mirror is removed. | No course material in the public repo; the mirror is TUM-internal. |
| D9 | Each sample suite has exactly one over-specified test (exact exception message) and one weak test (`assertThrows(RuntimeException.class)` on null). | Gives the report one false fail and one false pass to show. |
| D10 | Timeout failures are reported separately from normal failures. | `@StrictTimeout(2)` makes slow CI or slow mutants look like wrong answers. |
| D11 | Flakiness check is part of step 2: 10 runs on the solution in random method order. | Brief makes it v1; it needs the same per-variant runner as the other checks. |
| D13 | Results are keyed by the XML test name. Style A samples use `@DisplayName("methodName()")`, and the tool will warn when a display name is not of that form. | Real course suites do this, and it keeps names identical to the method names Artemis tasks bind to. |
| D14 | Style A sample helpers follow the real pattern: `TestUtils` with a base package, lookup by simple class name, invoke by method name with argument matching, `expectEquals` and `fail` with readable messages instead of JUnit asserts. No `@Policy` in style A, but the tests repo still ships `SecurityPolicy.yaml` like the template. | Mirrors a real course suite (patterns only, code self-written). |
| D15 | Mutation testing uses the Gradle PIT plugin (`gradle-pitest-plugin` 1.19.0, PIT 1.30.2, pitest-junit5-plugin 1.2.3), added by `scripts/pitest.init.gradle`. | Works on the unmodified Ares build (step 1b), so D3 holds and no hand-built classpath is needed. |
| D16 | PIT gets the test task's JVM args (Ares agent, `--add-opens`), minus the JaCoCo agent. | Mutants run in the same JVM setup as grading. PIT splits args on commas, which breaks the JaCoCo arg, and PIT measures coverage itself. |
| D17 | Mutator group `STRONGER`, not `DEFAULTS`. | With `DEFAULTS` the weak null test leaves no survivor: the negated null check is killed by other tests. Only "condition removed" exposes it. |
| D18 | A mutant counts as detected when PIT says `detected='true'` (`KILLED`, `TIMED_OUT`, `RUN_ERROR`). `TIMED_OUT` and `RUN_ERROR` are shown as "detected by timeout". | Under `@StrictTimeout` an endless-loop mutant ends as `RUN_ERROR`, not `TIMED_OUT` (step 1b). Both mean the suite noticed. |
| D19 | Survivors are reported as hints with line and mutation, never as a verdict or a score. | Equivalent mutants survive every suite (`>` to `>=` in the sample), so a human has to judge. |
| D20 | PIT target classes and tests are read from the build's `main` and `test` source sets. | PIT rejects a catch-all filter and the exercise's package is not known up front. |
| D21 | The runner is Java (`GradleRunner`: `ProcessBuilder` + the JDK's XML parser, no dependencies). The checks only see the `VariantRunner` interface. | One code path for all checks, and the verdict logic is unit-tested with scripted results, without starting Gradle. |
| D22 | Five outcomes per test and run: `PASS`, `FAIL`, `TIMEOUT`, `SKIPPED`, `NOT_RUN`. `<skipped/>` means `TIMEOUT` when Gradle reports that the test JVM died in that run, else `SKIPPED`. A failure whose type or message is a JUnit timeout is `TIMEOUT` too. | In the XML a test killed by `@StrictTimeout` looks exactly like `@Disabled`; only the process exit tells them apart. |
| D23 | The expected test list is the union of test names over all runs. A test missing from a run is `NOT_RUN`, never passed. | After a killed JVM the later tests are absent from the XML, so no single run is a reliable list. |
| D24 | Solution check = one `clean test` in the declared order. Flakiness check = N more runs (`--runs`, default 10) with `test --rerun` in the same work folder, in random method and class order set through JUnit's configuration parameters. | The first run is exactly Artemis' run. The reruns reuse the compiled and woven classes: same binaries, and no rebuild per run. |
| D25 | Run seeds are drawn from `SplittableRandom(baseSeed)`, not `baseSeed + i`. The report prints the base seed (`--seed` replays all runs) and the seed of each failing run. | Found by running: JUnit shuffles with `java.util.Random`, and ten consecutive seeds gave the same order, so an order-dependent test looked stable. |
| D26 | Exercise builds run with `--no-daemon`, and each Gradle run has a time limit (`--timeout-minutes`, default 10). | A hanging test in a suite without timeouts must be killable. With a daemon the only way is `gradlew --stop`, which stops every daemon of that version (it killed the tool's own test run). Cost: roughly twice the run time. |
| D27 | A test that passes on the solution but not on an alternative is `OVER-SPECIFIED?`, with the alternative and the failure message as evidence. Differential testing of alternatives (brief 4.4) is not in v1. | The tool cannot prove an alternative correct for arbitrary signatures without input generators. So it shows evidence and a human decides; the question mark says that. |
| D28 | One status per test, in this order: `REJECTED` (not passing on the solution), `FLAKY`, `OVER-SPECIFIED?`, `NOT CHECKED` (some run never reached it), `OK`. Exit code 0 = no findings, 1 = findings, 2 = the solution could not be run. | A flaky test's other results are unreliable, so flaky outranks over-specified. `NOT CHECKED` keeps a killed JVM from looking like a pass. The exit code makes the tool usable in CI. |
| D29 | The init script also prints the toolchain's Java version, which the report shows. It changes nothing in the build. | D4: the version is read, never assumed. |
| D30 | The tool's tests: unit tests in `test`, real Gradle runs in a separate `integrationTest` task (plain sample plus a flaky fixture in `src/test/exercises`, which borrows the plain sample's build files). | `./gradlew test` stays fast and offline. The flaky case lives outside `exercises/` so D9 holds. |
| D12 | Real course material is never committed. `local-exercises/` is git-ignored for local runs. | Brief constraint; course content is not ours to publish. |

## Findings from step 1 (verified by running)

- **Test names in the XML: resolved by D13.** Gradle writes the `@DisplayName` text when there is one, else `methodName()`. Real style A suites set `@DisplayName("methodName()")`, so both styles give `methodName()` in the XML. Prose display names would break this mapping; how Artemis itself treats them is unverified.
- **An endless loop under `@StrictTimeout` kills the whole test JVM** (Gradle: test executor exit value 124). The timed-out test appears as `skipped` and every test after it is missing from the XML. The tool must report "timeout / not run" for missing tests, never treat them as passed. Matters for PIT too.
- **Structural tests pass on the template** (style B). Expected in Artemis, so the template check must not flag them as free points. `arrayIsNotModified` also passes on the template in all samples, which is a real free-points case.
- **Maven Central rate-limits (HTTP 429)** fresh dependency downloads, which is why Artemis uses a mirror. A failed first run was a 429, not a test result; the tool must tell build failures from test failures.

## Findings from step 1b: PIT on the Ares build (verified by running)

Run: `./gradlew pitest --init-script scripts/pitest.init.gradle` in a copy of `tests/` with the variant in `assignment/src`.

| Sample | Variant | Mutants | Killed | Survived | Timed out | Run error |
|---|---|---|---|---|---|---|
| array-max-plain (no Ares, Java 17) | solution | 11 | 9 | 2 | 0 | 0 |
| array-max (Ares build, style A) | solution | 11 | 9 | 2 | 0 | 0 |
| array-max-ares (Ares build, style B) | solution | 11 | 9 | 2 | 0 | 0 |
| array-max-plain | c-while-loop | 11 | 8 | 1 | 2 | 0 |
| array-max | c-while-loop | 11 | 8 | 1 | 2 | 0 |
| array-max-ares | c-while-loop | 11 | 8 | 1 | 0 | 2 |

- **PIT works on the real Ares build through the plugin**, Java 25 toolchain included. Same numbers with and without Ares, and with and without the Ares agent flags, so Ares weaving does not disturb PIT. The fallback (PIT on the woven classes by hand) was not needed.
- **The weak test is found.** Survivor 1 is "null check removed" (line 9 of the solution): all 7 tests ran and none failed. That is `nullArrayIsRejected` accepting any RuntimeException.
- **Survivor 2 is an equivalent mutant**: `values[i] > max` to `>=` returns the same maximum. No test can kill it, which is why D19 exists.
- **Endless-loop mutants do not break the run.** `c-while-loop` turns `>` to `>=` into an endless loop. Without `@StrictTimeout` PIT stops the mutant itself (`TIMED_OUT`). With `@StrictTimeout(2)` Ares kills the JVM first, PIT logs "Minion exited abnormally", marks the mutant `RUN_ERROR` and continues with a new JVM. Each such mutant costs about 5 s.
- **Limits.** `RUN_ERROR` can also mean a crash that is not a timeout; PIT does not say which. PIT only runs when the suite is green on the variant, so it runs on the solution (and valid alternatives), not on the template.
- **Two traps hit on the way:** `targetClasses = ['*']` is rejected ("would cause PIT to mutate itself"), and passing all test JVM args fails with `ClassNotFoundException: append=true` because of the JaCoCo agent arg (D16).

## Build step results

- **Step 1 (done):** all three samples run on solution, both alternatives and template with the expected results. A solution without the null check scores 100% on every sample (weak test confirmed).
- **Step 1b (done):** PIT runs on all three samples through an init script, finds the weak null test, and survives endless-loop mutants with and without `@StrictTimeout`.
- **Step 2 (done):** `validate <exercise>` runs the solution, flakiness and alternatives checks. All three samples give exactly one finding: `emptyArrayIsRejected()` fails on `b-other-message`. Run times with 10 random-order runs and 3 alternatives: about 1 min (plain) to 2 min (Ares).

## Findings from step 2 (verified by running)

- **Neighbouring seeds shuffle alike** (D25). With seeds 1 to 10 the flaky fixture ran in one order ten times; with mixed seeds the order-dependent test failed in 4 of 10 runs.
- **`gradlew --stop` is not a safe way to end a hanging build** (D26).
- **Killed JVM end to end:** an endless-loop alternative on `array-max-ares` gives `TIMEOUT` for the test that hung and `NOT CHECKED` for the six tests after it, each naming the alternative. Nothing is reported as passed.
- **A hanging test without any timeout** (plain build) is ended by the tool's time limit and reported as a problem for that variant; the other variants are still checked.
- **Failure messages span several lines** in style A (`expected` and `actual` on their own lines), so the report joins them into one line.
- **Limit:** a class with its own `@TestMethodOrder` keeps its order in the flakiness runs.
