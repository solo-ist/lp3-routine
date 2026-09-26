package ist.solo.routine;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A full-screen single-line text field over the current screen, typed with
 * the LightOS keyboard. The only place this app asks you to type — names —
 * because typing on the LP3 is slow; everything else is a tap.
 */
final class Input {
    private Input() {}

    interface Submit {
        /** Return an error to show and stay open, or null to close. */
        String apply(String value);
    }

    static void show(FrameLayout root, String title, String initial, Submit submit, Runnable onCancel) {
        Context c = root.getContext();
        LinearLayout overlay = new LinearLayout(c);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setBackgroundColor(Style.BACKGROUND);
        overlay.setClickable(true);
        int side = Ui.dp(c, Style.SIDE_PAD_DP);
        overlay.setPadding(side, Ui.dp(c, 16), side, 0);

        // cancel · title · save, at the top. Not a bottom bar: the LightOS
        // keyboard paints a strip above its keys that it doesn't report as
        // an inset, so adjustResize leaves a bottom bar hidden under it.
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView cancel = Ui.barButton(c, "cancel", null);
        TextView heading = Ui.text(c, title, Style.DETAIL_SP, Style.MUTED);
        heading.setGravity(Gravity.CENTER);
        TextView saveButton = Ui.barButton(c, "save", null);
        header.addView(cancel);
        header.addView(heading, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f));
        header.addView(saveButton);
        overlay.addView(header, Ui.fill());

        EditText field = new EditText(c);
        field.setText(initial);
        field.setSelection(field.getText().length());
        field.setTextColor(Style.FOREGROUND);
        field.setHintTextColor(Style.MUTED);
        field.setTypeface(android.graphics.Typeface.create(Style.FONT_FAMILY, android.graphics.Typeface.NORMAL));
        field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Style.STEP_NAME_SP);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setFilters(new InputFilter[] {new InputFilter.LengthFilter(RoutineSpec.MAX_NAME)});
        field.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Style.MUTED));
        LinearLayout.LayoutParams fp = Ui.fill();
        fp.topMargin = Ui.dp(c, 16);
        overlay.addView(field, fp);

        TextView error = Ui.text(c, "", Style.DETAIL_SP, Style.MUTED);
        error.setPadding(0, Ui.dp(c, 8), 0, 0);
        overlay.addView(error, Ui.fill());

        Runnable close = () -> {
            hideKeyboard(field);
            root.removeView(overlay);
        };
        Runnable save = () -> {
            String value = field.getText().toString().trim();
            String problem = value.isEmpty() ? "needs a name" : submit.apply(value);
            if (problem != null) {
                error.setText(problem);
                return;
            }
            close.run();
        };
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                save.run();
                return true;
            }
            return false;
        });

        Ui.onTap(cancel, () -> {
            close.run();
            if (onCancel != null) onCancel.run();
        });
        Ui.onTap(saveButton, save);

        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        field.requestFocus();
        field.post(() -> c.getSystemService(InputMethodManager.class).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT));
    }

    private static void hideKeyboard(EditText field) {
        field.getContext().getSystemService(InputMethodManager.class)
                .hideSoftInputFromWindow(field.getWindowToken(), 0);
    }
}
