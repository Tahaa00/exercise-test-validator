Fixture for the validator's own integration tests, not a sample exercise.
It has no Gradle wrapper: the test copies it from `exercises/array-max/tests` (Gradle 8.12, Java 17).

- The build uses Ares 1 (`artemis-java-test-sandbox`), whose security manager blocks PIT's coverage probes
  unless the tool makes Ares trust them (DECISIONS.md D41).
- `Main.main` is a demo no test calls, so its mutants must be left out of the mutation counts (D43).
