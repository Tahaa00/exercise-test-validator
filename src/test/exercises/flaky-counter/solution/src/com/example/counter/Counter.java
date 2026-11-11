package com.example.counter;

public final class Counter {

    private int value;

    public int next() {
        value++;
        return value;
    }

    public int value() {
        return value;
    }
}
