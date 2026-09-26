package ist.solo.routine;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One step: its name, and its duration set by tapping ±1 / ±5 minutes rather
 * than typing a number. Down to zero makes it untimed — it counts up and waits
 * for done. Saves on every change.
 */
public class EditStepActivity extends Activity {
    static final String EXTRA_ROUTINE = "routine_id";
    static final String EXTRA_INDEX = "index";

    private FrameLayout root;
    private TextView position;
    private TextView name;
    private TextView duration;
    private LinearLayout actions;
    private long routineId;
    private int index;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        routineId = getIntent().getLongExtra(EXTRA_ROUTINE, 0);
        index = getIntent().getIntExtra(EXTRA_INDEX, 0);

        root = new FrameLayout(this);
        root.setBackgroundColor(Style.BACKGROUND);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(this, Style.SIDE_PAD_DP);
        col.setPadding(side, Ui.dp(this, 16), side, 0);

        position = Ui.text(this, "", Style.DETAIL_SP, Style.MUTED);
        position.setGravity(Gravity.CENTER);
        col.addView(position, Ui.fill());

        name = Ui.text(this, "", Style.STEP_NAME_SP, Style.FOREGROUND);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setPadding(0, Ui.dp(this, 24), 0, 0);
        Ui.quiet(name);
        name.setOnClickListener(v -> {
            Haptics.touch(this);
            rename();
        });
        col.addView(name, Ui.fill());

        TextView hint = Ui.text(this, "tap to rename", Style.DETAIL_SP, Style.MUTED);
        hint.setGravity(Gravity.CENTER);
        col.addView(hint, Ui.fill());

        duration = Ui.text(this, "", Style.COUNTDOWN_SP * 0.7f, Style.FOREGROUND);
        duration.setGravity(Gravity.CENTER);
        duration.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 8));
        col.addView(duration, Ui.fill());

        LinearLayout stepper = new LinearLayout(this);
        stepper.setOrientation(LinearLayout.HORIZONTAL);
        stepper.addView(Ui.barButton(this, "−5", v -> nudge(-5)));
        stepper.addView(Ui.barButton(this, "−1", v -> nudge(-1)));
        stepper.addView(Ui.barButton(this, "+1", v -> nudge(1)));
        stepper.addView(Ui.barButton(this, "+5", v -> nudge(5)));
        col.addView(stepper, Ui.fill());

        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(0, Ui.dp(this, 16), 0, 0);
        col.addView(actions, Ui.fill());

        col.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(Ui.barButton(this, "done", v -> finish()));
        col.addView(bar, Ui.fill());

        root.addView(col);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onBackPressed() {
        if (!Confirm.dismiss(root)) finish();
    }

    private RoutineSpec load() {
        RoutineSpec r = new Store(this).routine(routineId);
        return r == null || index < 0 || index >= r.steps.size() ? null : r;
    }

    private void save(RoutineSpec r) {
        new Store(this).save(r, System.currentTimeMillis());
        render();
    }

    private void render() {
        RoutineSpec r = load();
        if (r == null) {
            finish();
            return;
        }
        RoutineSpec.Step s = r.steps.get(index);
        position.setText(r.name + " · step " + (index + 1) + " of " + r.steps.size());
        name.setText(s.name);
        duration.setText(RoutineSpec.durationLabel(s.durationSec));

        actions.removeAllViews();
        if (index > 0) action("move up", () -> move(-1));
        if (index < r.steps.size() - 1) action("move down", () -> move(1));
        action("delete step", () -> Confirm.show(root, "delete " + s.name + "?", "keep", "delete", () -> {
            RoutineSpec fresh = load();
            if (fresh == null) return;
            fresh.steps.remove(index);
            new Store(this).save(fresh, System.currentTimeMillis());
            finish();
        }));
    }

    private void nudge(int minutes) {
        RoutineSpec r = load();
        if (r == null) return;
        RoutineSpec.Step s = r.steps.get(index);
        s.durationSec = RoutineSpec.nudge(s.durationSec, minutes);
        save(r);
    }

    private void move(int by) {
        RoutineSpec r = load();
        if (r != null && r.moveStep(index, index + by)) {
            index += by;
            save(r);
        }
    }

    private void rename() {
        RoutineSpec r = load();
        if (r == null) return;
        Input.show(root, "rename step", r.steps.get(index).name, value -> {
            RoutineSpec fresh = load();
            if (fresh == null) return null;
            fresh.steps.get(index).name = value;
            save(fresh);
            return null;
        }, null);
    }

    private void action(String label, Runnable onTap) {
        LinearLayout row = Ui.row(this, label, null);
        row.setOnClickListener(v -> {
            Haptics.touch(this);
            onTap.run();
        });
        actions.addView(row, Ui.fill());
    }
}
