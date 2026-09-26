package ist.solo.routine;

/**
 * The timer for one step, stored entirely as anchors — never as a counter that
 * ticks in memory. Whatever happens to the process between two readings
 * (screen off, LightOS pulling itself to the foreground, the app being killed)
 * the remaining time is recomputed from the anchors and comes out right.
 *
 * Pure Java so it can be tested without a device.
 */
final class StepTimer {
    /** Planned duration. 0 means an untimed step: it counts up and never ends by itself. */
    long plannedMs;
    /** Time added with +1 / +5 during this step. */
    long extraMs;

    long startElapsed;
    long startWall;
    int boot;

    long pausedAccumMs;
    /**
     * Only meaningful while paused. Not usable as the paused flag: after a
     * reboot, rebased elapsed anchors can legitimately be negative.
     */
    long pausedAtElapsed = -1;
    /** -1 when running. Wall time is never negative, so this is the flag. */
    long pausedAtWall = -1;

    static StepTimer start(long plannedMs, Now now) {
        StepTimer t = new StepTimer();
        t.plannedMs = plannedMs;
        t.startElapsed = now.elapsed;
        t.startWall = now.wall;
        t.boot = now.boot;
        return t;
    }

    boolean timed() {
        return plannedMs > 0;
    }

    boolean paused() {
        return pausedAtWall >= 0;
    }

    /**
     * Re-express the elapsed anchors in the current boot's clock. After a
     * reboot elapsedRealtime restarts near zero, so the old anchors are
     * meaningless; the wall-clock anchors are used to carry them across. The
     * time the phone spent off counts as time spent, which is what actually
     * happened.
     */
    void rebase(Now now) {
        if (boot == now.boot) return;
        startElapsed = now.elapsed - (now.wall - startWall);
        if (paused()) pausedAtElapsed = now.elapsed - (now.wall - pausedAtWall);
        boot = now.boot;
    }

    /** Time spent actively on this step, excluding pauses. */
    long activeMs(Now now) {
        rebase(now);
        long end = paused() ? pausedAtElapsed : now.elapsed;
        return Math.max(0, end - startElapsed - pausedAccumMs);
    }

    /** Negative once the step has run over. Meaningless for untimed steps. */
    long remainingMs(Now now) {
        return plannedMs + extraMs - activeMs(now);
    }

    boolean due(Now now) {
        return timed() && !paused() && remainingMs(now) <= 0;
    }

    void pause(Now now) {
        if (paused()) return;
        rebase(now);
        pausedAtElapsed = now.elapsed;
        pausedAtWall = now.wall;
    }

    void resume(Now now) {
        if (!paused()) return;
        rebase(now);
        pausedAccumMs += now.elapsed - pausedAtElapsed;
        pausedAtElapsed = -1;
        pausedAtWall = -1;
    }

    void addTime(long ms) {
        // Removing time can end a step early, but never make its plan negative.
        extraMs += ms;
        if (plannedMs + extraMs < 0) extraMs = -plannedMs;
    }

    /** elapsedRealtime at which the step runs out, for the alarm. */
    long deadlineElapsed(Now now) {
        return now.elapsed + remainingMs(now);
    }
}
