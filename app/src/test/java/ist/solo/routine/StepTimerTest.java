package ist.solo.routine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StepTimerTest {
    private static final long MIN = 60_000;
    private final Now t0 = new Now(1_000_000, 1_700_000_000_000L, 7);

    @Test
    public void screenOffForThreeMinutesLeavesTwo() {
        // R-P0-4: 5:00 step, 3 minutes with nobody looking, reopen → 2:00.
        StepTimer t = StepTimer.start(5 * MIN, t0);
        assertEquals(2 * MIN, t.remainingMs(t0.plus(3 * MIN)));
    }

    @Test
    public void pausesDontCount() {
        StepTimer t = StepTimer.start(5 * MIN, t0);
        t.pause(t0.plus(MIN));
        assertEquals(4 * MIN, t.remainingMs(t0.plus(10 * MIN)));
        t.resume(t0.plus(10 * MIN));
        assertEquals(3 * MIN, t.remainingMs(t0.plus(11 * MIN)));
        assertFalse(t.due(t0.plus(11 * MIN)));
    }

    @Test
    public void addedTimeExtendsTheDeadline() {
        StepTimer t = StepTimer.start(MIN, t0);
        t.addTime(5 * MIN);
        assertEquals(t0.elapsed + 6 * MIN, t.deadlineElapsed(t0));
        assertEquals(6 * MIN - 30_000, t.remainingMs(t0.plus(30_000)));
    }

    @Test
    public void removingTimeNeverMakesThePlanNegative() {
        StepTimer t = StepTimer.start(MIN, t0);
        t.addTime(-5 * MIN);
        assertEquals(0, t.plannedMs + t.extraMs);
    }

    @Test
    public void overtimeIsNegativeRemaining() {
        StepTimer t = StepTimer.start(MIN, t0);
        assertEquals(-30_000, t.remainingMs(t0.plus(90_000)));
        assertTrue(t.due(t0.plus(90_000)));
    }

    @Test
    public void untimedStepsCountUpAndAreNeverDue() {
        StepTimer t = StepTimer.start(0, t0);
        assertEquals(45 * MIN, t.activeMs(t0.plus(45 * MIN)));
        assertFalse(t.due(t0.plus(45 * MIN)));
    }

    @Test
    public void rebootFallsBackToTheWallClock() {
        StepTimer t = StepTimer.start(10 * MIN, t0);
        // Rebooted: elapsed restarts near zero, boot count moves on, and four
        // minutes of wall time have passed (including the time switched off).
        Now after = new Now(20_000, t0.wall + 4 * MIN, 8);
        assertEquals(6 * MIN, t.remainingMs(after));
        // And it keeps counting correctly on the new boot's clock.
        assertEquals(5 * MIN, t.remainingMs(new Now(20_000 + MIN, after.wall + MIN, 8)));
    }

    @Test
    public void rebootWhilePausedStaysPaused() {
        StepTimer t = StepTimer.start(10 * MIN, t0);
        t.pause(t0.plus(2 * MIN));
        Now after = new Now(5_000, t0.wall + 30 * MIN, 8);
        assertEquals(8 * MIN, t.remainingMs(after));
        t.resume(after);
        assertEquals(7 * MIN, t.remainingMs(new Now(5_000 + MIN, after.wall + MIN, 8)));
    }
}
