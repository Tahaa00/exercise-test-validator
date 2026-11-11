package com.example.counter;

public final class Counter {

    private int value;

    public int next() {
        while (value >= 0) {
            value = 0; // never ends
        }
        return value;
    }

    public int value() {
        return value;
    }
}
