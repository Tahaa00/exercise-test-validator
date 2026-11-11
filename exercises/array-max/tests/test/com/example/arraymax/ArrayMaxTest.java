package com.example.arraymax;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sample suite with two deliberate flaws, for the validator to find:
 * <ul>
 *   <li>{@link #emptyArrayIsRejected()} is over-specified: it demands an exact message the statement does not require.</li>
 *   <li>{@link #nullArrayIsRejected()} is weak: any RuntimeException passes, including the NullPointerException
 *       of a solution that forgot the null check.</li>
 *   <li>{@link #arrayIsNotModified()} is a correct test, but it also passes on the template, so it gives free points
 *       unless it is grouped with a test that needs a working max.</li>
 * </ul>
 */
class ArrayMaxTest {

    private Method max;

    @BeforeEach
    void findMethod() {
        Class<?> arrayMax = TestUtils.findClass("com.example.arraymax.ArrayMax");
        max = TestUtils.findStaticMethod(arrayMax, "max", int[].class);
    }

    private int callMax(int... values) {
        return (int) TestUtils.invokeStatic(max, (Object) values);
    }

    @Test
    @DisplayName("Returns the largest of mixed values")
    void maxOfMixedValues() {
        assertEquals(9, callMax(3, 9, 1, 4), "max([3, 9, 1, 4]) should be 9.");
    }

    @Test
    @DisplayName("Works when all values are negative")
    void maxOfNegativeValues() {
        assertEquals(-2, callMax(-7, -2, -5), "max([-7, -2, -5]) should be -2.");
    }

    @Test
    @DisplayName("Works for a single value")
    void maxOfSingleValue() {
        assertEquals(42, callMax(42), "max([42]) should be 42.");
    }

    @Test
    @DisplayName("Finds the maximum at the last position")
    void maxAtLastPosition() {
        assertEquals(8, callMax(1, 2, 8), "max([1, 2, 8]) should be 8. Does your loop reach the last element?");
    }

    @Test
    @DisplayName("Does not modify the input array")
    void arrayIsNotModified() {
        int[] values = {5, 1, 3};
        callMax(values);
        assertArrayEquals(new int[] {5, 1, 3}, values, "max must not change the array it is given.");
    }

    // FLAW (over-specified): the statement says the message is not graded, but this test compares it exactly.
    @Test
    @DisplayName("Rejects an empty array")
    void emptyArrayIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> callMax(),
                "An empty array should cause an IllegalArgumentException.");
        assertEquals("Array must not be empty", e.getMessage(), "Unexpected exception message.");
    }

    // FLAW (weak): accepts any RuntimeException, so a missing null check (NullPointerException) still passes.
    @Test
    @DisplayName("Rejects null")
    void nullArrayIsRejected() {
        assertThrows(RuntimeException.class, () -> TestUtils.invokeStatic(max, (Object) null),
                "null should be rejected.");
    }
}
