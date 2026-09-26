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
 *
 * Laid out to mirror the player — name, then the big number, centred — so
 * editing a step looks like the step it will become. Only three text sizes
 * (detail, name, number) plus one button size, and every control sits on a
 * centred grid: four columns for the stepper, two for everything else.
 */
public class EditStepActivity extends Activity {
    static final String EXTRA_ROUTINE = "routine_id";
    static final String EXTRA_INDEX = "index";

    private FrameLayout root;
    private TextView position;
    private TextView name;
    private TextView duration;
    private TextView up;
    private TextView down;
    private TextView nextStep;
    private LinearLayout moves;
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
        int gap = Ui.dp(this, Style.GROUP_GAP_DP);

        position = Ui.text(this, "", Style.DETAIL_SP, Style.MUTED);
        position.setGravity(Gravity.CENTER);
        col.addView(position, Ui.fill());

        col.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));

        // Name and number: the same block the player shows.
        name = Ui.text(this, "", Style.STEP_NAME_SP, Style.FOREGROUND);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        Ui.onTap(name, this::rename);
        col.addView(name, Ui.fill());

        duration = Ui.text(this, "", Style.EDIT_NUMBER_SP, Style.FOREGROUND);
        duration.setGravity(Gravity.CENTER);
        duration.setPadding(0, gap / 2, 0, 0);
        col.addView(duration, Ui.fill());

        // The stepper touches the number it changes.
        col.addView(buttons(
                Ui.barButton(this, "−5", v -> nudge(-5)),
                Ui.barButton(this, "−1", v -> nudge(-1)),
                Ui.barButton(this, "+1", v -> nudge(1)),
                Ui.barButton(this, "+5", v -> nudge(5))), Ui.fill());

        // Then the step's actions as two pairs. No extra gap: each button's
        // own padding already spaces the rows evenly, and adding more left a
        // hole under the stepper.
        up = Ui.barButton(this, "move up", v -> move(-1));
        down = Ui.barButton(this, "move down", v -> move(1));
        moves = buttons(up, down);
        col.addView(moves, Ui.fill());
        col.addView(buttons(
                Ui.barButton(this, "rename", v -> rename()),
                Ui.barButton(this, "delete", v -> delete())), Ui.fill());

        col.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));

        nextStep = Ui.barButton(this, "+ next step", v -> addNext());
        col.addView(buttons(nextStep, Ui.barButton(this, "done", v -> finish())), Ui.fill());

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

    private LinearLayout buttons(TextView... bs) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (TextView b : bs) row.addView(b);
        return row;
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
        // A direction that doesn't apply is shown dimmed rather than removed,
        // so the grid stays whole and nothing shifts sideways between steps.
        // With a single step neither applies, and the row goes entirely.
        moves.setVisibility(r.steps.size() > 1 ? View.VISIBLE : View.GONE);
        Ui.setEnabled(up, index > 0);
        Ui.setEnabled(down, index < r.steps.size() - 1);
        nextStep.setVisibility(r.steps.size() < RoutineSpec.MAX_STEPS ? View.VISIBLE : View.INVISIBLE);
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

    private void delete() {
        RoutineSpec r = load();
        if (r == null) return;
        Confirm.show(root, "delete " + r.steps.get(index).name + "?", "keep", "delete", () -> {
            RoutineSpec fresh = load();
            if (fresh == null) return;
            fresh.steps.remove(index);
            new Store(this).save(fresh, System.currentTimeMillis());
            finish();
        });
    }

    /**
     * Insert a new step right after this one and move on to it — so a
     * routine can be entered start to finish without leaving this screen.
     */
    private void addNext() {
        Input.show(root, "next step", "", value -> {
            RoutineSpec r = load();
            if (r == null || r.steps.size() >= RoutineSpec.MAX_STEPS) return null;
            r.steps.add(index + 1, new RoutineSpec.Step(value, RoutineSpec.DEFAULT_STEP_SEC));
            index += 1;
            save(r);
            return null;
        }, null);
    }

    private void rename() {
        RoutineSpec r = load();
        if (r == null) return;
        Input.show(root, "rename", r.steps.get(index).name, value -> {
            RoutineSpec fresh = load();
            if (fresh == null) return null;
            fresh.steps.get(index).name = value;
            save(fresh);
            return null;
        }, null);
    }
}
