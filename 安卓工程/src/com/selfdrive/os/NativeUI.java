package com.selfdrive.os;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
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
                    Toast.makeText(host, "请在系统设置中开启「自律 OS」的通知",
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
    }

    @JavascriptInterface
    public void cancelAlarms() {
        AlarmSched.cancelAll(host.getApplicationContext());
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
                try {
                    Context ctx = host.getApplicationContext();
                    File out = new File(ctx.getCacheDir(), "update.apk");
                    if (out.exists()) out.delete();
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(90000);
                    c.setInstanceFollowRedirects(true);
                    InputStream in = c.getInputStream();
                    FileOutputStream fos = new FileOutputStream(out);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                    fos.close();
                    in.close();
                    installApk(ctx, out);
                } catch (final Exception e) {
                    host.runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(host, "下载失败：" + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
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
