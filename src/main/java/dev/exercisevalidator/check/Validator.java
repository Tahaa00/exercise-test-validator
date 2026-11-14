package dev.exercisevalidator.check;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import dev.exercisevalidator.check.Verdict.Status;
import dev.exercisevalidator.run.BugDescription;
import dev.exercisevalidator.run.Exercise;
import dev.exercisevalidator.run.MutationResult;
import dev.exercisevalidator.run.RunResult;
import dev.exercisevalidator.run.TestOutcome;
import dev.exercisevalidator.run.TestResult;
import dev.exercisevalidator.run.VariantRunner;

/**
 * The execution checks of version 1:
 * <ol>
 *   <li><b>Solution check:</b> every test must pass on the reference solution, run exactly like Artemis runs it.</li>
 *   <li><b>Flakiness check:</b> the same tests, N more times in random order, must give the same result.</li>
 *   <li><b>Alternatives check:</b> every test must also pass on the other correct solutions.</li>
 *   <li><b>Buggy variants:</b> identify tutor-written mistakes that pass a complete grading run.</li>
 * </ol>
 * These checks compare JUnit results of real runs. Nothing here inspects test source code.
 */
public final class Validator {

    /** {@code name()} is what JUnit writes for a plain test method, {@code name[Class]} what Ares' structural tests write. */
    private static final int MAX_EVIDENCE_PER_KIND = 3;
    private static final Pattern USUAL_TEST_NAME = Pattern.compile("[A-Za-z_$][\\w$]*(\\(\\)|\\[[\\w$.]+])");

    private final VariantRunner runner;
    private final Consumer<String> progress;

    public Validator(VariantRunner runner, Consumer<String> progress) {
        this.runner = runner;
        this.progress = progress;
    }

    /** @param mutation whether to run the mutation check (it is the slowest of the four) */
    public Report validate(Exercise exercise, int shuffledRuns, long baseSeed, boolean mutation)
            throws IOException, InterruptedException {
        return validate(exercise, shuffledRuns, baseSeed, mutation, List.of());
    }

    /** Expected IDs come from an independent inventory, in the form {@code class#XML invocation name}. */
    public Report validate(Exercise exercise, int shuffledRuns, long baseSeed, boolean mutation,
                           List<String> expectedTests) throws IOException, InterruptedException {
        List<String> problems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (expectedTests.isEmpty()) {
            warnings.add("Test inventory uses observed executions only. Tests absent from every run require an independent expected list.");
        }
        Set<String> known = new LinkedHashSet<>(expectedTests);
        RunResult solution = null;
        MutationResult mutationResult = null;
        List<RunResult> shuffled = new ArrayList<>();

        try (VariantRunner.Session session = runner.open(exercise, "solution")) {
            progress.accept("solution: clean test");
            solution = session.run();
            accountForRun(known, problems, solution);
            if (solution.ran()) {
                // Non-neighbouring seeds avoid repeatedly drawing the same first JUnit shuffle.
                SplittableRandom seeds = new SplittableRandom(baseSeed);
                for (int i = 0; i < shuffledRuns; i++) {
                    long seed = seeds.nextLong() & Long.MAX_VALUE;
                    progress.accept("solution: random order " + (i + 1) + "/" + shuffledRuns + " (seed " + seed + ")");
                    RunResult run;
                    try {
                        run = session.rerunShuffled(seed);
                    } catch (IOException ex) {
                        run = failedRun("solution", seed, ex);
                    }
                    if (!run.ran()) {
                        // A run without results says nothing about any single test: one problem line, no verdicts.
                        problems.add("Flakiness check stopped after " + i + " of " + shuffledRuns + " runs: seed " + seed
                                + " had an incomplete build (" + run.build() + "). " + firstLine(run.problem()));
                        break;
                    }
                    shuffled.add(run);
                    accountForRun(known, problems, run);
                }
                if (mutation) {
                    mutationResult = mutate(session, solution, known);
                }
            }
        } catch (IOException ex) {
            if (solution == null) {
                solution = failedRun("solution", null, ex);
            } else {
                problems.add("Could not close the solution work folder. " + ex.getMessage());
            }
        }

        List<RunResult> alternatives = new ArrayList<>();
        if (solution.ran()) {
            for (String variant : exercise.alternatives()) {
                RunResult run = runVariant(exercise, variant);
                if (!run.ran()) {
                    problems.add(variant + " could not be run (" + run.build() + "), so it was left out. " + firstLine(run.problem()));
                    continue;
                }
                alternatives.add(run);
                accountForRun(known, problems, run);
            }
        }
        if (!expectedTests.isEmpty()) {
            Set<String> extras = new LinkedHashSet<>(known);
            extras.removeAll(expectedTests);
            if (!extras.isEmpty()) {
                problems.add("Observed tests outside the expected list: " + String.join(", ", extras));
            }
        }
        warnings.addAll(nameWarnings(solution));
        List<Verdict> verdicts = new ArrayList<>();
        for (String test : known) {
            verdicts.add(judge(test, solution, shuffled, alternatives));
        }
        if (!solution.ran()) {
            String fatal = "The suite could not be run on the solution (" + solution.build() + ").\n" + solution.problem();
            return new Report(exercise.name(), solution.javaVersion(), List.of(), 0, baseSeed, verdicts, problems,
                    warnings, null, List.of(), fatal, 0);
        }
        List<RunResult> references = new ArrayList<>(List.of(solution));
        references.addAll(shuffled);
        List<BuggyResult> buggy = checkBuggy(exercise, references, alternatives, known, problems, warnings);
        int referencePasses = 0;
        for (String id : known) {
            if (solution.outcomeOf(id) == TestOutcome.PASS) {
                referencePasses++;
            }
        }
        return new Report(exercise.name(), solution.javaVersion(), alternatives.stream().filter(RunResult::ran)
                .map(RunResult::variant).toList(), (int) shuffled.stream().filter(RunResult::ran).count(), baseSeed,
                verdicts, problems, warnings, mutationResult, buggy, null, referencePasses);
    }

    private static void accountForRun(Set<String> known, List<String> problems, RunResult run) {
        run.tests().forEach(t -> known.add(t.qualifiedName()));
        if (run.ran() && run.unattributedJvmExit()) {
            problems.add(run.variant() + (run.seed() == null ? "" : " seed " + run.seed())
                    + ": the test JVM was killed without an attributable timeout.");
        }
    }

    private static RunResult failedRun(String variant, Long seed, IOException ex) {
        return new RunResult(variant, seed, RunResult.Build.FAILED, false, null, List.of(), ex.getMessage());
    }

    private RunResult runVariant(Exercise exercise, String variant) throws InterruptedException {
        progress.accept(variant + ": clean test");
        try (VariantRunner.Session session = runner.open(exercise, variant)) {
            return session.run();
        } catch (IOException ex) {
            return failedRun(variant, null, ex);
        }
    }

    private List<BuggyResult> checkBuggy(Exercise exercise, List<RunResult> references, List<RunResult> alternatives,
                                         Set<String> known, List<String> problems, List<String> warnings)
            throws InterruptedException, IOException {
        List<String> variants = exercise.buggyVariants();
        if (variants.isEmpty()) {
            return List.of();
        }
        BuggyCheck check = new BuggyCheck(references, alternatives, known);
        List<BuggyResult> results = new ArrayList<>();
        for (String variant : variants) {
            RunResult run = runVariant(exercise, variant);
            BugDescription description = BugDescription.EMPTY;
            try {
                description = BugDescription.read(exercise.root().resolve(run.variant()));
            } catch (IOException ex) {
                warnings.add(run.variant() + ": could not read bug.properties. " + ex.getMessage());
            }
            BuggyResult result = check.judge(run, description);
            results.add(result);
            if (result.problem() != null) {
                problems.add(run.variant() + ": " + result.problem());
            }
        }
        return results;
    }

    private MutationResult mutate(VariantRunner.Session session, RunResult solution, Set<String> known)
            throws InterruptedException {
        long notPassing = known.stream().filter(id -> solution.outcomeOf(id) != TestOutcome.PASS).count();
        if (notPassing > 0 || solution.jvmKilled() || known.isEmpty()) {
            String reason = notPassing > 0 ? notPassing + (notPassing == 1 ? " test does" : " tests do")
                    + " not have a unique passing result on the solution." : "The reference run was incomplete.";
            return MutationResult.skipped(reason + " PIT needs a green suite to start from.");
        }
        progress.accept("solution: mutation testing with PIT");
        try {
            return session.mutate();
        } catch (IOException ex) {
            return MutationResult.failed("Could not obtain the PIT report. " + ex.getMessage());
        }
    }

    static Verdict judge(String test, RunResult solution, List<RunResult> shuffled, List<RunResult> alternatives) {
        List<String> evidence = new ArrayList<>();
        TestOutcome reference = observe(test, solution, evidence);
        // A kill that is pinned on one hanging test only costs the tests after it (they are missing, so NOT_RUN).
        // Only a kill nobody can explain makes the whole run unusable.
        boolean incomplete = isIncomplete(reference) || solution.unattributedJvmExit();
        boolean shuffledFailure = false;
        int shuffledPasses = 0;
        List<String> seedLines = new ArrayList<>();
        for (RunResult run : shuffled) {
            TestOutcome observed = observe(test, run, seedLines);
            incomplete |= isIncomplete(observed) || run.unattributedJvmExit();
            shuffledFailure |= isFailure(observed);
            if (observed == TestOutcome.PASS) {
                shuffledPasses++;
            }
        }
        if (shuffledFailure || (isFailure(reference) && shuffledPasses > 0)) {
            evidence.add("passed in " + shuffledPasses + " of " + shuffled.size() + " random-order runs");
        }
        // The first few seeds are enough to replay the failure; ten near-identical lines per test bury the report.
        seedLines.stream().limit(MAX_EVIDENCE_PER_KIND).forEach(evidence::add);
        if (seedLines.size() > MAX_EVIDENCE_PER_KIND) {
            evidence.add("... and " + (seedLines.size() - MAX_EVIDENCE_PER_KIND) + " more runs");
        }
        boolean alternativeFailure = false;
        for (RunResult run : alternatives) {
            TestOutcome observed = observe(test, run, evidence);
            incomplete |= isIncomplete(observed) || run.unattributedJvmExit();
            alternativeFailure |= isFailure(observed);
        }

        Status status;
        if (isFailure(reference) || reference == TestOutcome.SKIPPED) {
            // A test that is skipped on the solution earns no points for a correct submission either (D28).
            status = Status.REJECTED;
        } else if (isIncomplete(reference)) {
            status = Status.NOT_CHECKED;
        } else if (shuffledFailure) {
            status = Status.FLAKY;
        } else if (alternativeFailure) {
            status = Status.OVER_SPECIFIED;
        } else if (incomplete) {
            status = Status.NOT_CHECKED;
        } else {
            status = Status.OK;
            evidence.add("Passed on the solution. Random-order runs passed: " + shuffledPasses
                    + ". Correct alternatives passed: " + alternatives.size() + ".");
        }
        return new Verdict(test, status, List.copyOf(evidence), incomplete);
    }

    private static boolean isFailure(TestOutcome outcome) {
        return outcome == TestOutcome.FAIL || outcome == TestOutcome.TIMEOUT;
    }

    private static boolean isIncomplete(TestOutcome outcome) {
        return outcome != TestOutcome.PASS && !isFailure(outcome);
    }

    /** Preserve partial XML as evidence even when the build cannot support a verdict about this test. */
    private static TestOutcome observe(String test, RunResult run, List<String> evidence) {
        List<TestResult> rows = run.matches(test);
        TestOutcome outcome = run.outcomeOf(rows);
        if (outcome == TestOutcome.PASS && !run.unattributedJvmExit()) {
            return outcome;
        }
        String detail;
        if (rows.isEmpty()) {
            detail = "NOT RUN (missing from the results" + (run.jvmKilled() ? ", test JVM killed" : "") + ")";
        } else if (rows.size() > 1) {
            detail = "AMBIGUOUS (" + rows.size() + " results for the same identity: "
                    + String.join(", ", rows.stream().map(Validator::describe).toList()) + ")";
        } else if (rows.get(0).className().isBlank() || rows.get(0).name().isBlank()) {
            detail = "AMBIGUOUS (missing XML class or invocation name): " + describe(rows.get(0));
        } else {
            detail = describe(rows.get(0));
        }
        if (run.unattributedJvmExit()) {
            detail += " (test JVM exit could not be attributed)";
        }
        if (!run.ran()) {
            detail = "Build " + run.build() + " (" + firstLine(run.problem()) + "). " + detail;
        }
        evidence.add(run.seed() == null ? detail + " on " + (run.variant().equals("solution") ? "the solution" : run.variant())
                : "seed " + run.seed() + ": " + detail);
        return outcome;
    }

    private static String describe(TestResult result) {
        return result.message().isEmpty() ? result.outcome().name() : result.outcome() + " (" + result.message() + ")";
    }

    /** Artemis binds tasks and points to test names, so odd or ambiguous names are worth a warning. */
    static List<String> nameWarnings(RunResult solution) {
        List<String> warnings = new ArrayList<>();
        Map<String, Set<String>> classesByName = new LinkedHashMap<>();
        for (TestResult test : solution.tests()) {
            classesByName.computeIfAbsent(test.name(), n -> new LinkedHashSet<>()).add(test.className());
        }
        classesByName.forEach((name, classes) -> {
            if (!USUAL_TEST_NAME.matcher(name).matches()) {
                warnings.add("Test name \"" + name + "\" is not of the form methodName(). "
                        + "Check that the Artemis task binding uses exactly this name.");
            }
            if (classes.size() > 1) {
                warnings.add("Test name \"" + name + "\" exists in " + classes.size() + " classes (" + String.join(", ", classes)
                        + "). Check the Artemis task binding. The report shows each class separately.");
            }
        });
        return warnings;
    }

    private static String firstLine(String text) {
        return text == null ? "" : text.strip().lines().findFirst().orElse("");
    }
}
