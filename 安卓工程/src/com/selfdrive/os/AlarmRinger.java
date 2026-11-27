package com.selfdrive.os;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * 原生闹钟响铃：循环播放系统闹钟铃声 + 震动，并持有唤醒锁保证息屏也能响。
 * 到点由 AlarmReceiver 启动；用户在 Web 端关掉闹钟时（dismissAlarm）通过
 * NativeUI.stopAlarmSound() 调 stop()；最长 2 分钟自动停止兜底。
 */
public final class AlarmRinger {

    private static final long TIMEOUT_MS = 120000L;

    private static MediaPlayer player;
    private static Vibrator vibrator;
    private static PowerManager.WakeLock wakeLock;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Runnable stopTask = new Runnable() {
        public void run() { stop(); }
    };

    private AlarmRinger() {}

    public static synchronized void start(Context ctx) {
        stop();
        Context app = ctx.getApplicationContext();

        // 唤醒屏幕 + 保持 CPU，确保息屏/Doze 下也能出声
        try {
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK
                                | PowerManager.ACQUIRE_CAUSES_WAKEUP
                                | PowerManager.ON_AFTER_RELEASE,
                        "selfdrive:alarm_ringer");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(TIMEOUT_MS + 5000L);
            }
        } catch (Exception ignored) {}

        // 循环闹钟铃声
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            player = new MediaPlayer();
            player.setDataSource(app, uri);
            player.setLooping(true);
            if (Build.VERSION.SDK_INT >= 21) {
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            } else {
                player.setAudioStreamType(3); // AudioManager.STREAM_ALARM
            }
            player.prepare();
            player.start();
        } catch (Exception ignored) {}

        // 循环震动
        try {
            vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                long[] pat = {500, 300, 500, 300, 800, 300};
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pat, 0));
                } else {
                    vibrator.vibrate(pat, 0);
                }
            }
        } catch (Exception ignored) {}

        handler.removeCallbacks(stopTask);
        handler.postDelayed(stopTask, TIMEOUT_MS);
    }

    public static synchronized void stop() {
        handler.removeCallbacks(stopTask);
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
        if (vibrator != null) {
            try { vibrator.cancel(); } catch (Exception ignored) {}
            vibrator = null;
        }
        if (wakeLock != null) {
            try { if (wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
            wakeLock = null;
        }
    }
}
