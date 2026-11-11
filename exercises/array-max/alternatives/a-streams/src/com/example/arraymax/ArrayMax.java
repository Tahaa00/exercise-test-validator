package com.example.arraymax;

import java.util.Arrays;

/** Correct alternative: streams instead of a loop. Same message as the solution. */
public final class ArrayMax {

    private ArrayMax() {
    }

    public static int max(int[] values) {
        if (values == null) {
            throw new IllegalArgumentException("Array must not be empty");
        }
        return Arrays.stream(values)
                .max()
                .orElseThrow(() -> new IllegalArgumentException("Array must not be empty"));
    }
}
