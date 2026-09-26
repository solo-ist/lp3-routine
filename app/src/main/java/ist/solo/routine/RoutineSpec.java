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

    // --- editing ------------------------------------------------------------

    static final String[] DAY_NAMES = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
    static final String[] DAY_SHORT = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};
    static final int WEEKDAYS = 0b0011111;
    static final int WEEKENDS = 0b1100000;

    /** New steps start here: long enough to be a real step, easy to adjust. */
    static final int DEFAULT_STEP_SEC = 5 * 60;

    boolean onDay(int day) {
        return (weekdays & (1 << day)) != 0;
    }

    void toggleDay(int day) {
        weekdays ^= 1 << day;
    }

    /** "every day", "weekdays", "weekends", "mon wed fri", or "unscheduled". */
    String daysLabel() {
        if (weekdays == 0) return "unscheduled";
        if (weekdays == EVERY_DAY) return "every day";
        if (weekdays == WEEKDAYS) return "weekdays";
        if (weekdays == WEEKENDS) return "weekends";
        StringBuilder sb = new StringBuilder();
        for (int d = 0; d < 7; d++) {
            if (!onDay(d)) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(DAY_SHORT[d]);
        }
        return sb.toString();
    }

    /** Move a step one place, keeping everything else in order. Out of range is a no-op. */
    boolean moveStep(int from, int to) {
        if (from < 0 || from >= steps.size() || to < 0 || to >= steps.size() || from == to) return false;
        steps.add(to, steps.remove(from));
        return true;
    }

    /** Duration after a ±minutes nudge, kept within 0 (untimed) … the maximum. */
    static int nudge(int durationSec, int minutes) {
        long next = (long) durationSec + minutes * 60L;
        return (int) Math.max(0, Math.min(MAX_STEP_MINUTES * 60L, next));
    }

    /** "10 min", "1:30", or "untimed". */
    static String durationLabel(int sec) {
        if (sec == 0) return "untimed";
        if (sec % 60 == 0) return (sec / 60) + " min";
        return Format.elapsed(sec * 1000L);
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
