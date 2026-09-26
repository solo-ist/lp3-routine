package ist.solo.routine;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small view builders, so every screen draws text the same way. */
final class Ui {
    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, String s, float sp, int colour) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextColor(colour);
        tv.setTypeface(Typeface.create(Style.FONT_FAMILY, Typeface.NORMAL));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    /** Heading over a muted detail line. Detail may be null. */
    static LinearLayout row(Context c, String title, String detail) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.VERTICAL);
        quiet(row);
        int v = dp(c, Style.ROW_PAD_VERTICAL_DP);
        row.setPadding(0, v, 0, v);
        TextView head = text(c, title, Style.HEADING_SP, Style.FOREGROUND);
        head.setSingleLine(true);
        head.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(head);
        if (detail != null) {
            TextView d = text(c, detail, Style.DETAIL_SP, Style.MUTED);
            d.setPadding(0, dp(c, 4), 0, 0);
            row.addView(d);
        }
        return row;
    }

    /** A text button for a bottom bar: equal weight, centred, touch haptic. */
    static TextView barButton(Context c, String label, View.OnClickListener onTap) {
        TextView b = text(c, label, Style.BUTTON_SP, Style.FOREGROUND);
        b.setGravity(Gravity.CENTER);
        quiet(b);
        int v = dp(c, 16);
        b.setPadding(0, v, 0, v);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (onTap != null) onTap(b, () -> onTap.onClick(b));
        return b;
    }

    /** Tap handling for bar buttons: stop any step-end buzz, tick, act. */
    static void onTap(View b, Runnable action) {
        Context c = b.getContext();
        b.setOnClickListener(view -> {
            // A tap doesn't supersede the step-end buzz on its own: Android
            // drops a TOUCH vibration while an ALARM one plays
            // ("ignored_for_higher_importance"). Stop it explicitly.
            Haptics.stop(c);
            Haptics.touch(c);
            action.run();
        });
    }

    /** Enable or dim a button. Dimmed buttons keep their place and ignore taps. */
    static void setEnabled(TextView b, boolean on) {
        b.setEnabled(on);
        b.setTextColor(on ? Style.FOREGROUND : Style.MUTED);
    }

    /**
     * Turn off the framework's own haptics on a view we buzz for ourselves.
     * Otherwise a long-press plays Android's HEAVY_CLICK on top of our 40 ms
     * tick, which `dumpsys vibrator_manager` shows as two vibrations.
     */
    static <V extends View> V quiet(V v) {
        v.setHapticFeedbackEnabled(false);
        return v;
    }

    static LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
