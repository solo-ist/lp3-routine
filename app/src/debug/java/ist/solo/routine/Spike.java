package ist.solo.routine;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * Phase 0 spike controls, debug builds only (which also have their own
 * applicationId and so their own data — test runs never touch real history).
 *
 * Long-press the title on the home screen to open.
 */
final class Spike {
    private Spike() {}

    static void show(Activity a, Runnable onClose) {
        // HomeActivity's own root, so the panel stacks over its content.
        FrameLayout host = (FrameLayout) ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Style.BACKGROUND);
        panel.setClickable(true);
        int side = Ui.dp(a, Style.SIDE_PAD_DP);
        panel.setPadding(side, Ui.dp(a, 16), side, 0);

        Runnable close = () -> {
            host.removeView(panel);
            onClose.run();
        };

        item(a, panel, "test: 3 × 1 min", "screen-off buzz, process death", () -> {
            close.run();
            startTest(a, "test 1 min", 60);
        });
        item(a, panel, "test: 3 × 20 s", "quick auto-next check", () -> {
            close.run();
            startTest(a, "test 20 s", 20);
        });
        item(a, panel, "notify in 30 s", "turn the screen off and watch", () -> {
            AlarmManager am = a.getSystemService(AlarmManager.class);
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 30_000, notifyIntent(a));
            close.run();
        });
        item(a, panel, "close", null, close);

        host.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static void startTest(Activity a, String name, int stepSec) {
        RoutineSpec r = new RoutineSpec();
        r.name = name;
        for (int i = 1; i <= 3; i++) r.steps.add(new RoutineSpec.Step("step " + i, stepSec));
        long id = new Store(a).upsertByName(r, System.currentTimeMillis());
        if (Runs.start(a, id) != null) a.startActivity(new Intent(a, PlayerActivity.class));
    }

    private static PendingIntent notifyIntent(Activity a) {
        Intent i = new Intent(a, SpikeReceiver.class);
        return PendingIntent.getBroadcast(a, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void item(Activity a, LinearLayout panel, String title, String detail, Runnable onTap) {
        LinearLayout row = Ui.row(a, title, detail);
        row.setOnClickListener(v -> {
            Haptics.touch(a);
            onTap.run();
        });
        panel.addView(row, Ui.fill());
    }
}
