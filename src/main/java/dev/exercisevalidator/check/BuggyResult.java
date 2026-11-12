package dev.exercisevalidator.check;

import java.util.List;

import dev.exercisevalidator.run.BugDescription;
import dev.exercisevalidator.run.RunResult;

/** The grading suite's outcome on one tutor-written, deliberately wrong solution. */
public record BuggyResult(RunResult run, BugDescription description, Status status, String intendedOutcome,
                          List<String> catchingTests, String problem) {
    public enum Status { MISSED, CAUGHT, INCONCLUSIVE }
}
