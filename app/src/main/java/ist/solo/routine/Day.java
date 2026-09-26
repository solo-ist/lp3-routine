package ist.solo.routine;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Which calendar day a run belongs to.
 *
 * "Today" rolls over at a boundary rather than at midnight, so a routine
 * started at 1:30 a.m. counts toward the day you think of as today, not a
 * phantom tomorrow. A run is attributed to the day in which it *started*.
 */
final class Day {
    private Day() {}

    /** 4:00 a.m. Configurable later (R-P0-9); stored per run so history never shifts. */
    static final int DEFAULT_BOUNDARY_MINUTES = 4 * 60;

    static LocalDate of(long wallMs, ZoneId zone, int boundaryMinutes) {
        return Instant.ofEpochMilli(wallMs)
                .atZone(zone)
                .minusMinutes(boundaryMinutes)
                .toLocalDate();
    }
}
