package ist.solo.routine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class DayTest {
    private static final ZoneId NY = ZoneId.of("America/New_York");

    private static long at(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, NY).toInstant().toEpochMilli();
    }

    @Test
    public void halfPastOneOnWednesdayCountsAsTuesday() {
        // 2026-09-30 is a Wednesday.
        assertEquals(LocalDate.of(2026, 9, 29), Day.of(at(2026, 9, 30, 1, 30), NY, Day.DEFAULT_BOUNDARY_MINUTES));
    }

    @Test
    public void theBoundaryItselfIsTheNewDay() {
        assertEquals(LocalDate.of(2026, 9, 30), Day.of(at(2026, 9, 30, 4, 0), NY, Day.DEFAULT_BOUNDARY_MINUTES));
        assertEquals(LocalDate.of(2026, 9, 29), Day.of(at(2026, 9, 30, 3, 59), NY, Day.DEFAULT_BOUNDARY_MINUTES));
    }

    @Test
    public void midnightBoundaryIsPlainCalendarDays() {
        assertEquals(LocalDate.of(2026, 9, 30), Day.of(at(2026, 9, 30, 0, 1), NY, 0));
    }
}
