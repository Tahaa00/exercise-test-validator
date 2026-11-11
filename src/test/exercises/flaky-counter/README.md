Fixture for the validator's own integration tests, not a sample exercise.
It has no build files: the test copies them from `exercises/array-max-plain/tests`.

- `CounterTest` shares one static `Counter` between two tests, so the result depends on the method order (flaky).
- `alternatives/a-hangs` loops forever, to exercise the tool's own time limit on a build without test timeouts.
