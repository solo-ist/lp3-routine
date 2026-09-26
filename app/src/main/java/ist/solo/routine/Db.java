package ist.solo.routine;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Schema v1.
 *
 * Routines are definitions and can change. Runs are history and never do:
 * each run copies the routine's name and each step's name and plan at start,
 * so editing or deleting a routine can't rewrite what already happened.
 * Every timestamp is UTC milliseconds — never a local-time string without a
 * zone, which is the bug FINDINGS.md records from the notes API.
 */
final class Db extends SQLiteOpenHelper {
    private static final String NAME = "routine.db";
    static final int VERSION = 1;

    private static Db instance;

    static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }

    private Db(Context c) {
        super(c, NAME, null, VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE routine ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "weekdays INTEGER NOT NULL DEFAULT 0,"
                + "target_minute INTEGER NOT NULL DEFAULT -1,"
                + "threshold INTEGER NOT NULL DEFAULT 100,"
                + "archived INTEGER NOT NULL DEFAULT 0,"
                + "deleted INTEGER NOT NULL DEFAULT 0,"
                + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE step ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "routine_id INTEGER NOT NULL REFERENCES routine(id) ON DELETE CASCADE,"
                + "position INTEGER NOT NULL,"
                + "name TEXT NOT NULL,"
                + "duration_sec INTEGER NOT NULL DEFAULT 0,"
                + "detail TEXT,"
                + "weekday_mask INTEGER NOT NULL DEFAULT 127)");
        db.execSQL("CREATE INDEX step_routine ON step(routine_id, position)");

        // No foreign key to routine: history outlives its definition.
        db.execSQL("CREATE TABLE run ("
                + "id TEXT PRIMARY KEY,"
                + "routine_id INTEGER NOT NULL,"
                + "routine_name TEXT NOT NULL,"
                + "day TEXT NOT NULL,"
                + "started_at INTEGER NOT NULL,"
                + "ended_at INTEGER NOT NULL DEFAULT -1,"
                + "status TEXT NOT NULL,"
                + "source TEXT NOT NULL DEFAULT 'live',"
                + "threshold INTEGER NOT NULL,"
                + "current INTEGER NOT NULL,"
                + "alerted INTEGER NOT NULL DEFAULT 0,"
                // The current step's timer anchors. See StepTimer.
                + "t_planned_ms INTEGER NOT NULL,"
                + "t_extra_ms INTEGER NOT NULL,"
                + "t_start_elapsed INTEGER NOT NULL,"
                + "t_start_wall INTEGER NOT NULL,"
                + "t_boot INTEGER NOT NULL,"
                + "t_paused_accum_ms INTEGER NOT NULL,"
                + "t_paused_at_elapsed INTEGER NOT NULL,"
                + "t_paused_at_wall INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX run_day ON run(day)");
        db.execSQL("CREATE INDEX run_status ON run(status)");
        db.execSQL("CREATE TABLE run_step ("
                + "run_id TEXT NOT NULL REFERENCES run(id) ON DELETE CASCADE,"
                + "position INTEGER NOT NULL,"
                + "name TEXT NOT NULL,"
                + "planned_ms INTEGER NOT NULL,"
                + "started_at INTEGER NOT NULL DEFAULT -1,"
                + "ended_at INTEGER NOT NULL DEFAULT -1,"
                + "active_ms INTEGER NOT NULL DEFAULT 0,"
                + "outcome TEXT,"
                + "PRIMARY KEY (run_id, position))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1 is the first schema. Each later version adds a step here, in
        // order, and never drops history.
    }
}
