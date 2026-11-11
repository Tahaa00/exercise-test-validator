package com.example.arraymax;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import de.tum.in.test.api.StrictTimeout;
import de.tum.in.test.api.WhitelistPath;
import de.tum.in.test.api.jupiter.Public;

/**
 * Behavior tests in Ares 1 style: @Public puts every test under the Ares security manager.
 * {@link #nullArrayIsRejected()} is weak on purpose, so the mutation check has a survivor to show.
 */
@Public
@WhitelistPath("")
@StrictTimeout(2)
class ArrayMaxBehaviorTest {

    @Test
    void maxOfMixedValues() {
        assertEquals(9, ArrayMax.max(new int[] {3, 9, 1, 4}), "max([3, 9, 1, 4]) should be 9.");
    }

    @Test
    void maxOfNegativeValues() {
        assertEquals(-2, ArrayMax.max(new int[] {-7, -2, -5}), "max([-7, -2, -5]) should be -2.");
    }

    @Test
    void maxOfSingleValue() {
        assertEquals(42, ArrayMax.max(new int[] {42}), "max([42]) should be 42.");
    }

    @Test
    void maxAtLastPosition() {
        assertEquals(8, ArrayMax.max(new int[] {1, 2, 8}), "max([1, 2, 8]) should be 8.");
    }

    @Test
    void arrayIsNotModified() {
        int[] values = {5, 1, 3};
        ArrayMax.max(values);
        assertArrayEquals(new int[] {5, 1, 3}, values, "max must not change the array it is given.");
    }

    @Test
    void emptyArrayIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ArrayMax.max(new int[0]),
                "An empty array should cause an IllegalArgumentException.");
    }

    // Weak: accepts any RuntimeException, so a missing null check (NullPointerException) still passes.
    @Test
    void nullArrayIsRejected() {
        assertThrows(RuntimeException.class, () -> ArrayMax.max(null), "null should be rejected.");
    }
}
