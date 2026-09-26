package ist.solo.routine;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** A thin line that fills left to right. No animation: it moves once a second. */
final class ProgressLine extends View {
    private final Paint track = new Paint();
    private final Paint fill = new Paint();
    private float fraction;

    ProgressLine(Context c) {
        super(c);
        track.setColor(Style.MUTED);
        fill.setColor(Style.FOREGROUND);
    }

    void set(float f) {
        float clamped = Math.max(0f, Math.min(1f, f));
        if (clamped != fraction) {
            fraction = clamped;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        float mid = h / 2f;
        canvas.drawRect(0, mid - 0.5f * getResources().getDisplayMetrics().density,
                w, mid + 0.5f * getResources().getDisplayMetrics().density, track);
        canvas.drawRect(0, 0, w * fraction, h, fill);
    }
}
