package ist.solo.routine;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * One exact alarm, set for the moment the current step runs out.
 *
 * This is what makes the step-end buzz arrive with the screen off, the app in
 * the background, or the process dead: the alarm is held by the system and
 * wakes {@link StepEndReceiver}. It is re-armed after every change to the run,
 * so there is only ever one, and it always points at the current step.
 *
 * Elapsed-realtime based, like the timer itself, so a wall-clock change can't
 * move it. After a reboot it is gone, and {@link BootReceiver} re-arms it.
 */
final class StepAlarm {
    private StepAlarm() {}

    static void sync(Context c, RunState r, Now now) {
        if (r == null || !r.running() || r.alerted || !r.timer.timed() || r.timer.paused()) {
            cancel(c);
            return;
        }
        AlarmManager am = c.getSystemService(AlarmManager.class);
        long at = r.timer.deadlineElapsed(now);
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(c));
        } else {
            // USE_EXACT_ALARM is granted at install for sideloads, so this
            // shouldn't happen. If it does, an inexact alarm is late rather
            // than absent — and the player still shows the truth.
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(c));
        }
    }

    static void cancel(Context c) {
        c.getSystemService(AlarmManager.class).cancel(pending(c));
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, StepEndReceiver.class);
        return PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
