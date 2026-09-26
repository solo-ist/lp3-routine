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
    public void timeOfDayFollowsTheClockStyle() {
        java.time.ZoneId ny = java.time.ZoneId.of("America/New_York");
        long t = java.time.ZonedDateTime.of(2026, 9, 26, 19, 52, 30, 0, ny).toInstant().toEpochMilli();
        assertEquals("19:52", Format.timeOfDay(t, ny, true));
        assertEquals("7:52 pm", Format.timeOfDay(t, ny, false));
        long justAfterMidnight = java.time.ZonedDateTime.of(2026, 9, 26, 0, 5, 0, 0, ny).toInstant().toEpochMilli();
        assertEquals("12:05 am", Format.timeOfDay(justAfterMidnight, ny, false));
    }

    @Test
    public void plansReadAsMinutes() {
        assertEquals("25 min", Format.minutes(25 * 60_000));
        assertEquals("1 h 5 min", Format.minutes(65 * 60_000));
        assertEquals("45 s", Format.minutes(45_000));
    }
}
