package com.selfdrive.os;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 打卡/就寝到点提醒：普通通知（带系统提示音与震动），不响铃声、不弹全屏。 */
public final class Notify {

    private static final String CH = "reminders";
    private static final int NOTIFY_ID = 3001;

    private Notify() {}

    static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm =
                    (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel c = new NotificationChannel(
                    CH, "打卡与就寝提醒", NotificationManager.IMPORTANCE_HIGH);
            c.enableVibration(true);
            c.setDescription("到点提醒打卡、写心情日记");
            nm.createNotificationChannel(c);
        }
    }

    public static void show(Context ctx, String title, String text) {
        try {
            ensureChannel(ctx);
            NotificationManager nm =
                    (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Intent i = new Intent(ctx, MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(ctx, 2001, i, flags);

            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(ctx, CH)
                    : new Notification.Builder(ctx);
            int icon = ctx.getResources().getIdentifier("ic_launcher", "mipmap", ctx.getPackageName());
            if (icon == 0) icon = android.R.drawable.ic_lock_idle_alarm;
            b.setSmallIcon(icon)
                    .setContentTitle(title == null ? "自律日记" : title)
                    .setContentText(text == null ? "" : text)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .setVibrate(new long[]{0, 300, 250, 300});
            if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH);
            nm.notify(NOTIFY_ID, b.build());
        } catch (Exception ignored) {
        }
    }
}
