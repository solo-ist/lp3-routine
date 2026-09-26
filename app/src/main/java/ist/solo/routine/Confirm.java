package ist.solo.routine;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A full-screen question with two answers, drawn over the current screen.
 * Used where an accidental tap would lose something: ending a run, or
 * starting one while another is in progress.
 */
final class Confirm {
    private Confirm() {}

    static void show(FrameLayout root, String question, String no, String yes, Runnable onYes) {
        Context c = root.getContext();
        LinearLayout overlay = new LinearLayout(c);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setBackgroundColor(Style.BACKGROUND);
        overlay.setClickable(true); // swallow touches meant for the screen beneath
        int side = Ui.dp(c, Style.SIDE_PAD_DP);
        overlay.setPadding(side, 0, side, 0);

        TextView q = Ui.text(c, question, Style.STEP_NAME_SP, Style.FOREGROUND);
        q.setGravity(Gravity.CENTER);
        overlay.addView(q, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(Ui.barButton(c, no, v -> root.removeView(overlay)));
        bar.addView(Ui.barButton(c, yes, v -> {
            root.removeView(overlay);
            onYes.run();
        }));
        overlay.addView(bar, Ui.fill());

        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Closes the topmost overlay, if one is open. For the back key. */
    static boolean dismiss(FrameLayout root) {
        if (root.getChildCount() < 2) return false;
        root.removeViewAt(root.getChildCount() - 1);
        return true;
    }
}
