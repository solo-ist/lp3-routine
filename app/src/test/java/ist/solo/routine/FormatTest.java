package ist.solo.routine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FormatTest {
    @Test
    public void countdownRoundsUpSoItNeverShowsZeroEarly() {
        assertEquals("0:01", Format.clock(1));
        assertEquals("5:00", Format.clock(300_000));
        assertEquals("4:59", Format.clock(299_000));
        assertEquals("1:02:03", Format.clock(3_723_000));
    }

    @Test
    public void overtimeAndCountUpRoundDown() {
        assertEquals("+2:14", Format.over(134_999));
        assertEquals("0:00", Format.elapsed(999));
    }

    @Test
    public void plansReadAsMinutes() {
        assertEquals("25 min", Format.minutes(25 * 60_000));
        assertEquals("1 h 5 min", Format.minutes(65 * 60_000));
        assertEquals("45 s", Format.minutes(45_000));
    }
}
