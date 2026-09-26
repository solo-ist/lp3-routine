package ist.solo.routine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Woken by {@link StepAlarm} when a step runs out. Advances the run (or, with
 * auto-next off, marks it as over time), buzzes, and arms the next step's
 * alarm — all without the player needing to be open.
 */
public class StepEndReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Runs.tick(context);
    }
}
