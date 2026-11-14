package com.example.arraymax;

public final class ArrayMax {

    private ArrayMax() {
    }

    public static int max(int[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("Array must not be empty");
        }
        int max = values[0];
        for (int i = 1; i < values.length; i++) {
            if (values[i] > max) {
                max = values[i];
            }
        }
        return max;
    }
}
