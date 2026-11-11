package com.example.arraymax;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import de.tum.cit.ase.ares.api.StrictTimeout;
import de.tum.cit.ase.ares.api.jupiter.Public;

/**
 * Behavior tests in Ares style: @Public, a security policy, and a strict 2-second timeout per test.
 * Same deliberate flaws as the plain JUnit sample:
 * {@link #emptyArrayIsRejected()} is over-specified, {@link #nullArrayIsRejected()} is weak.
 */
@Public
@de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml")
class ArrayMaxBehaviorTest {

    private Method maxMethod() {
        try {
            return Class.forName("com.example.arraymax.ArrayMax").getMethod("max", int[].class);
        } catch (ReflectiveOperationException e) {
            return fail("ArrayMax.max(int[]) was not found.", e);
        }
    }

    private int max(int[] values) {
        try {
            return (int) maxMethod().invoke(null, (Object) values);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            return fail("max threw " + e.getCause(), e.getCause());
        } catch (IllegalAccessException e) {
            return fail("ArrayMax.max(int[]) could not be called.", e);
        }
    }

    @Test
    @StrictTimeout(2)
    void maxOfMixedValues() {
        assertEquals(9, max(new int[] {3, 9, 1, 4}), "max([3, 9, 1, 4]) should be 9.");
    }

    @Test
    @StrictTimeout(2)
    void maxOfNegativeValues() {
        assertEquals(-2, max(new int[] {-7, -2, -5}), "max([-7, -2, -5]) should be -2.");
    }

    @Test
    @StrictTimeout(2)
    void maxOfSingleValue() {
        assertEquals(42, max(new int[] {42}), "max([42]) should be 42.");
    }

    @Test
    @StrictTimeout(2)
    void maxAtLastPosition() {
        assertEquals(8, max(new int[] {1, 2, 8}), "max([1, 2, 8]) should be 8. Does your loop reach the last element?");
    }

    @Test
    @StrictTimeout(2)
    void arrayIsNotModified() {
        int[] values = {5, 1, 3};
        max(values);
        assertArrayEquals(new int[] {5, 1, 3}, values, "max must not change the array it is given.");
    }

    // FLAW (over-specified): the statement says the message is not graded, but this test compares it exactly.
    @Test
    @StrictTimeout(2)
    void emptyArrayIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> max(new int[0]),
                "An empty array should cause an IllegalArgumentException.");
        assertEquals("Array must not be empty", e.getMessage(), "Unexpected exception message.");
    }

    // FLAW (weak): accepts any RuntimeException, so a missing null check (NullPointerException) still passes.
    @Test
    @StrictTimeout(2)
    void nullArrayIsRejected() {
        assertThrows(RuntimeException.class, () -> max(null), "null should be rejected.");
    }
}
