package ist.solo.routine;

import android.graphics.Color;

/**
 * LightOS's look, carried over from Menu, which measured it against the real
 * toolbox on 582-release-lp3 (1080x1240 @ 480dpi, scale 3.0).
 *
 * Typeface: LightOS maps the system "sans-serif" family to Akkurat LL in
 * /system/etc/fonts.xml, so asking for sans-serif yields the toolbox's actual
 * font with nothing to ship.
 */
final class Style {
    private Style() {}

    static final int BACKGROUND = Color.BLACK;
    static final int FOREGROUND = Color.WHITE;
    /** Secondary text: details, the next step, anything paused. */
    static final int MUTED = Color.parseColor("#6E6E6E");

    static final String FONT_FAMILY = "sans-serif";

    /** Two-line rows (heading over a muted detail), as in Notifications. */
    static final float HEADING_SP = 24f;
    static final float DETAIL_SP = 16f;
    static final int ROW_PAD_VERTICAL_DP = 12;
    static final int SIDE_PAD_DP = 24;

    /** The player. Sized so "+12:34" fits the 360dp-wide screen. */
    static final float STEP_NAME_SP = 30f;
    static final float COUNTDOWN_SP = 88f;
    static final float BUTTON_SP = 20f;
    static final int PROGRESS_HEIGHT_DP = 3;

    /**
     * Touch haptic, matched to LightOS by Menu: com.lightos plays a plain
     * createOneShot(40, DEFAULT_AMPLITUDE) with Usage=TOUCH.
     */
    static final long HAPTIC_MS = 40L;

    /**
     * Step end: three firm pulses, long enough to notice in a pocket and
     * unmistakably not a touch tick.
     */
    static final long[] STEP_END_WAVEFORM = {0, 300, 150, 300, 150, 300};
}
