package com.example.counter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CounterTest {

    // FLAW (flaky): one counter shared by all tests, so each test sees what the others left behind.
    private static final Counter SHARED = new Counter();

    @Test
    void startsAtZero() {
        assertEquals(0, SHARED.value(), "A new counter should start at 0.");
    }

    @Test
    void nextAddsOne() {
        assertEquals(SHARED.value() + 1, SHARED.next(), "next() should return the old value plus one.");
    }
}
