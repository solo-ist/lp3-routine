package ist.solo.routine;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Today's routines, with whatever is in progress first. Tapping a routine
 * starts it — two taps from the toolbox to a running first step.
 */
public class HomeActivity extends Activity {

    private FrameLayout root;
    private LinearLayout list;
    private LinearLayout bar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        root.setBackgroundColor(Style.BACKGROUND);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(this, Style.SIDE_PAD_DP);
        column.setPadding(side, Ui.dp(this, 16), side, 0);

        TextView title = Ui.text(this, "routine", Style.DETAIL_SP, Style.MUTED);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, Ui.dp(this, 8));
        Ui.quiet(title);
        if (BuildConfig.DEBUG) {
            // Phase 0 spike controls. Debug builds only, with their own data.
            title.setOnLongClickListener(v -> {
                Haptics.touch(this);
                Spike.show(this, this::render);
                return true;
            });
        }
        column.addView(title, Ui.fill());

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, Ui.fill());
        column.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        column.addView(bar, Ui.fill());

        root.addView(column);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Store store = new Store(this);
        new Prefs(this).historyStart(System.currentTimeMillis());
        if (!store.hasRoutines()) store.upsertByName(Templates.morning(), System.currentTimeMillis());
        render();
    }

    void render() {
        ImportFile file = new ImportFile(this);
        if (file.present()) {
            renderImport(file);
            return;
        }
        list.removeAllViews();
        bar.removeAllViews();
        bar.setVisibility(View.GONE);

        Store store = new Store(this);
        RunState running = Runs.tick(this);
        if (running != null && running.running()) {
            add(Ui.row(this, "resume " + running.routineName,
                    "step " + (running.current + 1) + " of " + running.steps.size() + " · " + running.step().name),
                    this::openPlayer);
        }

        String today = Day.of(System.currentTimeMillis(), ZoneId.systemDefault(), Day.DEFAULT_BOUNDARY_MINUTES).toString();
        List<RoutineSpec> routines = store.routines();
        for (RoutineSpec r : routines) {
            if (running != null && running.running() && running.routineId == r.id) continue;
            add(Ui.row(this, r.name, detail(store, r, today)), () -> start(r, running));
        }

        Prefs prefs = new Prefs(this);
        addSetting("auto-next", prefs.autoNext(), () -> prefs.setAutoNext(!prefs.autoNext()));
        addSetting("keep screen on", prefs.keepScreenOn(), () -> prefs.setKeepScreenOn(!prefs.keepScreenOn()));
    }

    private String detail(Store store, RoutineSpec r, String today) {
        RunState last = store.lastFinished(r.id, today);
        if (last != null) {
            String took = Format.minutes(last.actualMs());
            return last.status == RunState.Status.COMPLETE
                    ? "done · " + took
                    : "partial · " + last.counted() + " of " + last.steps.size() + " · " + took;
        }
        String s = r.summary();
        if (r.targetMinute >= 0) {
            s = String.format(Locale.ROOT, "%d:%02d · ", r.targetMinute / 60, r.targetMinute % 60) + s;
        }
        return s;
    }

    private void start(RoutineSpec r, RunState running) {
        if (running != null && running.running()) {
            Confirm.show(root, "end " + running.routineName + " and start " + r.name + "?",
                    "cancel", "start", () -> startNow(r));
        } else {
            startNow(r);
        }
    }

    private void startNow(RoutineSpec r) {
        if (Runs.start(this, r.id) != null) openPlayer();
    }

    private void openPlayer() {
        startActivity(new Intent(this, PlayerActivity.class));
    }

    private void add(View row, Runnable onTap) {
        row.setOnClickListener(v -> {
            Haptics.touch(this);
            onTap.run();
        });
        list.addView(row, Ui.fill());
    }

    private void addSetting(String label, boolean on, Runnable toggle) {
        add(Ui.row(this, label, on ? "on" : "off"), () -> {
            toggle.run();
            render();
        });
    }

    // --- import -------------------------------------------------------------

    private void renderImport(ImportFile file) {
        list.removeAllViews();
        bar.removeAllViews();
        bar.setVisibility(View.VISIBLE);
        List<RoutineSpec> parsed;
        try {
            parsed = file.read();
        } catch (ImportParser.ImportException e) {
            list.addView(Ui.row(this, "can't import " + ImportFile.NAME, e.getMessage()), Ui.fill());
            bar.addView(Ui.barButton(this, "discard", v -> {
                file.discard();
                render();
            }));
            return;
        }
        list.addView(Ui.row(this, "import " + parsed.size() + (parsed.size() == 1 ? " routine?" : " routines?"),
                "a routine with the same name is replaced; its history is kept"), Ui.fill());
        for (RoutineSpec r : parsed) list.addView(Ui.row(this, r.name, r.summary()), Ui.fill());

        bar.addView(Ui.barButton(this, "discard", v -> {
            file.discard();
            render();
        }));
        bar.addView(Ui.barButton(this, "import", v -> {
            Store store = new Store(this);
            long now = System.currentTimeMillis();
            for (RoutineSpec r : parsed) store.upsertByName(r, now);
            file.discard();
            render();
        }));
    }
}
