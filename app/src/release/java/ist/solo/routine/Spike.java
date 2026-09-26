package ist.solo.routine;

import android.app.Activity;

/** Release builds have no spike controls. See src/debug for the real one. */
final class Spike {
    private Spike() {}

    static void show(Activity a, Runnable onClose) {}
}
