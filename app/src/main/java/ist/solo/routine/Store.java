package ist.solo.routine;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/** Reads and writes routines and runs. No logic beyond mapping rows. */
final class Store {
    private final SQLiteDatabase db;

    Store(Context c) {
        db = Db.get(c).getWritableDatabase();
    }

    SQLiteDatabase db() {
        return db;
    }

    // --- routines -----------------------------------------------------------

    boolean hasRoutines() {
        try (Cursor c = db.rawQuery("SELECT 1 FROM routine WHERE deleted = 0 LIMIT 1", null)) {
            return c.moveToFirst();
        }
    }

    List<RoutineSpec> routines() {
        List<RoutineSpec> out = new ArrayList<>();
        try (Cursor c = db.rawQuery(
                "SELECT id, name, weekdays, target_minute, threshold FROM routine"
                        + " WHERE deleted = 0 AND archived = 0"
                        + " ORDER BY CASE WHEN target_minute < 0 THEN 1 ELSE 0 END, target_minute, name",
                null)) {
            while (c.moveToNext()) out.add(routineFrom(c));
        }
        for (RoutineSpec r : out) loadSteps(r);
        return out;
    }

    RoutineSpec routine(long id) {
        RoutineSpec r = null;
        try (Cursor c = db.rawQuery(
                "SELECT id, name, weekdays, target_minute, threshold FROM routine WHERE id = ? AND deleted = 0",
                new String[] {Long.toString(id)})) {
            if (c.moveToFirst()) r = routineFrom(c);
        }
        if (r != null) loadSteps(r);
        return r;
    }

    /**
     * Insert, or — if a live routine already has this name — replace its
     * definition in place, keeping its id so its history stays attached.
     */
    long upsertByName(RoutineSpec r, long nowWall) {
        db.beginTransaction();
        try {
            r.id = idByName(r.name, -1);
            write(r, nowWall);
            db.setTransactionSuccessful();
            return r.id;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Save an edited routine: its fields and its whole step list, replaced
     * together. A new routine (id 0) is inserted. Past runs are untouched —
     * they carry their own copy of every name and plan.
     */
    long save(RoutineSpec r, long nowWall) {
        db.beginTransaction();
        try {
            if (r.id == 0) r.id = -1;
            write(r, nowWall);
            db.setTransactionSuccessful();
            return r.id;
        } finally {
            db.endTransaction();
        }
    }

    /** Hide a routine. Its row stays, so runs that point at it still resolve. */
    void delete(long id) {
        ContentValues v = new ContentValues();
        v.put("deleted", 1);
        db.update("routine", v, "id = ?", new String[] {Long.toString(id)});
    }

    /** Whether another live routine already uses this name, ignoring case. */
    boolean nameTaken(String name, long exceptId) {
        return idByName(name, exceptId) >= 0;
    }

    private long idByName(String name, long exceptId) {
        try (Cursor c = db.rawQuery(
                "SELECT id FROM routine WHERE name = ? COLLATE NOCASE AND deleted = 0 AND id != ? LIMIT 1",
                new String[] {name, Long.toString(exceptId)})) {
            return c.moveToFirst() ? c.getLong(0) : -1;
        }
    }

    /** Insert when r.id < 0, else update. Call inside a transaction. */
    private void write(RoutineSpec r, long nowWall) {
        ContentValues v = new ContentValues();
        v.put("name", r.name);
        v.put("weekdays", r.weekdays);
        v.put("target_minute", r.targetMinute);
        v.put("threshold", r.threshold);
        if (r.id < 0) {
            v.put("created_at", nowWall);
            r.id = db.insertOrThrow("routine", null, v);
        } else {
            db.update("routine", v, "id = ?", new String[] {Long.toString(r.id)});
            db.delete("step", "routine_id = ?", new String[] {Long.toString(r.id)});
        }
        for (int i = 0; i < r.steps.size(); i++) {
            RoutineSpec.Step s = r.steps.get(i);
            ContentValues sv = new ContentValues();
            sv.put("routine_id", r.id);
            sv.put("position", i);
            sv.put("name", s.name);
            sv.put("duration_sec", s.durationSec);
            sv.put("detail", s.detail);
            sv.put("weekday_mask", s.weekdayMask);
            db.insertOrThrow("step", null, sv);
        }
    }

    private static RoutineSpec routineFrom(Cursor c) {
        RoutineSpec r = new RoutineSpec();
        r.id = c.getLong(0);
        r.name = c.getString(1);
        r.weekdays = c.getInt(2);
        r.targetMinute = c.getInt(3);
        r.threshold = c.getInt(4);
        return r;
    }

    private void loadSteps(RoutineSpec r) {
        try (Cursor c = db.rawQuery(
                "SELECT name, duration_sec, detail, weekday_mask FROM step WHERE routine_id = ? ORDER BY position",
                new String[] {Long.toString(r.id)})) {
            while (c.moveToNext()) {
                RoutineSpec.Step s = new RoutineSpec.Step(c.getString(0), c.getInt(1));
                s.detail = c.isNull(2) ? null : c.getString(2);
                s.weekdayMask = c.getInt(3);
                r.steps.add(s);
            }
        }
    }

    // --- runs ---------------------------------------------------------------

    private static final String RUN_COLS = "id, routine_id, routine_name, day, started_at, ended_at, status,"
            + " threshold, current, alerted, t_planned_ms, t_extra_ms, t_start_elapsed, t_start_wall,"
            + " t_boot, t_paused_accum_ms, t_paused_at_elapsed, t_paused_at_wall";

    /** The run in progress, if any. There is at most one. */
    RunState running() {
        return runWhere("status = ?", RunState.Status.RUNNING.name());
    }

    RunState run(String id) {
        return runWhere("id = ?", id);
    }

    /** The most recent finished run of a routine attributed to a day, or null. */
    RunState lastFinished(long routineId, String day) {
        return runWhere("routine_id = ? AND day = ? AND status != 'RUNNING' ORDER BY started_at DESC",
                Long.toString(routineId), day);
    }

    private RunState runWhere(String where, String... args) {
        RunState r = null;
        try (Cursor c = db.rawQuery("SELECT " + RUN_COLS + " FROM run WHERE " + where + " LIMIT 1", args)) {
            if (c.moveToFirst()) {
                r = new RunState();
                r.id = c.getString(0);
                r.routineId = c.getLong(1);
                r.routineName = c.getString(2);
                r.day = c.getString(3);
                r.startedAt = c.getLong(4);
                r.endedAt = c.getLong(5);
                r.status = RunState.Status.valueOf(c.getString(6));
                r.threshold = c.getInt(7);
                r.current = c.getInt(8);
                r.alerted = c.getInt(9) != 0;
                StepTimer t = new StepTimer();
                t.plannedMs = c.getLong(10);
                t.extraMs = c.getLong(11);
                t.startElapsed = c.getLong(12);
                t.startWall = c.getLong(13);
                t.boot = c.getInt(14);
                t.pausedAccumMs = c.getLong(15);
                t.pausedAtElapsed = c.getLong(16);
                t.pausedAtWall = c.getLong(17);
                r.timer = t;
            }
        }
        if (r == null) return null;
        try (Cursor c = db.rawQuery(
                "SELECT name, planned_ms, started_at, ended_at, active_ms, outcome FROM run_step"
                        + " WHERE run_id = ? ORDER BY position",
                new String[] {r.id})) {
            while (c.moveToNext()) {
                RunState.Step s = new RunState.Step();
                s.name = c.getString(0);
                s.plannedMs = c.getLong(1);
                s.startedAt = c.getLong(2);
                s.endedAt = c.getLong(3);
                s.activeMs = c.getLong(4);
                s.outcome = c.isNull(5) ? null : RunState.Outcome.valueOf(c.getString(5));
                r.steps.add(s);
            }
        }
        return r;
    }

    /** Write the whole run. Call inside a transaction. */
    void save(RunState r) {
        ContentValues v = new ContentValues();
        v.put("id", r.id);
        v.put("routine_id", r.routineId);
        v.put("routine_name", r.routineName);
        v.put("day", r.day);
        v.put("started_at", r.startedAt);
        v.put("ended_at", r.endedAt);
        v.put("status", r.status.name());
        v.put("threshold", r.threshold);
        v.put("current", r.current);
        v.put("alerted", r.alerted ? 1 : 0);
        StepTimer t = r.timer;
        v.put("t_planned_ms", t.plannedMs);
        v.put("t_extra_ms", t.extraMs);
        v.put("t_start_elapsed", t.startElapsed);
        v.put("t_start_wall", t.startWall);
        v.put("t_boot", t.boot);
        v.put("t_paused_accum_ms", t.pausedAccumMs);
        v.put("t_paused_at_elapsed", t.pausedAtElapsed);
        v.put("t_paused_at_wall", t.pausedAtWall);
        // Insert-or-update rather than REPLACE: REPLACE deletes the row first,
        // which would cascade and delete the run's steps.
        db.insertWithOnConflict("run", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        db.update("run", v, "id = ?", new String[] {r.id});
        for (int i = 0; i < r.steps.size(); i++) {
            RunState.Step s = r.steps.get(i);
            ContentValues sv = new ContentValues();
            sv.put("run_id", r.id);
            sv.put("position", i);
            sv.put("name", s.name);
            sv.put("planned_ms", s.plannedMs);
            sv.put("started_at", s.startedAt);
            sv.put("ended_at", s.endedAt);
            sv.put("active_ms", s.activeMs);
            sv.put("outcome", s.outcome == null ? null : s.outcome.name());
            db.insertWithOnConflict("run_step", null, sv, SQLiteDatabase.CONFLICT_IGNORE);
            db.update("run_step", sv, "run_id = ? AND position = ?", new String[] {r.id, Integer.toString(i)});
        }
    }
}
