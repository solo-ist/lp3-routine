package ist.solo.routine;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * What actually happened, next to what was planned. For time-blindness this is
 * the useful part: not a score, just the real numbers.
 */
public class SummaryActivity extends Activity {
    static final String EXTRA_RUN = "run_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RunState r = new Store(this).run(getIntent().getStringExtra(EXTRA_RUN));
        if (r == null) {
            finish();
            return;
        }

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Style.BACKGROUND);
        int side = Ui.dp(this, Style.SIDE_PAD_DP);
        col.setPadding(side, Ui.dp(this, 16), side, 0);

        TextView title = Ui.text(this, r.routineName, Style.DETAIL_SP, Style.MUTED);
        title.setGravity(Gravity.CENTER);
        col.addView(title, Ui.fill());

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, Ui.fill());
        col.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        String status = r.status == RunState.Status.COMPLETE
                ? "complete"
                : "partial · " + r.counted() + " of " + r.steps.size() + " steps";
        list.addView(Ui.row(this, status,
                Format.elapsed(r.actualMs()) + " of " + Format.elapsed(r.plannedMs()) + " planned"), Ui.fill());

        for (RunState.Step s : r.steps) list.addView(Ui.row(this, s.name, stepDetail(s)), Ui.fill());

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(Ui.barButton(this, "done", v -> finish()));
        col.addView(bar, Ui.fill());

        setContentView(col);
    }

    static String stepDetail(RunState.Step s) {
        String planned = s.plannedMs > 0 ? Format.elapsed(s.plannedMs) : "untimed";
        String actual = Format.elapsed(s.activeMs);
        if (s.outcome == null) return planned;
        switch (s.outcome) {
            case SKIPPED: return "skipped";
            case UNREACHED: return "not reached";
            case ENDED: return "ended at " + actual;
            default: return s.plannedMs > 0 ? planned + " → " + actual : actual;
        }
    }
}
