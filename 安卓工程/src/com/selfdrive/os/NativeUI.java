package com.selfdrive.os;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;

/** 网页与原生之间的桥：状态栏主题 + 外链打开 + 打卡/就寝提醒 + 更新包安装。 */
public class NativeUI {

    private static final String PREFS = "reminders";

    private final MainActivity host;

    NativeUI(MainActivity host) {
        this.host = host;
    }

    private Context ctx() {
        return host.getApplicationContext();
    }

    @JavascriptInterface
    public void setDark(final boolean dark) {
        host.runOnUiThread(new Runnable() {
            public void run() { host.applySystemBars(dark); }
        });
    }

    @JavascriptInterface
    public String platform() {
        return "android";
    }

    /** 用系统浏览器打开外链，不在 WebView 内跳走。 */
    @JavascriptInterface
    public void openUrl(final String url) {
        host.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    host.startActivity(i);
                } catch (Exception ignored) {}
            }
        });
    }

    /** 智能打开外链：微信读书链接优先定向到微信读书 App（包名 com.weread.app.fenko），豆瓣链接优先豆瓣 App，打不开再回退系统浏览器。 */
    @JavascriptInterface
    public void openUrlSmart(final String url) {
        host.runOnUiThread(new Runnable() {
            public void run() {
                if (url != null && url.contains("weread.qq.com")) {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        i.setPackage("com.weread.app.fenko");
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        host.startActivity(i);
                        return;
                    } catch (Exception ignored) {}
                }
                if (url != null && url.contains("douban.com")) {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        i.setPackage("com.douban.frodo");
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        host.startActivity(i);
                        return;
                    } catch (Exception ignored) {}
                }
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    host.startActivity(i);
                } catch (Exception ignored) {}
            }
        });
    }

    /* ===== 打卡/就寝到点提醒（普通通知，不响铃声不弹全屏） ===== */

    /** wake/sleep 传 "HH:mm"；传空串表示关闭该提醒。 */
    @JavascriptInterface
    public void syncReminders(final String wake, final String sleep) {
        prefs().edit()
                .putString("wake", wake == null ? "" : wake)
                .putString("sleep", sleep == null ? "" : sleep)
                .commit();
        scheduleDaily(ctx(), 1001, wake, "自律日记 · 打卡提醒", "新的一天开始了，完成晨间洗漱打卡吧");
        scheduleDaily(ctx(), 1002, sleep, "自律日记 · 就寝提醒", "到就寝时间了，记得写今天的心情日记");
    }

    @JavascriptInterface
    public void cancelReminders() {
        AlarmManager am = (AlarmManager) ctx().getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(pi(ctx(), 1001, "", ""));
            am.cancel(pi(ctx(), 1002, "", ""));
        }
        prefs().edit().clear().commit();
    }

    /** 开机后按上次保存的时间重排提醒（BootReceiver 调用）。 */
    static void rescheduleFromPrefs(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        scheduleDaily(c, 1001, p.getString("wake", ""),
                "自律日记 · 打卡提醒", "新的一天开始了，完成晨间洗漱打卡吧");
        scheduleDaily(c, 1002, p.getString("sleep", ""),
                "自律日记 · 就寝提醒", "到就寝时间了，记得写今天的心情日记");
    }

    private SharedPreferences prefs() {
        return ctx().getSharedPreferences(PREFS, 0);
    }

    private static void scheduleDaily(Context c, int code, String hhmm, String title, String text) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent p = pi(c, code, title, text);
        if (hhmm == null || !hhmm.matches("\\d{1,2}:\\d{2}")) {
            am.cancel(p);
            return;
        }
        String[] hm = hhmm.split(":");
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
        cal.set(Calendar.MINUTE, Integer.parseInt(hm[1]));
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        long t = cal.getTimeInMillis();
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                throw new Exception("exact alarm not permitted");
            }
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, t, p);
            }
        } catch (Exception e) {
            if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, t, p);
            }
        }
    }

    private static PendingIntent pi(Context c, int code, String title, String text) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.putExtra(ReminderReceiver.EXTRA_TITLE, title);
        i.putExtra(ReminderReceiver.EXTRA_TEXT, text);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, code, i, flags);
    }

    /* ===== 更新：前端带进度条下载完成后，把安装包交给原生校验并调起安装 ===== */

    /** 入参为 base64 编码的 APK 内容；校验后调起系统安装器覆盖安装。 */
    @JavascriptInterface
    public void saveAndInstallApk(final String b64) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            Context c = ctx();
            File f = new File(c.getCacheDir(), "update.apk");
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(data);
            fos.close();
            if (!isApkValid(f)) {
                f.delete();
                throw new IOException("安装包校验失败");
            }
            installApk(c, f);
        } catch (final Exception e) {
            host.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(host, "安装失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private boolean isApkValid(File f) {
        if (f == null || f.length() < 1024) return false;
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] h = new byte[4];
            int r = fis.read(h);
            return r == 4 && h[0] == 0x50 && h[1] == 0x4B && h[2] == 0x03 && h[3] == 0x04;
        } catch (Exception e) {
            return false;
        }
    }

    private void installApk(Context ctx, File apk) {
        try {
            Uri uri = FileProviderLite.uriFor(apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(i);
        } catch (final Exception e) {
            host.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(host, "安装失败：" + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
