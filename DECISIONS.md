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
| D12 | Real course material is never committed. `local-exercises/` is git-ignored for local runs. | Brief constraint; course content is not ours to publish. |

## Findings from step 1 (verified by running)

- **Test names in the XML: resolved by D13.** Gradle writes the `@DisplayName` text when there is one, else `methodName()`. Real style A suites set `@DisplayName("methodName()")`, so both styles give `methodName()` in the XML. Prose display names would break this mapping; how Artemis itself treats them is unverified.
- **An endless loop under `@StrictTimeout` kills the whole test JVM** (Gradle: test executor exit value 124). The timed-out test appears as `skipped` and every test after it is missing from the XML. The tool must report "timeout / not run" for missing tests, never treat them as passed. Matters for PIT too.
- **Structural tests pass on the template** (style B). Expected in Artemis, so the template check must not flag them as free points. `arrayIsNotModified` also passes on the template in all samples, which is a real free-points case.
- **Maven Central rate-limits (HTTP 429)** fresh dependency downloads, which is why Artemis uses a mirror. A failed first run was a 429, not a test result; the tool must tell build failures from test failures.

## Build step results

- **Step 1 (done):** all three samples run on solution, both alternatives and template with the expected results. A solution without the null check scores 100% on every sample (weak test confirmed).
