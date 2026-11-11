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
| D7 | Samples: `array-max` (Artemis build, style A, **Gradle 8.12 + Java 17** since D31), `array-max-ares` (Artemis build, style B, Gradle 9.8.0 + Java 25), `array-max-plain` (no Ares, Java 17). | Covers both real test styles on the real build, both build setups in use, plus a no-Ares baseline for PIT. |
| D8 | Sample build files are rendered from the **public** Artemis template (ls1intum/Artemis), not copied from course repos; the Artemis Maven mirror is removed. | No course material in the public repo; the mirror is TUM-internal. |
| D9 | Each sample suite has exactly one over-specified test (exact exception message) and one weak test (`assertThrows(RuntimeException.class)` on null). | Gives the report one false fail and one false pass to show. |
| D10 | Timeout failures are reported separately from normal failures. | `@StrictTimeout(2)` makes slow CI or slow mutants look like wrong answers. |
| D11 | Flakiness check is part of step 2: 10 runs on the solution in random method order. | Brief makes it v1; it needs the same per-variant runner as the other checks. |
| D13 | Results are keyed by the XML test name. Style A samples use `@DisplayName("methodName()")`, and the tool will warn when a display name is not of that form. | Real course suites do this, and it keeps names identical to the method names Artemis tasks bind to. |
| D14 | Style A sample helpers follow the real pattern: `TestUtils` with a base package, lookup by simple class name, invoke by method name with argument matching, `expectEquals` and `fail` with readable messages instead of JUnit asserts. No `@Policy` in style A, but the tests repo still ships `SecurityPolicy.yaml` like the template. | Mirrors a real course suite (patterns only, code self-written). |
| D15 | Mutation testing uses the Gradle PIT plugin (`gradle-pitest-plugin` 1.19.0, PIT 1.30.2, pitest-junit5-plugin 1.2.3), added by the init script `pitest.init.gradle` (in the tool's resources since step 3). | Works on the unmodified Ares build (step 1b), so D3 holds and no hand-built classpath is needed. |
| D16 | PIT gets the test task's JVM args (Ares agent, `--add-opens`), minus the JaCoCo agent. | Mutants run in the same JVM setup as grading. PIT splits args on commas, which breaks the JaCoCo arg, and PIT measures coverage itself. |
| D17 | Mutator group `STRONGER`, not `DEFAULTS`. | With `DEFAULTS` the weak null test leaves no survivor: the negated null check is killed by other tests. Only "condition removed" exposes it. |
| D18 | A mutant counts as detected when PIT says `detected='true'` (`KILLED`, `TIMED_OUT`, `RUN_ERROR`). `TIMED_OUT` and `RUN_ERROR` are shown as "detected by timeout". | Under `@StrictTimeout` an endless-loop mutant ends as `RUN_ERROR`, not `TIMED_OUT` (step 1b). Both mean the suite noticed. |
| D19 | Survivors are reported as hints with line and mutation, never as a verdict or a score. | Equivalent mutants survive every suite (`>` to `>=` in the sample), so a human has to judge. |
| D20 | PIT target classes and tests are read from the build's `main` and `test` source sets. | PIT rejects a catch-all filter and the exercise's package is not known up front. |
| D21 | The runner is Java (`GradleRunner`: `ProcessBuilder` + the JDK's XML parser, no dependencies). The checks only see the `VariantRunner` interface. | One code path for all checks, and the verdict logic is unit-tested with scripted results, without starting Gradle. |
| D22 | Five outcomes per test and run: `PASS`, `FAIL`, `TIMEOUT`, `SKIPPED`, `NOT_RUN`. A failure whose type or message is a JUnit timeout is `TIMEOUT`. For a killed test JVM see D32. | Timeouts point to a slow machine or an endless loop, not a wrong answer (D10). |
| D23 | The expected test list is the union of test names over all runs. A test missing from a run is `NOT_RUN`, never passed. | After a killed JVM the later tests are absent from the XML, so no single run is a reliable list. |
| D24 | Solution check = one `clean test` in the declared order. Flakiness check = N more runs (`--runs`, default 10) with `test --rerun` in the same work folder, in random method and class order set through JUnit's configuration parameters. | The first run is exactly Artemis' run. The reruns reuse the compiled and woven classes: same binaries, and no rebuild per run. |
| D25 | Run seeds are drawn from `SplittableRandom(baseSeed)`, not `baseSeed + i`. The report prints the base seed (`--seed` replays all runs) and the seed of each failing run. | Found by running: JUnit shuffles with `java.util.Random`, and ten consecutive seeds gave the same order, so an order-dependent test looked stable. |
| D26 | Exercise builds run with `--no-daemon`, and each Gradle run has a time limit (`--timeout-minutes`, default 10). | A hanging test in a suite without timeouts must be killable. With a daemon the only way is `gradlew --stop`, which stops every daemon of that version (it killed the tool's own test run). Cost: roughly twice the run time. |
| D27 | A test that passes on the solution but not on an alternative is `OVER-SPECIFIED?`, with the alternative and the failure message as evidence. Differential testing of alternatives (brief 4.4) is not in v1. | The tool cannot prove an alternative correct for arbitrary signatures without input generators. So it shows evidence and a human decides; the question mark says that. |
| D28 | One status per test, in this order: `REJECTED` (not passing on the solution), `FLAKY`, `OVER-SPECIFIED?`, `NOT CHECKED` (some run never reached it), `OK`. Exit code 0 = no findings, 1 = findings, 2 = the solution could not be run. | A flaky test's other results are unreliable, so flaky outranks over-specified. `NOT CHECKED` keeps a killed JVM from looking like a pass. The exit code makes the tool usable in CI. |
| D29 | The init script also prints the toolchain's Java version, which the report shows. It changes nothing in the build. | D4: the version is read, never assumed. |
| D30 | The tool's tests: unit tests in `test`, real Gradle runs in a separate `integrationTest` task (plain sample plus a flaky fixture in `src/test/exercises`, which borrows the plain sample's build files). | `./gradlew test` stays fast and offline. The flaky case lives outside `exercises/` so D9 holds. |
| D31 | The course setup of WS 2026/27 is Java 17 + Gradle 8.12 (course instruction, 11.11.2026). `array-max` uses it; only the wrapper properties and the toolchain line differ from the Artemis template. The tool still hardcodes neither version. | Verified by running: freefair 9.8.0, Ares 2.2.1 (Java 17 bytecode) and AspectJ 1.9.25.1 work unchanged on Gradle 8.12. |
| D32 | When Gradle reports a killed test JVM, results are repaired from the tool's own record of test starts and ends (`validator-test-events.tsv`, written by the init script). The hanging test is the one closed as SKIPPED with no test starting after it. The JUnit XML stays the only source for normal runs. | Gradle 8.12 writes no XML at all after a killed JVM, Gradle 9.8.0 a partial one where the hanging test looks `@Disabled`. The record is the same on both. |
| D33 | Gradle runs on the JDK of the tool's environment, or of `--java-home`. No automatic JDK search. A "class file major version" failure gets a hint that names the fix. | Gradle 8.12 cannot start on JDK 25. Finding installed JDKs differs per OS and is not worth it in v1; the tests run on the build's toolchain either way. |
| D34 | The mutation check runs `pitest` once, on the solution, in the work folder of the solution run and without `clean`. The tool reads PIT's `mutations.xml` with the JDK's XML parser. | PIT mutates exactly the classes that were just tested, with no second build. XML is PIT's stable machine-readable output, and the tool stays free of dependencies. |
| D35 | PIT only starts when every test passed in the declared-order solution run. Otherwise the report says "Skipped" with the reason. | PIT first runs the suite unchanged and aborts if a test fails: a red test cannot tell a mutant from the original. |
| D36 | The report shows counts ("11 mutants: 9 detected, 2 survived"), no percentage and no threshold. This replaces the brief's "mutation score". | One of the two survivors in the sample is an equivalent mutant no test can kill. "82%" would read as a defect of the suite; the count plus the list lets a human judge (D19). |
| D37 | Undetected mutants and a failed PIT run are not findings and do not change the exit code. | A CI job must not go red over an equivalent mutant or a failed plugin download. The per-test verdicts stay the only pass/fail signal. |
| D38 | The mutation check is on by default; `--no-mutation` skips it. | Weak tests are half of what the tool looks for, so the default report should show them. It costs one more Gradle run. |
| D39 | Each undetected mutant is printed with the mutated source line, read from the solution in the work folder. The file is found by package path plus file name, because PIT gives no path. | A tutor can judge "missing test or equivalent mutant?" from the report alone, without opening PIT's HTML report. |
| D40 | Mutants are listed as `SURVIVED` (tests ran the line and passed) or `NOT COVERED` (no test runs the line). Mutants detected without a failing assertion are counted as "by timeout or crash". | The two undetected kinds need different fixes: a stronger assertion or a new test. `RUN_ERROR` does not say whether it was a timeout or a crash (D18), so the label claims neither. |
| D41 | On a build with Ares 1 (`artemis-java-test-sandbox`), the PIT run makes Ares trust the solution's classes and `org.pitest`, with Ares' own system properties `ares.security.trustedpackages` and `ares.gradle.ignore`. Only the PIT run, only when Ares 1 is on the test classpath; the report says so. | Ares 1 has a security manager that forbids untrusted code to load `sun.*`, where PIT's coverage probes live, and distrusts PIT's frames on the stack. The code that is trusted is the tutor's own solution, and the graded runs (solution, flakiness, alternatives) stay sandboxed. Refines D16. |
| D42 | The two properties reach PIT's test JVMs through `JAVA_TOOL_OPTIONS` on the `pitest` task, not through PIT's `jvmArgs`. | The trusted list contains commas, and PIT splits `jvmArgs` on commas (D16). No build file is edited (D3). |
| D43 | Mutants in a `main(String[])` that no test runs are left out of the counts and named in one line. A `main` with at least one covered mutant is reported like any other method. | Exercises ship a demo `main` that is not graded; on a real exercise it produced half of all hints. Nothing is hidden, and a tested `main` is not assumed to be a demo. |
| D44 | A method in which no mutant is covered is printed as one line with its line range and mutant count. | "No test runs this method" is one gap, not one per mutant (9 lines for one untested method on a real exercise). |
| D45 | When PIT gives no report, the tool shows PIT's own reason: the tests that failed only inside PIT by name, or PIT's error line. Gradle's text is the fallback. | Gradle only says "java finished with non-zero exit value 1". |
| D12 | Real course material is never committed. `local-exercises/` is git-ignored for local runs. | Brief constraint; course content is not ours to publish. |

## Findings from step 1 (verified by running)

- **Test names in the XML: resolved by D13.** Gradle writes the `@DisplayName` text when there is one, else `methodName()`. Real style A suites set `@DisplayName("methodName()")`, so both styles give `methodName()` in the XML. Prose display names would break this mapping; how Artemis itself treats them is unverified.
- **An endless loop under `@StrictTimeout` kills the whole test JVM** (Gradle: test executor exit value 124). The timed-out test appears as `skipped` and every test after it is missing from the XML. The tool must report "timeout / not run" for missing tests, never treat them as passed. Matters for PIT too.
- **Structural tests pass on the template** (style B). Expected in Artemis, so the template check must not flag them as free points. `arrayIsNotModified` also passes on the template in all samples, which is a real free-points case.
- **Maven Central rate-limits (HTTP 429)** fresh dependency downloads, which is why Artemis uses a mirror. A failed first run was a 429, not a test result; the tool must tell build failures from test failures.

## Findings from step 1b: PIT on the Ares build (verified by running)

Run: `./gradlew pitest --init-script <path to>/src/main/resources/dev/exercisevalidator/pitest.init.gradle` in a copy of `tests/` with the variant in `assignment/src`.

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
- **Step 3 (done):** the report has a mutation section: counts, plus every undetected mutant with its source line. Same result on all three samples: 11 mutants, 9 detected, 2 survived.
- **Step 2 (done):** `validate <exercise>` runs the solution, flakiness and alternatives checks. All three samples give exactly one finding: `emptyArrayIsRejected()` fails on `b-other-message`. Run times with 10 random-order runs and 3 alternatives: about 1 min (plain) to 2 min (Ares).

## Findings from step 2 (verified by running)

- **Neighbouring seeds shuffle alike** (D25). With seeds 1 to 10 the flaky fixture ran in one order ten times; with mixed seeds the order-dependent test failed in 4 of 10 runs.
- **`gradlew --stop` is not a safe way to end a hanging build** (D26).
- **Killed JVM end to end:** an endless-loop alternative on `array-max-ares` gives `TIMEOUT` for the test that hung and `NOT CHECKED` for the six tests after it, each naming the alternative. Nothing is reported as passed.
- **A hanging test without any timeout** (plain build) is ended by the tool's time limit and reported as a problem for that variant; the other variants are still checked.
- **Failure messages span several lines** in style A (`expected` and `actual` on their own lines), so the report joins them into one line.
- **Limit:** a class with its own `@TestMethodOrder` keeps its order in the flakiness runs.


## Findings: Gradle 8.12 with Java 17 (verified by running, 11.11.2026)

- **The Ares build works with two edits**: wrapper to 8.12, toolchain to 17. All tests pass, the validator report is the same, Java 17 is read from the build.
- **Gradle 8.12 does not start on JDK 25** ("Unsupported class file major version 69"); it starts on 17 and 21. Gradle 9.8.0 starts on 17 to 25.
- **Killed test JVM, no XML**: with an endless loop under `@StrictTimeout`, Gradle 8.12 leaves `build/test-results/test` without any XML; 9.8.0 writes the tests up to the hanging one. With D32 both give the same report: `TIMEOUT` for the hanging test, `NOT CHECKED` for the rest. Artemis reads the same XML, so on 8.12 a student's endless loop probably shows as a failed build without per-test feedback (not tested on Artemis).
- **Gradle closes the hanging test as SKIPPED** in its test events on both versions, so "started but never ended" does not identify it; "skipped and nothing started afterwards" does.
- **PIT is unchanged** on `array-max` with 8.12 + 17: solution 11 mutants, 9 killed, 2 survived; `c-while-loop` 8 killed, 1 survived, 2 timed out. Same on a style B copy (2 `RUN_ERROR` instead of timed out).

## Findings from step 3: PIT in the report (verified by running, 11.11.2026)

Run: `validate <exercise> --runs 2 --seed 1` with the tool on JDK 17.

| Sample | Setup | Mutants | Detected | Survived | Not covered |
|---|---|---|---|---|---|
| array-max-plain | Gradle 9.8.0, Java 17, no Ares | 11 | 9 | 2 | 0 |
| array-max | Gradle 8.12, Java 17, Ares | 11 | 9 | 2 | 0 |
| array-max-ares | Gradle 9.8.0, Java 25, Ares, `--java-home` JDK 25 | 11 | 9 | 2 | 0 |
| copy of array-max-ares with `c-while-loop` as the solution | as above | 11 | 10 (2 by timeout or crash) | 1 | 0 |

- **Same numbers as the step 1b spike** on every setup, now from the tool's own report instead of a manual PIT run.
- **Both survivors are shown with their line**: line 9 (null check removed, the weak `nullArrayIsRejected`) and line 14 (`>` to `>=`, the equivalent mutant).
- **Endless-loop mutants under `@StrictTimeout`** end as `RUN_ERROR` and show up as "2 of them by timeout or crash". The run continues and the report is complete.
- **Cost:** PIT adds one Gradle run to the solution. The whole plain sample with 2 random-order runs and 3 alternatives took 44 s.
- **Limits.** PIT's description of a removed condition names the bytecode branch ("replaced equality check with true"), which can read inverted next to the source line. The mutator group is fixed to `STRONGER`. PIT runs on the solution only, not on alternatives.

## Findings from the trial on six real exercises (verified by running, 11.11.2026)

The tool ran on six real course exercises (not in this repo, D12). All solutions passed, no test was flaky. Two tool bugs came out of it and were reproduced on the self-written fixture `src/test/exercises/ares1-sandbox` (Java 17, Gradle 8.12, Ares 1.13.0).

- **Ares 1 blocks PIT.** PIT's log shows `BAD PACKAGE ACCESS: sun.pitest`, every test fails in PIT's coverage run, and PIT stops with "tests did not pass without mutation". Ares 1 lists `sun.` as a package untrusted code may not use.
- **Trusting the solution is not enough.** With only the solution trusted, the coverage run is green, but every mutant is reported `KILLED` with `numberOfTestsRun='0'`: Ares now rejects `org.pitest` frames on the stack, so each test fails before it runs. A wrong "all mutants killed" is worse than a crash, which is why `org.pitest` is trusted too (D41).
- **Ares checks the build file.** A trusted package without a matching rule in `build.gradle` gives a `ConfigurationException`; `ares.gradle.ignore=true` switches that check off without touching the file.
- **Result with D41:** 11 mutants, 9 detected, 2 survived on the fixture, the same as the three samples. Same numbers with Ares 1.11.3 and 1.15.0. A class in the default package works, because Ares matches the trusted list as a prefix of the class name. Ares 1.8.0 does not start on Java 17 at all, with or without the tool.
- **Endless-loop mutants under Ares 1** fail the test through `@StrictTimeout` without killing the JVM, so they count as `KILLED`, not as "by timeout or crash".
- **Demo `main`:** the fixture's `Main.main` yields 5 mutants no test covers; they are left out and named in one line (D43).
- **Limit.** The mutants of the solution run without the Ares sandbox. A suite that expects a `SecurityException` from the solution would behave differently in the PIT run; none of the real exercises does.
