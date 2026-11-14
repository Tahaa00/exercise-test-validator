package com.example.arraymax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/** Independent checks of the two sample bugs. The exercise's grading suite never sees these tests. */
class ArrayMaxWitnessTest {
    @Test
    void nullWitness() {
        assertThrows(IllegalArgumentException.class, () -> ArrayMax.max(null));
    }

    @Test
    void inputWitness() {
        int[] input = {2, 1};
        int[] before = input.clone();
        assertEquals(2, ArrayMax.max(input));
        assertEquals(Arrays.toString(before), Arrays.toString(input));
    }
}
