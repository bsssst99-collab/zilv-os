package com.selfdrive.os;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 网页与原生之间的桥：状态栏主题 + 通知/闹钟/更新。 */
public class NativeUI {

    private final MainActivity host;

    NativeUI(MainActivity host) {
        this.host = host;
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

    /* ===== 通知 ===== */

    @JavascriptInterface
    public boolean areNotificationsEnabled() {
        return Notify.areEnabled(host.getApplicationContext());
    }

    @JavascriptInterface
    public void enableNotifications() {
        final Context ctx = host.getApplicationContext();
        Notify.ensureChannel(ctx);
        if (!Notify.areEnabled(ctx)) {
            openNotificationSettings();
            host.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(host, "请在系统设置中开启「自律日记」的通知",
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @JavascriptInterface
    public void openNotificationSettings() {
        host.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    i.putExtra(Settings.EXTRA_APP_PACKAGE, host.getPackageName());
                    host.startActivity(i);
                } catch (Exception e) {
                    try {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + host.getPackageName()));
                        host.startActivity(i);
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    @JavascriptInterface
    public void notify(final String title, final String body) {
        Notify.show(host.getApplicationContext(), title, body, 2001);
    }

    /* ===== 后台闹钟 ===== */

    @JavascriptInterface
    public void syncAlarms(final String wake, final String sleep) {
        Notify.ensureChannel(host.getApplicationContext());
        AlarmSched.sync(host.getApplicationContext(), wake, sleep);
        promptExactAlarmIfMissing();
    }

    /** 安卓 12+：精确闹钟需运行时授权，否则 setExactAndAllowWhileIdle 会被系统延后/不触发。 */
    private void promptExactAlarmIfMissing() {
        if (Build.VERSION.SDK_INT < 31) return;
        AlarmManager am = (AlarmManager) host.getSystemService(Context.ALARM_SERVICE);
        if (am != null && !am.canScheduleExactAlarms()) {
            requestExactAlarmPermission();
        }
    }

    @JavascriptInterface
    public boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) host.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    @JavascriptInterface
    public void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < 31) return;
        host.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + host.getPackageName()));
                    host.startActivity(i);
                } catch (Exception e) {
                    try {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + host.getPackageName()));
                        host.startActivity(i);
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    @JavascriptInterface
    public void cancelAlarms() {
        AlarmSched.cancelAll(host.getApplicationContext());
    }

    /* ===== 闹钟响铃控制 / 电池优化白名单 ===== */

    @JavascriptInterface
    public void stopAlarmSound() {
        AlarmRinger.stop();
    }

    @JavascriptInterface
    public boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            android.os.PowerManager pm = (android.os.PowerManager)
                    host.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(host.getPackageName());
        } catch (Exception e) {
            return true;
        }
    }

    @JavascriptInterface
    public void requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < 23) return;
        host.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + host.getPackageName()));
                    host.startActivity(i);
                } catch (Exception e) {
                    try {
                        host.startActivity(
                                new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    /* ===== 版本更新：下载并覆盖安装 ===== */

    @JavascriptInterface
    public void downloadAndInstall(final String url, final String version) {
        host.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(host, "开始下载更新包 v" + version + "…",
                        Toast.LENGTH_LONG).show();
            }
        });
        new Thread(new Runnable() {
            public void run() {
                // 主地址 + 国内可达的 GitHub 镜像兜底；全部失败则交给浏览器手动下载。
                final String[] mirrors = new String[]{
                        url,
                        "https://gh-proxy.com/" + url,
                        "https://ghproxy.net/" + url
                };
                File out = null;
                String lastErr = "未知错误";
                for (String u : mirrors) {
                    try {
                        File f = downloadToFile(u);
                        if (f != null && isApkValid(f)) { out = f; break; }
                        lastErr = "下载的文件不是有效安装包";
                    } catch (Exception e) {
                        lastErr = (e.getMessage() == null ? e.toString() : e.getMessage());
                    }
                }
                if (out != null) {
                    installApk(host.getApplicationContext(), out);
                } else {
                    final String err = lastErr;
                    host.runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(host, "下载失败：" + err + "，正在打开浏览器手动下载",
                                    Toast.LENGTH_LONG).show();
                            openUrl(url);
                        }
                    });
                }
            }
        }).start();
    }

    /** 下载单个地址到缓存文件。手动跟随重定向，兼容部分 ROM 跨域不自动跟随的情况。 */
    private File downloadToFile(String urlStr) throws Exception {
        Context ctx = host.getApplicationContext();
        File out = new File(ctx.getCacheDir(), "update.apk");
        if (out.exists()) out.delete();
        String loc = urlStr;
        HttpURLConnection c = null;
        int redirects = 0;
        while (true) {
            c = (HttpURLConnection) new URL(loc).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(120000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String next = c.getHeaderField("Location");
                c.disconnect();
                if (next == null || redirects++ > 6) throw new IOException("重定向失败 (HTTP " + code + ")");
                loc = next;
                continue;
            }
            if (code != 200) {
                c.disconnect();
                throw new IOException("HTTP " + code);
            }
            break;
        }
        try {
            InputStream in = c.getInputStream();
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
            fos.close();
            in.close();
        } finally {
            c.disconnect();
        }
        return out;
    }

    /** 校验文件确为 APK/ZIP（魔数 PK\x03\x04），避免把镜像的错误页当成安装包。 */
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
