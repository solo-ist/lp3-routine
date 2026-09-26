package ist.solo.routine;

import android.content.Context;
import android.content.SharedPreferences;

/** The few settings, and the date history starts from. */
final class Prefs {
    private static final String FILE = "routine";

    private final SharedPreferences p;

    Prefs(Context c) {
        p = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /**
     * When a timed step runs out, move on by itself. Off (the default): buzz,
     * then count into overtime until you tap done — so a step never slips by
     * while you weren't looking.
     */
    boolean autoNext() {
        return p.getBoolean("auto_next", false);
    }

    void setAutoNext(boolean v) {
        p.edit().putBoolean("auto_next", v).apply();
    }

    /** Keep the screen on while a run is in progress. */
    boolean keepScreenOn() {
        return p.getBoolean("keep_screen_on", true);
    }

    void setKeepScreenOn(boolean v) {
        p.edit().putBoolean("keep_screen_on", v).apply();
    }

    /** Whether the starter routine has been added. Once only. */
    boolean seeded() {
        return p.getBoolean("seeded", false);
    }

    void setSeeded() {
        p.edit().putBoolean("seeded", true).apply();
    }

    /** Whether we've already asked for notification permission, so we ask once. */
    boolean askedNotifications() {
        return p.getBoolean("asked_notifications", false);
    }

    void setAskedNotifications() {
        p.edit().putBoolean("asked_notifications", true).apply();
    }

    /**
     * When the app first ran. The calendar will show nothing before it
     * (I3: no invented history).
     */
    long historyStart(long nowWall) {
        long v = p.getLong("history_start", 0);
        if (v == 0) {
            v = nowWall;
            p.edit().putLong("history_start", v).apply();
        }
        return v;
    }
}
