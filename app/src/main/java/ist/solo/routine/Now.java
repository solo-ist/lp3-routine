package ist.solo.routine;

/**
 * One reading of every clock the timer cares about, taken at the same instant.
 *
 * {@code elapsed} is {@code SystemClock.elapsedRealtime()}: monotonic, keeps
 * counting through screen-off and deep sleep, immune to the user or the
 * network changing the wall clock. It is the only clock durations are
 * measured on.
 *
 * It resets on reboot, though, so {@code boot} (Settings.Global.BOOT_COUNT)
 * identifies which boot an elapsed reading belongs to, and {@code wall}
 * (System.currentTimeMillis, UTC) is kept alongside as the fallback for
 * bridging a reboot and for attributing a run to a calendar day.
 */
final class Now {
    final long elapsed;
    final long wall;
    final int boot;

    Now(long elapsed, long wall, int boot) {
        this.elapsed = elapsed;
        this.wall = wall;
        this.boot = boot;
    }

    Now plus(long ms) {
        return new Now(elapsed + ms, wall + ms, boot);
    }
}
