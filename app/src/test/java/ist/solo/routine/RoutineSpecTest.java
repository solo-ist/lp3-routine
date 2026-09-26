package ist.solo.routine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RoutineSpecTest {

    private static RoutineSpec abc() {
        RoutineSpec r = new RoutineSpec();
        r.name = "r";
        for (String n : new String[] {"a", "b", "c"}) r.steps.add(new RoutineSpec.Step(n, 60));
        return r;
    }

    private static String order(RoutineSpec r) {
        StringBuilder sb = new StringBuilder();
        for (RoutineSpec.Step s : r.steps) sb.append(s.name);
        return sb.toString();
    }

    @Test
    public void movingAStepKeepsTheRestInOrder() {
        RoutineSpec r = abc();
        assertTrue(r.moveStep(2, 1));
        assertEquals("acb", order(r));
        assertTrue(r.moveStep(0, 1));
        assertEquals("cab", order(r));
    }

    @Test
    public void movingPastEitherEndDoesNothing() {
        RoutineSpec r = abc();
        assertFalse(r.moveStep(0, -1));
        assertFalse(r.moveStep(2, 3));
        assertEquals("abc", order(r));
    }

    @Test
    public void nudgesClampBetweenUntimedAndTheMaximum() {
        assertEquals(360, RoutineSpec.nudge(300, 1));
        assertEquals(0, RoutineSpec.nudge(60, -5));
        assertEquals(180 * 60, RoutineSpec.nudge(178 * 60, 5));
        assertEquals(150, RoutineSpec.nudge(90, 1));
    }

    @Test
    public void durationsReadNaturally() {
        assertEquals("untimed", RoutineSpec.durationLabel(0));
        assertEquals("10 min", RoutineSpec.durationLabel(600));
        assertEquals("1:30", RoutineSpec.durationLabel(90));
    }

    @Test
    public void dayLabelsNameTheCommonSets() {
        RoutineSpec r = abc();
        r.weekdays = 0;
        assertEquals("unscheduled", r.daysLabel());
        r.weekdays = RoutineSpec.EVERY_DAY;
        assertEquals("every day", r.daysLabel());
        r.weekdays = RoutineSpec.WEEKDAYS;
        assertEquals("weekdays", r.daysLabel());
        r.toggleDay(1);
        r.toggleDay(3);
        assertEquals("mon wed fri", r.daysLabel());
    }
}
