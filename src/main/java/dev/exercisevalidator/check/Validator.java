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
    private static final Pattern USUAL_TEST_NAME = Pattern.compile("[A-Za-z_$][\\w$]*(\\(\\)|\\[[\\w$.]+])");
    private static final int MAX_EVIDENCE_PER_KIND = 3;

    private final VariantRunner runner;
    private final Consumer<String> progress;

    public Validator(VariantRunner runner, Consumer<String> progress) {
        this.runner = runner;
        this.progress = progress;
    }

    /** @param mutation whether to run the mutation check (it is the slowest of the four) */
    public Report validate(Exercise exercise, int shuffledRuns, long baseSeed, boolean mutation)
            throws IOException, InterruptedException {
        List<String> problems = new ArrayList<>();
        RunResult solution;
        MutationResult mutationResult = null;
        List<RunResult> shuffled = new ArrayList<>();
        List<RunResult> referenceRuns = new ArrayList<>();

        try (VariantRunner.Session session = runner.open(exercise, "solution")) {
            progress.accept("solution: clean test");
            solution = session.run();
            referenceRuns.add(solution);
            if (!solution.ran()) {
                // Without a solution run there is nothing to compare against, so stop here.
                return new Report(exercise.name(), solution.javaVersion(), List.of(), 0, baseSeed, List.of(), List.of(),
                        List.of(), null, "The suite could not be run on the solution (" + solution.build() + ").\n" + solution.problem());
            }
            // Seeds are drawn from a generator instead of counting baseSeed, baseSeed + 1, ...: java.util.Random,
            // which JUnit shuffles with, gives nearly the same first shuffle for neighbouring seeds (seen in testing:
            // ten consecutive seeds produced one order). The same base seed still reproduces the same ten runs.
            SplittableRandom seeds = new SplittableRandom(baseSeed);
            for (int i = 0; i < shuffledRuns; i++) {
                long seed = seeds.nextLong() & Long.MAX_VALUE;
                progress.accept("solution: random order " + (i + 1) + "/" + shuffledRuns + " (seed " + seed + ")");
                RunResult run = session.rerunShuffled(seed);
                referenceRuns.add(run);
                if (!run.ran()) {
                    problems.add("Flakiness check stopped after " + i + " of " + shuffledRuns + " runs: seed " + seed
                            + " gave no test results (" + run.build() + "). " + firstLine(run.problem()));
                    break;
                }
                shuffled.add(run);
            }
            if (mutation) {
                // Same work folder as the solution run, so PIT mutates exactly the classes that were just tested.
                mutationResult = mutate(session, solution);
            }
        }

        List<RunResult> alternatives = new ArrayList<>();
        for (String variant : exercise.alternatives()) {
            try (VariantRunner.Session session = runner.open(exercise, variant)) {
                progress.accept(variant + ": clean test");
                RunResult run = session.run();
                if (run.ran()) {
                    alternatives.add(run);
                } else {
                    problems.add(variant + " could not be run (" + run.build() + "), so it was left out. " + firstLine(run.problem()));
                }
            }
        }

        List<Verdict> verdicts = new ArrayList<>();
        for (String test : allTestNames(solution, shuffled, alternatives)) {
            verdicts.add(judge(test, solution, shuffled, alternatives));
        }
        List<String> warnings = nameWarnings(solution);
        List<BuggyResult> buggy = checkBuggy(exercise, referenceRuns, alternatives, problems, warnings);
        return new Report(exercise.name(), solution.javaVersion(), alternatives.stream().map(RunResult::variant).toList(),
                shuffled.size(), baseSeed, verdicts, problems, warnings, mutationResult, buggy, null);
    }

    private List<BuggyResult> checkBuggy(Exercise exercise, List<RunResult> references, List<RunResult> alternatives,
                                         List<String> problems, List<String> warnings) throws InterruptedException, IOException {
        List<String> variants = exercise.buggyVariants();
        if (variants.isEmpty()) {
            return List.of();
        }
        BuggyCheck check = new BuggyCheck(references, alternatives);
        List<BuggyResult> results = new ArrayList<>();
        for (String variant : variants) {
            progress.accept(variant + ": clean test");
            RunResult run;
            try (VariantRunner.Session session = runner.open(exercise, variant)) {
                run = session.run();
            } catch (IOException ex) {
                run = new RunResult(variant, null, RunResult.Build.FAILED, false, null, List.of(), ex.getMessage());
            }
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

    private MutationResult mutate(VariantRunner.Session session, RunResult solution) throws IOException, InterruptedException {
        long notPassing = solution.tests().stream().filter(t -> t.outcome() != TestOutcome.PASS).count();
        if (notPassing > 0) {
            // PIT first runs the suite unchanged and stops if anything fails: a red test cannot tell a mutant from the original.
            return MutationResult.skipped(notPassing + (notPassing == 1 ? " test does" : " tests do")
                    + " not pass on the solution. PIT needs a green suite to start from.");
        }
        progress.accept("solution: mutation testing with PIT");
        return session.mutate();
    }

    /**
     * The expected test list is the union over all runs. A single run is not enough: when a test JVM is killed,
     * the tests after that point are simply absent from its XML.
     */
    private static Set<String> allTestNames(RunResult solution, List<RunResult> shuffled, List<RunResult> alternatives) {
        Set<String> names = new LinkedHashSet<>();
        List<RunResult> all = new ArrayList<>(List.of(solution));
        all.addAll(shuffled);
        all.addAll(alternatives);
        for (RunResult run : all) {
            run.tests().forEach(t -> names.add(t.name()));
        }
        return names;
    }

    static Verdict judge(String test, RunResult solution, List<RunResult> shuffled, List<RunResult> alternatives) {
        List<String> evidence = new ArrayList<>();
        long shuffledPasses = shuffled.stream().filter(r -> r.outcomeOf(test) == TestOutcome.PASS).count();

        // 1. Solution check. A test that does not pass on the reference is wrong whatever the other runs say.
        if (solution.outcomeOf(test) != TestOutcome.PASS) {
            evidence.add(describe(test, solution) + " on the solution");
            if (shuffledPasses > 0) {
                evidence.add("but passed in " + shuffledPasses + " of " + shuffled.size() + " random-order runs, so it is also unstable");
            }
            return new Verdict(test, Status.REJECTED, evidence);
        }

        // 2. Flakiness check. NOT_RUN is not counted as a differing result: it is the echo of another test's timeout.
        List<RunResult> differing = shuffled.stream().filter(r -> isObservedNonPass(r.outcomeOf(test))).toList();
        if (!differing.isEmpty()) {
            evidence.add("passed in " + shuffledPasses + " of " + shuffled.size() + " random-order runs");
            differing.stream().limit(MAX_EVIDENCE_PER_KIND)
                    .forEach(r -> evidence.add("seed " + r.seed() + ": " + describe(test, r)));
            if (differing.size() > MAX_EVIDENCE_PER_KIND) {
                evidence.add("... and " + (differing.size() - MAX_EVIDENCE_PER_KIND) + " more runs");
            }
        }

        // 3. Alternatives check.
        List<RunResult> failingAlternatives = alternatives.stream().filter(r -> isObservedNonPass(r.outcomeOf(test))).toList();
        failingAlternatives.forEach(r -> evidence.add(describe(test, r) + " on " + r.variant()));

        // Runs that never reached the test: no verdict from them, but say so instead of implying a pass.
        long unreachedShuffled = shuffled.stream().filter(r -> r.outcomeOf(test) == TestOutcome.NOT_RUN).count();
        if (unreachedShuffled > 0) {
            evidence.add("not run in " + unreachedShuffled + " of " + shuffled.size() + " random-order runs (test JVM killed earlier in the run)");
        }
        List<String> unreachedAlternatives = alternatives.stream()
                .filter(r -> r.outcomeOf(test) == TestOutcome.NOT_RUN).map(RunResult::variant).toList();
        if (!unreachedAlternatives.isEmpty()) {
            evidence.add("not run on " + String.join(", ", unreachedAlternatives) + " (test JVM killed earlier in the run, or the test is missing there)");
        }

        Status status;
        if (!differing.isEmpty()) {
            status = Status.FLAKY; // ranks above over-specified: an unstable test makes its other results unreliable
        } else if (!failingAlternatives.isEmpty()) {
            status = Status.OVER_SPECIFIED;
        } else if (unreachedShuffled > 0 || !unreachedAlternatives.isEmpty()) {
            status = Status.NOT_CHECKED;
        } else {
            status = Status.OK;
        }
        return new Verdict(test, status, evidence);
    }

    private static boolean isObservedNonPass(TestOutcome outcome) {
        return outcome != TestOutcome.PASS && outcome != TestOutcome.NOT_RUN;
    }

    private static String describe(String test, RunResult run) {
        TestResult result = run.find(test).orElse(null);
        if (result == null) {
            return "NOT RUN (missing from the results" + (run.jvmKilled() ? ", the test JVM was killed earlier" : "") + ")";
        }
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
                        + "). Results are keyed by name, so they are merged here.");
            }
        });
        return warnings;
    }

    private static String firstLine(String text) {
        return text == null ? "" : text.strip().lines().findFirst().orElse("");
    }
}
