package com.example.arraymax;

import java.util.Arrays;

public final class ArrayMax {

    private ArrayMax() {
    }

    public static int max(int[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Array must not be empty");
        }
        Arrays.sort(values);
        return values[values.length - 1];
    }
}
