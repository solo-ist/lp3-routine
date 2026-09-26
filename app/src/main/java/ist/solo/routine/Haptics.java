package ist.solo.routine;

import android.content.Context;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;

/**
 * The only two things this app ever vibrates for.
 *
 * Both respect the system haptics switch. The step end uses USAGE_ALARM
 * because it is something the user asked to be told, at a time they chose —
 * the same class as a kitchen timer — and alarm-usage vibration is still
 * delivered with the screen off.
 */
final class Haptics {
    private Haptics() {}

    static void touch(Context c) {
        Vibrator v = vibrator(c);
        if (v == null || !enabled(c)) return;
        v.vibrate(
                VibrationEffect.createOneShot(Style.HAPTIC_MS, VibrationEffect.DEFAULT_AMPLITUDE),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH));
    }

    static void stepEnd(Context c) {
        Vibrator v = vibrator(c);
        if (v == null || !enabled(c)) return;
        v.vibrate(
                VibrationEffect.createWaveform(Style.STEP_END_WAVEFORM, -1),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM));
    }

    /** LightOS's haptics toggle. Defaults on if the setting is absent. */
    static boolean enabled(Context c) {
        return Settings.System.getInt(c.getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0;
    }

    private static Vibrator vibrator(Context c) {
        VibratorManager vm = c.getSystemService(VibratorManager.class);
        return vm == null ? null : vm.getDefaultVibrator();
    }
}
