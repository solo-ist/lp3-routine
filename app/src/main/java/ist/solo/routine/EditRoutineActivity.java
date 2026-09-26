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

/**
 * One routine: its name, its days and its steps. Every change saves as it's
 * made — there is no "save" to forget. Runs already recorded keep their own
 * copy of names and plans, so editing never rewrites history.
 */
public class EditRoutineActivity extends Activity {
    static final String EXTRA_ID = "routine_id";

    private FrameLayout root;
    private TextView title;
    private LinearLayout list;
    private long id;
    private boolean daysPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        root.setBackgroundColor(Style.BACKGROUND);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(this, Style.SIDE_PAD_DP);
        col.setPadding(side, Ui.dp(this, 16), side, 0);

        title = Ui.text(this, "", Style.DETAIL_SP, Style.MUTED);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, Ui.dp(this, 8));
        col.addView(title, Ui.fill());

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, Ui.fill());
        col.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(Ui.barButton(this, "done", v -> onBackPressed()));
        col.addView(bar, Ui.fill());

        root.addView(col);
        setContentView(root);

        id = getIntent().getLongExtra(EXTRA_ID, 0);
        if (id == 0) askForNewRoutine();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (id != 0) render();
    }

    @Override
    public void onBackPressed() {
        if (Confirm.dismiss(root)) {
            if (id == 0) finish(); // cancelled naming a new routine
            return;
        }
        if (daysPage) {
            daysPage = false;
            render();
            return;
        }
        finish();
    }

    private void askForNewRoutine() {
        title.setText("new routine");
        Input.show(root, "name the routine", "", name -> {
            Store store = new Store(this);
            if (store.nameTaken(name, 0)) return "there's already a routine called " + name;
            RoutineSpec r = new RoutineSpec();
            r.name = name;
            r.weekdays = RoutineSpec.EVERY_DAY;
            id = store.save(r, System.currentTimeMillis());
            render();
            return null;
        }, this::finish);
    }

    private RoutineSpec load() {
        return new Store(this).routine(id);
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
        list.removeAllViews();
        if (daysPage) renderDays(r);
        else renderMain(r);
    }

    private void renderMain(RoutineSpec r) {
        title.setText("edit routine");
        add(Ui.row(this, r.name, "rename"), () -> Input.show(root, "rename", r.name, name -> {
            if (new Store(this).nameTaken(name, r.id)) return "there's already a routine called " + name;
            r.name = name;
            save(r);
            return null;
        }, null));
        add(Ui.row(this, "days", r.daysLabel()), () -> {
            daysPage = true;
            render();
        });

        list.addView(section(r.steps.isEmpty() ? "no steps yet" : "steps · " + r.summary()), Ui.fill());
        for (int i = 0; i < r.steps.size(); i++) {
            final int index = i;
            RoutineSpec.Step s = r.steps.get(i);
            add(Ui.row(this, s.name, RoutineSpec.durationLabel(s.durationSec)), () -> openStep(index));
        }
        if (r.steps.size() < RoutineSpec.MAX_STEPS) {
            add(Ui.row(this, "+ add step", null), () -> Input.show(root, "new step", "", name -> {
                RoutineSpec fresh = load();
                fresh.steps.add(new RoutineSpec.Step(name, RoutineSpec.DEFAULT_STEP_SEC));
                save(fresh);
                openStep(fresh.steps.size() - 1);
                return null;
            }, null));
        }

        list.addView(section(""), Ui.fill());
        add(Ui.row(this, "delete routine", "past runs are kept"), () ->
                Confirm.show(root, "delete " + r.name + "?", "keep", "delete", () -> {
                    new Store(this).delete(r.id);
                    finish();
                }));
    }

    private void renderDays(RoutineSpec r) {
        title.setText(r.name + " · days");
        for (int d = 0; d < 7; d++) {
            final int day = d;
            add(Ui.row(this, RoutineSpec.DAY_NAMES[d], r.onDay(d) ? "on" : "off"), () -> {
                RoutineSpec fresh = load();
                fresh.toggleDay(day);
                save(fresh);
            });
        }
    }

    private void openStep(int index) {
        startActivity(new Intent(this, EditStepActivity.class)
                .putExtra(EditStepActivity.EXTRA_ROUTINE, id)
                .putExtra(EditStepActivity.EXTRA_INDEX, index));
    }

    private void add(View row, Runnable onTap) {
        row.setOnClickListener(v -> {
            Haptics.touch(this);
            onTap.run();
        });
        list.addView(row, Ui.fill());
    }

    private TextView section(String label) {
        TextView t = Ui.text(this, label, Style.DETAIL_SP, Style.MUTED);
        t.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 4));
        return t;
    }
}
