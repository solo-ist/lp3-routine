package ist.solo.routine;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Phase 0, OQ-2: does LightOS show anything when a plain app posts a
 * notification with the screen off? Debug builds only.
 */
public class SpikeReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "spike";

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Spike", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);
        Notification n = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("morning")
                .setContentText("time to start")
                .setCategory(Notification.CATEGORY_REMINDER)
                .build();
        nm.notify(1, n);
    }
}
