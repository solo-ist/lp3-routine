package ist.solo.routine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Alarms don't survive a reboot. If a run was in progress, catch it up — the
 * time the phone was off counts, because it passed — and re-arm its alarm.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Runs.tick(context);
    }
}
