package ist.solo.routine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.ZoneId;

public class RunStateTest {
    private static final long MIN = 60_000;
    private static final ZoneId NY = ZoneId.of("America/New_York");
    private final Now t0 = new Now(1_000_000, 1_700_000_000_000L, 1);

    private static RoutineSpec routine(int... minutes) {
        RoutineSpec r = new RoutineSpec();
        r.id = 42;
        r.name = "morning";
        for (int i = 0; i < minutes.length; i++) r.steps.add(new RoutineSpec.Step("s" + (i + 1), minutes[i] * 60));
        return r;
    }

    private RunState start(RoutineSpec r) {
        return RunState.start("run", r, t0, NY, Day.DEFAULT_BOUNDARY_MINUTES);
    }

    @Test
    public void autoNextAdvancesAtTheDueMomentNotWhenNoticed() {
        RunState r = start(routine(2, 5));
        // Noticed 40 s late.
        Now late = t0.plus(2 * MIN + 40_000);
        assertTrue(r.advanceIfDue(late, true));
        assertEquals(1, r.current);
        assertEquals(2 * MIN, r.steps.get(0).activeMs);
        assertEquals(RunState.Outcome.AUTO, r.steps.get(0).outcome);
        // The second step started when the first was due, so 40 s are gone.
        assertEquals(5 * MIN - 40_000, r.timer.remainingMs(late));
    }

    @Test
    public void onlyTheFirstCallerBuzzes() {
        RunState r = start(routine(1, 1));
        Now due = t0.plus(MIN);
        assertTrue(r.advanceIfDue(due, true));
        assertFalse(r.advanceIfDue(due, true));
    }

    @Test
    public void autoNextOffBuzzesOnceThenCountsOvertime() {
        RunState r = start(routine(1, 1));
        assertTrue(r.advanceIfDue(t0.plus(MIN), false));
        assertFalse(r.advanceIfDue(t0.plus(2 * MIN), false));
        assertEquals(0, r.current);
        assertEquals(-MIN, r.timer.remainingMs(t0.plus(2 * MIN)));
    }

    @Test
    public void addingTimeAfterTheBuzzRearmsIt() {
        RunState r = start(routine(1, 1));
        r.advanceIfDue(t0.plus(MIN), false);
        r.addTime(MIN, t0.plus(MIN));
        assertFalse(r.alerted);
        assertTrue(r.advanceIfDue(t0.plus(2 * MIN), false));
    }

    @Test
    public void manyOverdueStepsCatchUpInOneCall() {
        RunState r = start(routine(1, 1, 1));
        assertTrue(r.advanceIfDue(t0.plus(10 * MIN), true));
        assertFalse(r.running());
        assertEquals(RunState.Status.COMPLETE, r.status);
        assertEquals(t0.wall + 3 * MIN, r.endedAt);
    }

    @Test
    public void untimedStepWaitsForDone() {
        RunState r = start(routine(0, 1));
        assertFalse(r.advanceIfDue(t0.plus(60 * MIN), true));
        r.done(t0.plus(3 * MIN));
        assertEquals(1, r.current);
        assertEquals(3 * MIN, r.steps.get(0).activeMs);
    }

    @Test
    public void skippedStepsFailAFullThreshold() {
        RunState r = start(routine(1, 1, 1, 1));
        r.skip(t0.plus(10_000));
        for (int i = 0; i < 3; i++) r.done(t0.plus(20_000 + i));
        assertEquals(RunState.Status.PARTIAL, r.status);
        assertEquals(3, r.counted());
    }

    @Test
    public void skippedStepsCanMeetALowerThreshold() {
        RoutineSpec spec = routine(1, 1, 1, 1);
        spec.threshold = 75;
        RunState r = start(spec);
        r.skip(t0.plus(10_000));
        for (int i = 0; i < 3; i++) r.done(t0.plus(20_000 + i));
        assertEquals(RunState.Status.COMPLETE, r.status);
    }

    @Test
    public void endingEarlyMarksTheRestUnreached() {
        RunState r = start(routine(5, 5, 5));
        r.done(t0.plus(4 * MIN));
        r.end(t0.plus(6 * MIN));
        assertEquals(RunState.Status.PARTIAL, r.status);
        assertEquals(RunState.Outcome.ENDED, r.steps.get(1).outcome);
        assertEquals(2 * MIN, r.steps.get(1).activeMs);
        assertEquals(RunState.Outcome.UNREACHED, r.steps.get(2).outcome);
        assertEquals(6 * MIN, r.actualMs());
        assertEquals(15 * MIN, r.plannedMs());
    }

    @Test
    public void nothingHappensAfterTheRunIsOver() {
        RunState r = start(routine(1));
        r.done(t0.plus(MIN));
        assertFalse(r.running());
        r.skip(t0.plus(2 * MIN));
        r.end(t0.plus(3 * MIN));
        assertEquals(RunState.Status.COMPLETE, r.status);
        assertNull(r.next());
    }

    @Test
    public void pausingStopsTheStepFromEnding() {
        RunState r = start(routine(1, 1));
        r.pause(t0.plus(30_000));
        assertFalse(r.advanceIfDue(t0.plus(10 * MIN), true));
        assertEquals(0, r.current);
    }
}
