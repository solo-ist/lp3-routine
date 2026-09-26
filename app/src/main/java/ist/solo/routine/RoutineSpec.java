package ist.solo.routine;

import java.util.ArrayList;
import java.util.List;

/** A routine's definition: what it is, not what happened when it ran. */
final class RoutineSpec {
    static final int MAX_STEPS = 30;
    static final int MAX_NAME = 40;
    static final int MAX_STEP_MINUTES = 180;

    /** Monday = bit 0 … Sunday = bit 6. 0 means unscheduled. */
    static final int EVERY_DAY = 0b1111111;

    long id;
    String name;
    int weekdays;
    /** Minutes after midnight, or -1. Display only in v1. */
    int targetMinute = -1;
    /** Share of steps, in percent, that must be done for a run to count as complete. */
    int threshold = 100;
    final List<Step> steps = new ArrayList<>();

    static final class Step {
        String name;
        /** 0 = untimed: counts up, advances only on done. */
        int durationSec;
        String detail;
        /** Weekdays this step applies to (R-P2-3). Stored now, not yet used. */
        int weekdayMask = EVERY_DAY;

        Step(String name, int durationSec) {
            this.name = name;
            this.durationSec = durationSec;
        }
    }

    long plannedMs() {
        long ms = 0;
        for (Step s : steps) ms += s.durationSec * 1000L;
        return ms;
    }

    /** "8 steps · 25 min". */
    String summary() {
        String n = steps.size() == 1 ? "1 step" : steps.size() + " steps";
        long planned = plannedMs();
        return planned == 0 ? n : n + " · " + Format.minutes(planned);
    }
}
