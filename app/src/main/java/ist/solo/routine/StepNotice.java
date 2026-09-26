package ist.solo.routine;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * A visible "time's up" for when the player isn't on screen.
 *
 * The buzz alone proved too easy to miss. LightOS draws no shade, but
 * BrightControl banners notifications and wakes the screen for them (verified
 * on 582 — see SoloPhone/SIDELOADS.md), so this is what makes a step end
 * something you can see. It is replaced in place, never stacked, and cleared
 * as soon as the player is open.
 *
 * The channel doesn't vibrate: {@link Haptics#stepEnd} already has, and two
 * buzzes for one event would be noise.
 */
final class StepNotice {
    private StepNotice() {}

    private static final String CHANNEL = "step_end";
    private static final int ID = 1;

    static void post(Context c, RunState r) {
        if (c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Step ends", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);

        String text;
        if (!r.running()) text = "done";
        else if (r.alerted) text = r.step().name + " — time's up";
        else text = "now: " + r.step().name;

        Intent open = new Intent(c, r.running() ? PlayerActivity.class : HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(r.routineName)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(c, 0, open,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .build();
        nm.notify(ID, n);
    }

    static void cancel(Context c) {
        c.getSystemService(NotificationManager.class).cancel(ID);
    }
}
