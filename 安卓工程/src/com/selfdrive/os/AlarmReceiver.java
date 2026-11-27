package com.selfdrive.os;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/**
 * 闹钟到点时被系统唤起：
 *  1. 持有短时唤醒锁，保证 CPU 不立刻睡回去；
 *  2. 弹高优先级通知（带全屏 Intent）；
 *  3. 起床闹钟额外：原生循环响铃/震动 + 直接拉起 MainActivity 弹全屏界面；
 *  4. 安排下一天的同一时刻，实现每日循环。
 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String type = intent.getStringExtra("type");
        if (type == null) type = "wake";

        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = null;
        if (pm != null) {
            wl = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "selfdrive:alarm_receiver");
            wl.setReferenceCounted(false);
            wl.acquire(8000L);
        }
        try {
            if ("sleep".equals(type)) {
                Notify.show(ctx, "自律日记 · 该写日记了",
                        "到就寝时间了，今天的心情日记还没写。", 1002, false);
            } else {
                Notify.show(ctx, "自律日记 · 起床挑战",
                        "拍一张洗漱完的照片，今天才算开始。", 1001, true);
                // 原生循环响铃 + 震动（不依赖 WebView，息屏也响）
                AlarmRinger.start(ctx);
                // 兜底：直接拉起 Activity 显示全屏起床界面（配合全屏通知的豁免窗口）
                try {
                    Intent open = new Intent(ctx, MainActivity.class);
                    open.putExtra("fromAlarm", true);
                    open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    ctx.startActivity(open);
                } catch (Exception ignored) {}
            }
            AlarmSched.rescheduleAll(ctx.getApplicationContext());
        } finally {
            if (wl != null) {
                try { if (wl.isHeld()) wl.release(); } catch (Exception ignored) {}
            }
        }
    }
}
