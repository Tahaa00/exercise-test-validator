package com.example.arraymax;

/**
 * Valid alternative: a while loop that only moves on when the current element is not a new maximum.
 * Kept as a fixture for mutation testing: changing {@code >} to {@code >=} turns this loop into an endless one.
 */
public final class ArrayMax {

    private ArrayMax() {
    }

    public static int max(int[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Array must not be empty");
        }
        int max = values[0];
        int i = 1;
        while (i < values.length) {
            if (values[i] > max) {
                max = values[i];
            } else {
                i++;
            }
        }
        return max;
    }
}
