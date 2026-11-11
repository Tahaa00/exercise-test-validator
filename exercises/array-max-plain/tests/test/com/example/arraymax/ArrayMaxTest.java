package com.example.arraymax;

import static com.example.arraymax.TestUtils.expectEquals;
import static com.example.arraymax.TestUtils.invokeStatic;
import static com.example.arraymax.TestUtils.thrownBy;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Style A sample suite: plain JUnit 5, reflection helpers, display names equal to the method names
 * (so Artemis' task binding by test name keeps working). Deliberate flaws for the validator to find:
 * <ul>
 *   <li>{@link #emptyArrayIsRejected()} is over-specified: it demands an exact message the statement does not require.</li>
 *   <li>{@link #nullArrayIsRejected()} is weak: any RuntimeException passes, including the NullPointerException
 *       of a solution that forgot the null check.</li>
 *   <li>{@link #arrayIsNotModified()} is a correct test, but it also passes on the template, so it gives free points
 *       unless it is grouped with a test that needs a working max.</li>
 * </ul>
 */
class ArrayMaxTest {

    private static int max(int... values) {
        return (int) invokeStatic("ArrayMax", "max", (Object) values);
    }

    @Test
    @DisplayName("maxOfMixedValues()")
    void maxOfMixedValues() {
        expectEquals(9, max(3, 9, 1, 4), "max([3, 9, 1, 4]) returned the wrong value.");
    }

    @Test
    @DisplayName("maxOfNegativeValues()")
    void maxOfNegativeValues() {
        expectEquals(-2, max(-7, -2, -5), "max([-7, -2, -5]) returned the wrong value. Did you start with 0?");
    }

    @Test
    @DisplayName("maxOfSingleValue()")
    void maxOfSingleValue() {
        expectEquals(42, max(42), "max([42]) returned the wrong value.");
    }

    @Test
    @DisplayName("maxAtLastPosition()")
    void maxAtLastPosition() {
        expectEquals(8, max(1, 2, 8), "max([1, 2, 8]) returned the wrong value. Does your loop reach the last element?");
    }

    @Test
    @DisplayName("arrayIsNotModified()")
    void arrayIsNotModified() {
        int[] values = {5, 1, 3};
        max(values);
        expectEquals("[5, 1, 3]", Arrays.toString(values), "max must not change the array it is given.");
    }

    // FLAW (over-specified): the statement says the message is not graded, but this test compares it exactly.
    @Test
    @DisplayName("emptyArrayIsRejected()")
    void emptyArrayIsRejected() {
        Throwable thrown = thrownBy("ArrayMax", "max", (Object) new int[0]);
        if (!(thrown instanceof IllegalArgumentException)) {
            fail("max([]) should throw an IllegalArgumentException, but threw " + thrown + ".");
        }
        expectEquals("Array must not be empty", thrown.getMessage(), "max([]) threw the wrong message.");
    }

    // FLAW (weak): accepts any RuntimeException, so a missing null check (NullPointerException) still passes.
    @Test
    @DisplayName("nullArrayIsRejected()")
    void nullArrayIsRejected() {
        Throwable thrown = thrownBy("ArrayMax", "max", (Object) null);
        if (!(thrown instanceof RuntimeException)) {
            fail("max(null) should throw an exception, but threw " + thrown + ".");
        }
    }
}
