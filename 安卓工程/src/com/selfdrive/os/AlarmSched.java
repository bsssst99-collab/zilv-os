package com.selfdrive.os;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.Calendar;

/** 用 AlarmManager 在后台调度「起床 / 就寝」提醒，App 被杀也能响。 */
public final class AlarmSched {

    private static final String PREFS = "selfdrive_prefs";
    private static final int REQ_WAKE = 2001;
    private static final int REQ_SLEEP = 2002;

    private AlarmSched() {}

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void save(Context ctx, String wake, String sleep, boolean enabled) {
        sp(ctx).edit()
                .putString("wake", wake)
                .putString("sleep", sleep)
                .putBoolean("enabled", enabled)
                .apply();
    }

    static void sync(Context ctx, String wake, String sleep) {
        cancelAll(ctx);
        save(ctx, wake, sleep, true);
        schedule(ctx, wake, "wake", true);
        schedule(ctx, sleep, "sleep", true);
    }

    static void cancelAll(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            for (int req : new int[]{REQ_WAKE, REQ_SLEEP}) {
                int flags = PendingIntent.FLAG_NO_CREATE;
                if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
                Intent i = new Intent(ctx, AlarmReceiver.class);
                PendingIntent pi = PendingIntent.getBroadcast(ctx, req, i, flags);
                if (pi != null) am.cancel(pi);
            }
        }
        save(ctx, "07:00", "23:00", false);
    }

    /** allowToday=true：今天未到点就安排今天，否则明天。 */
    static void schedule(Context ctx, String hhmm, String type, boolean allowToday) {
        int req = "wake".equals(type) ? REQ_WAKE : REQ_SLEEP;
        long at = nextOccurrence(hhmm, allowToday);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent i = new Intent(ctx, AlarmReceiver.class);
        i.putExtra("type", type);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, req, i, flags);

        try {
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            // 精确闹钟权限被拒（Android 12+ / MIUI），退化为非精确，仍然会响
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } catch (Exception ignored) {}
        }
    }

    static void rescheduleAll(Context ctx) {
        SharedPreferences s = sp(ctx);
        if (!s.getBoolean("enabled", false)) return;
        schedule(ctx, s.getString("wake", "07:00"), "wake", true);
        schedule(ctx, s.getString("sleep", "23:00"), "sleep", true);
    }

    private static long nextOccurrence(String hhmm, boolean allowToday) {
        int h = 7, m = 0;
        try {
            String[] p = hhmm.split(":");
            h = Integer.parseInt(p[0]);
            m = Integer.parseInt(p[1]);
        } catch (Exception ignored) {}
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, h);
        c.set(Calendar.MINUTE, m);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (!allowToday || c.getTimeInMillis() <= System.currentTimeMillis()) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return c.getTimeInMillis();
    }
}
