package ist.solo.routine;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * One run of a routine, and every transition it can make.
 *
 * Pure Java: {@link Runs} loads this, applies a transition and saves it in a
 * single transaction, so the logic can be tested without a device and the
 * store stays dumb.
 *
 * What is recorded is what happened. A step that ran over records its real
 * time; a step advanced automatically records exactly its plan, and the next
 * step starts at the moment the previous one was due — not whenever the phone
 * next got round to noticing — so a late alarm can't steal time from the step
 * after it.
 */
final class RunState {

    enum Status { RUNNING, COMPLETE, PARTIAL }

    enum Outcome {
        /** Tapped done. */
        DONE,
        /** Time ran out with auto-next on. Counts as done. */
        AUTO,
        SKIPPED,
        /** The routine was ended during this step. */
        ENDED,
        /** The routine was ended before reaching this step. */
        UNREACHED
    }

    static final class Step {
        String name;
        long plannedMs;
        long startedAt = -1;
        long endedAt = -1;
        long activeMs;
        Outcome outcome;

        boolean counts() {
            return outcome == Outcome.DONE || outcome == Outcome.AUTO;
        }
    }

    String id;
    long routineId;
    String routineName;
    /** ISO date the run is attributed to, fixed at start. */
    String day;
    long startedAt;
    long endedAt = -1;
    Status status = Status.RUNNING;
    int threshold = 100;
    int current;
    /** The current step's end has already been signalled; don't buzz again. */
    boolean alerted;
    StepTimer timer;
    final List<Step> steps = new ArrayList<>();

    static RunState start(String id, RoutineSpec routine, Now now, ZoneId zone, int boundaryMinutes) {
        if (routine.steps.isEmpty()) throw new IllegalArgumentException("routine has no steps");
        RunState r = new RunState();
        r.id = id;
        r.routineId = routine.id;
        r.routineName = routine.name;
        r.day = Day.of(now.wall, zone, boundaryMinutes).toString();
        r.startedAt = now.wall;
        r.threshold = routine.threshold;
        for (RoutineSpec.Step s : routine.steps) {
            Step step = new Step();
            step.name = s.name;
            step.plannedMs = s.durationSec * 1000L;
            r.steps.add(step);
        }
        r.enter(0, now);
        return r;
    }

    boolean running() {
        return status == Status.RUNNING;
    }

    Step step() {
        return steps.get(current);
    }

    /** Null on the last step. */
    Step next() {
        return current + 1 < steps.size() ? steps.get(current + 1) : null;
    }

    // --- transitions --------------------------------------------------------

    void pause(Now now) {
        if (running()) timer.pause(now);
    }

    void resume(Now now) {
        if (running()) timer.resume(now);
    }

    void addTime(long ms, Now now) {
        if (!running()) return;
        timer.addTime(ms);
        // Adding time to a step that had already run out re-arms its alert.
        if (timer.timed() && timer.remainingMs(now) > 0) alerted = false;
    }

    void done(Now now) {
        finish(Outcome.DONE, now);
    }

    void skip(Now now) {
        finish(Outcome.SKIPPED, now);
    }

    /** End early. Whatever wasn't reached is recorded as unreached, not skipped. */
    void end(Now now) {
        if (!running()) return;
        close(step(), Outcome.ENDED, now);
        for (int i = current + 1; i < steps.size(); i++) steps.get(i).outcome = Outcome.UNREACHED;
        complete(now);
    }

    /**
     * Called by whoever looks at the run — the player's tick or the step-end
     * alarm. Returns true exactly once per step end: whichever caller gets
     * there first is the one that buzzes.
     */
    boolean advanceIfDue(Now now, boolean autoNext) {
        boolean alert = false;
        while (running() && timer.due(now)) {
            if (!alerted) alert = true;
            if (!autoNext) {
                alerted = true;
                break;
            }
            // Step ends at the moment it was due, which may be in the past.
            Now at = now.plus(timer.remainingMs(now));
            Step s = step();
            s.activeMs = timer.plannedMs + timer.extraMs;
            s.endedAt = at.wall;
            s.outcome = Outcome.AUTO;
            moveNext(at);
        }
        return alert;
    }

    // --- internals ----------------------------------------------------------

    private void finish(Outcome outcome, Now now) {
        if (!running()) return;
        close(step(), outcome, now);
        moveNext(now);
    }

    private void close(Step s, Outcome outcome, Now now) {
        s.activeMs = timer.activeMs(now);
        s.endedAt = now.wall;
        s.outcome = outcome;
    }

    private void moveNext(Now at) {
        if (current + 1 < steps.size()) enter(current + 1, at);
        else complete(at);
    }

    private void enter(int index, Now at) {
        current = index;
        Step s = steps.get(index);
        s.startedAt = at.wall;
        timer = StepTimer.start(s.plannedMs, at);
        alerted = false;
    }

    private void complete(Now at) {
        endedAt = at.wall;
        status = meetsThreshold() ? Status.COMPLETE : Status.PARTIAL;
    }

    // --- summary ------------------------------------------------------------

    int counted() {
        int n = 0;
        for (Step s : steps) if (s.counts()) n++;
        return n;
    }

    boolean meetsThreshold() {
        return counted() * 100 >= threshold * steps.size();
    }

    long plannedMs() {
        long ms = 0;
        for (Step s : steps) ms += s.plannedMs;
        return ms;
    }

    /**
     * Wall-clock time the routine will end if everything from here goes to
     * plan: what's left of this step plus every later step's plan. A step
     * already over time contributes nothing more — it ends when you tap done.
     * Untimed steps have no plan and add nothing, so while one is running the
     * estimate slides later with it, which is the truth.
     */
    long finishAt(Now now) {
        long ms = 0;
        if (timer.timed()) ms += Math.max(0, timer.remainingMs(now));
        for (int i = current + 1; i < steps.size(); i++) ms += steps.get(i).plannedMs;
        return now.wall + ms;
    }

    long actualMs() {
        long ms = 0;
        for (Step s : steps) ms += s.activeMs;
        return ms;
    }
}
