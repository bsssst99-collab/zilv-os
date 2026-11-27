package com.selfdrive.os;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 网页与原生之间的桥：状态栏主题 + 外链打开 + 版本更新。 */
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

    /** 用系统浏览器打开外链（金句跳豆瓣等），不在 WebView 内跳走。 */
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

    /* ===== 版本更新：下载并覆盖安装（保留作兜底，默认走前端浏览器下载） ===== */

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
