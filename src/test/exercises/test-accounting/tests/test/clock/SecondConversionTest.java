package clock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SecondConversionTest {
    @Test
    void oneHour() {
        // A deliberately incorrect expectation in a different class with the same method name.
        assertEquals(61, Minutes.inHours(1));
    }
}
