package ist.solo.routine;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import android.provider.Settings;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Every change to a run goes through here: load, apply one transition, save —
 * in a single transaction — then re-arm the step alarm and, if this change was
 * the one that claimed a step's end, buzz.
 *
 * The player's once-a-second tick and the step-end alarm both call
 * {@link #tick}. {@link RunState#advanceIfDue} returns true only for the first
 * of them to see a step end, so the phone buzzes once however the two race.
 */
final class Runs {
    private Runs() {}

    interface Op {
        /** Return true if the phone should buzz for a step end. */
        boolean apply(RunState r, Now now);
    }

    static Now now(Context c) {
        int boot = Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, 0);
        return new Now(SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot);
    }

    static RunState running(Context c) {
        return new Store(c).running();
    }

    /** Start a routine. Any run already in progress is ended first, as partial. */
    static RunState start(Context c, long routineId) {
        Store store = new Store(c);
        RoutineSpec routine = store.routine(routineId);
        if (routine == null || routine.steps.isEmpty()) return null;
        Now now = now(c);
        RunState r;
        SQLiteDatabase db = store.db();
        db.beginTransaction();
        try {
            RunState old = store.running();
            if (old != null) {
                old.end(now);
                store.save(old);
            }
            r = RunState.start(UUID.randomUUID().toString(), routine, now,
                    ZoneId.systemDefault(), Day.DEFAULT_BOUNDARY_MINUTES);
            store.save(r);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        StepAlarm.sync(c, r, now);
        return r;
    }

    /** Apply a transition to the run in progress. Returns it afterwards, or null if none. */
    static RunState apply(Context c, Op op) {
        Store store = new Store(c);
        Now now = now(c);
        boolean alert;
        RunState r;
        SQLiteDatabase db = store.db();
        db.beginTransaction();
        try {
            r = store.running();
            if (r == null) {
                db.setTransactionSuccessful();
                StepAlarm.cancel(c);
                return null;
            }
            // Catch up first, so a transition never acts on a step that has
            // already ended while nobody was looking.
            boolean autoNext = new Prefs(c).autoNext();
            alert = r.advanceIfDue(now, autoNext);
            if (r.running()) alert |= op.apply(r, now);
            store.save(r);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        StepAlarm.sync(c, r, now);
        if (alert) {
            Haptics.stepEnd(c);
            if (!PlayerActivity.visible) StepNotice.post(c, r);
        }
        return r;
    }

    static RunState tick(Context c) {
        return apply(c, (r, now) -> false);
    }
}
