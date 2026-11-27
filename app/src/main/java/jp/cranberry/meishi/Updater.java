package jp.cranberry.meishi;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** アプリ内アップデート。GitHubの最新リリースを確認し、ダウンロードしてAndroidの更新画面を出す。 */
public class Updater extends BroadcastReceiver {
    private static final String BASE = "https://github.com/cranberrynursead-alt/meishi-android/releases/latest/download/";
    private static final String ACTION = "jp.cranberry.meishi.INSTALL_STATUS";
    static volatile boolean busy;

    public static long currentCode(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        for (int i = 0; i < 5; i++) {
            HttpURLConnection g = (HttpURLConnection) new URL(url).openConnection();
            g.setInstanceFollowRedirects(false);
            g.setConnectTimeout(15000);
            g.setReadTimeout(60000);
            int code = g.getResponseCode();
            if (code >= 300 && code < 400) {
                url = new URL(new URL(url), g.getHeaderField("Location")).toString();
                g.disconnect();
                continue;
            }
            if (code != 200) {
                g.disconnect();
                throw new Exception("HTTP " + code);
            }
            return g;
        }
        throw new Exception("redirect");
    }

    /** 最新版の {versionCode, versionName}。確認できなければ null。通信するので別スレッドで呼ぶ。 */
    public static String[] latest() {
        try {
            HttpURLConnection g = open(BASE + "version.txt");
            BufferedReader r = new BufferedReader(new InputStreamReader(g.getInputStream(), "UTF-8"));
            String line = r.readLine();
            r.close();
            g.disconnect();
            if (line == null) return null;
            String[] p = line.trim().split("\\s+");
            if (p.length < 2) return null;
            Long.parseLong(p[0]);
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    /** 更新を始める。初回は「このアプリからのインストール」の許可画面を開く。 */
    public static void start(final Activity a) {
        if (busy) return;
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(a, "「この提供元のアプリを許可」をオンにして戻り、もう一度「更新する」を押してください", Toast.LENGTH_LONG).show();
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
            } catch (Exception ignored) {
            }
            return;
        }
        busy = true;
        Toast.makeText(a, "新しいバージョンをダウンロードしています", Toast.LENGTH_SHORT).show();
        final Context app = a.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                PackageInstaller.Session s = null;
                try {
                    PackageInstaller pi = app.getPackageManager().getPackageInstaller();
                    PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                    sp.setAppPackageName(app.getPackageName());
                    int id = pi.createSession(sp);
                    s = pi.openSession(id);
                    HttpURLConnection g = open(BASE + "meishi.apk");
                    InputStream in = g.getInputStream();
                    OutputStream out = s.openWrite("app", 0, -1);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    s.fsync(out);
                    out.close();
                    in.close();
                    g.disconnect();
                    Intent it = new Intent(app, Updater.class).setAction(ACTION);
                    int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    s.commit(PendingIntent.getBroadcast(app, 7, it, fl).getIntentSender());
                    s.close();
                } catch (Exception e) {
                    busy = false;
                    if (s != null) try { s.abandon(); } catch (Exception ignored) { }
                    toast(app, "更新をダウンロードできませんでした。通信状態を確認して、もう一度お試しください。");
                }
            }
        }).start();
    }

    private static void toast(final Context c, final String t) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(c, t, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        int st = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    c.startActivity(confirm);
                } catch (Exception e) {
                    busy = false;
                }
            }
            return;
        }
        busy = false;
        if (st != PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(c, "更新が完了しませんでした。もう一度「更新する」を押してください。", Toast.LENGTH_LONG).show();
        }
    }
}
