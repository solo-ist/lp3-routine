package ist.solo.routine;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One step at a time: its name, the time left, and what's next. Nothing else
 * of the routine is visible, on purpose.
 *
 * This screen owns no timing state. It re-reads the run on every tick and
 * draws what the anchors say, so it is equally right after the screen was
 * off, after LightOS pulled itself to the front, or after the process died.
 */
public class PlayerActivity extends Activity {

    private static final long MINUTE = 60_000L;

    /** Whether the player is on screen, so a step end knows if it needs a notification. */
    static volatile boolean visible;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::tick;

    private FrameLayout root;
    private TextView position;
    private TextView name;
    private TextView countdown;
    private ProgressLine progress;
    private TextView next;
    private TextView pause;
    private TextView plus;

    /** The run being shown, so it can hand off to the summary once it finishes. */
    private String runId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        root.setBackgroundColor(Style.BACKGROUND);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(this, Style.SIDE_PAD_DP);
        col.setPadding(side, Ui.dp(this, 16), side, 0);

        position = Ui.text(this, "", Style.DETAIL_SP, Style.MUTED);
        position.setGravity(Gravity.CENTER);
        col.addView(position, Ui.fill());

        col.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));

        name = Ui.text(this, "", Style.STEP_NAME_SP, Style.FOREGROUND);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        col.addView(name, Ui.fill());

        countdown = Ui.text(this, "", Style.COUNTDOWN_SP, Style.FOREGROUND);
        countdown.setGravity(Gravity.CENTER);
        countdown.setFontFeatureSettings("tnum"); // digits don't jitter as they change
        countdown.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 16));
        col.addView(countdown, Ui.fill());

        progress = new ProgressLine(this);
        col.addView(progress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, Style.PROGRESS_HEIGHT_DP)));

        next = Ui.text(this, "", Style.DETAIL_SP, Style.MUTED);
        next.setGravity(Gravity.CENTER);
        next.setPadding(0, Ui.dp(this, 16), 0, 0);
        col.addView(next, Ui.fill());

        col.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        pause = Ui.barButton(this, "pause", v -> act((r, now) -> {
            if (r.timer.paused()) r.resume(now);
            else r.pause(now);
            return false;
        }));
        plus = Ui.barButton(this, "+1", v -> addTime(MINUTE));
        plus.setOnLongClickListener(v -> {
            Haptics.touch(this);
            addTime(5 * MINUTE);
            return true;
        });
        bar.addView(pause);
        bar.addView(plus);
        bar.addView(Ui.barButton(this, "skip", v -> act((r, now) -> {
            r.skip(now);
            return false;
        })));
        bar.addView(Ui.barButton(this, "done", v -> act((r, now) -> {
            r.done(now);
            return false;
        })));
        col.addView(bar, Ui.fill());

        root.addView(col);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        StepNotice.cancel(this);
        if (new Prefs(this).keepScreenOn()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        tick();
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
        handler.removeCallbacks(tick);
    }

    @Override
    public void onBackPressed() {
        if (Confirm.dismiss(root)) return;
        RunState r = Runs.running(this);
        if (r == null) {
            super.onBackPressed();
            return;
        }
        Confirm.show(root, "end " + r.routineName + "?", "keep going", "end", () -> act((run, now) -> {
            run.end(now);
            return false;
        }));
    }

    private void addTime(long ms) {
        act((r, now) -> {
            r.addTime(ms, now);
            return false;
        });
    }

    private void act(Runs.Op op) {
        render(Runs.apply(this, op));
    }

    private void tick() {
        render(Runs.tick(this));
    }

    private void render(RunState r) {
        handler.removeCallbacks(tick);
        if (r != null) runId = r.id;
        if (r == null || !r.running()) {
            finishToSummary();
            return;
        }
        Now now = Runs.now(this);
        StepTimer t = r.timer;
        RunState.Step step = r.step();
        boolean paused = t.paused();

        position.setText(r.routineName + " · step " + (r.current + 1) + " of " + r.steps.size());
        name.setText(step.name);
        RunState.Step n = r.next();
        next.setText(n == null ? "last step" : "next: " + n.name);
        pause.setText(paused ? "resume" : "pause");
        countdown.setTextColor(paused ? Style.MUTED : Style.FOREGROUND);

        long delay;
        if (t.timed()) {
            plus.setVisibility(View.VISIBLE);
            long remaining = t.remainingMs(now);
            long total = t.plannedMs + t.extraMs;
            if (remaining > 0) {
                countdown.setText(Format.clock(remaining));
                progress.set(total == 0 ? 1f : 1f - (float) remaining / total);
                delay = remaining % 1000 == 0 ? 1000 : remaining % 1000;
            } else {
                // Over time: only reachable with auto-next off.
                long over = -remaining;
                countdown.setText(Format.over(over));
                progress.set(1f);
                delay = 1000 - over % 1000;
            }
        } else {
            // Untimed: count up, advance only on done.
            plus.setVisibility(View.INVISIBLE);
            long active = t.activeMs(now);
            countdown.setText(Format.elapsed(active));
            progress.set(0f);
            delay = 1000 - active % 1000;
        }
        handler.postDelayed(tick, paused ? 1000 : delay + 15);
    }

    private void finishToSummary() {
        if (runId != null) {
            startActivity(new Intent(this, SummaryActivity.class).putExtra(SummaryActivity.EXTRA_RUN, runId));
        }
        finish();
    }
}
