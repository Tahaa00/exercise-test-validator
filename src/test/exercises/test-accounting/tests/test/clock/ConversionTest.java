package clock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class ConversionTest {
    @Test
    void oneHour() {
        assertEquals(60, Minutes.inHours(1));
    }

    @Disabled("Pending tutor review")
    @Test
    void twoHours() {
        assertEquals(120, Minutes.inHours(2));
    }

    @Tag("deferred")
    @Test
    void zeroHours() {
        assertEquals(0, Minutes.inHours(0));
    }
}
