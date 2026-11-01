package com.kisisel.magaza;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** APK indirme, dogrulama ve PackageInstaller ile kurulum. */
final class InstallManager {
    static final int QUEUED = 1, DOWNLOADING = 2, VERIFYING = 3, INSTALLING = 4, FAILED = 5;

    static final class Task {
        final String pkg;
        volatile int state = QUEUED;
        volatile int progress = -1; // yuzde, bilinmiyorsa -1
        volatile long done, total;
        volatile String error;
        volatile boolean cancelled;
        Task(String p) { pkg = p; }
    }

    interface Listener { void onTaskChanged(String pkg); }

    private static InstallManager instance;

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Task> tasks = new HashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    static synchronized InstallManager get(Context c) {
        if (instance == null) instance = new InstallManager(c.getApplicationContext());
        return instance;
    }

    private InstallManager(Context c) { ctx = c; }

    void addListener(Listener l) { listeners.add(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    synchronized Task task(String pkg) { return tasks.get(pkg); }

    private long lastNotify;

    private void changed(final String pkg, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastNotify < 150) return;
        lastNotify = now;
        main.post(new Runnable() {
            @Override public void run() {
                for (Listener l : listeners) l.onTaskChanged(pkg);
            }
        });
    }

    /** Kuyruga ekler. Zaten calisiyorsa false doner. */
    boolean enqueue(final AppEntry e) {
        final Task t;
        synchronized (this) {
            Task old = tasks.get(e.packageName);
            if (old != null && old.state != FAILED) return false;
            t = new Task(e.packageName);
            tasks.put(e.packageName, t);
        }
        changed(e.packageName, true);
        pool.execute(new Runnable() {
            @Override public void run() { process(e, t); }
        });
        return true;
    }

    void cancel(String pkg) {
        Task t;
        synchronized (this) { t = tasks.get(pkg); }
        if (t == null) return;
        if (t.state == INSTALLING) return; // sistem onayi bekleniyor
        t.cancelled = true;
        remove(pkg);
    }

    synchronized void remove(String pkg) {
        tasks.remove(pkg);
        changed(pkg, true);
    }

    void fail(String pkg, String msg) {
        Task t;
        synchronized (this) { t = tasks.get(pkg); }
        if (t == null) return;
        t.state = FAILED;
        t.error = msg;
        changed(pkg, true);
    }

    private File apkFile(String pkg) {
        File dir = new File(ctx.getCacheDir(), "apk");
        dir.mkdirs();
        return new File(dir, pkg + ".apk");
    }

    private void process(AppEntry e, Task t) {
        File f = apkFile(e.packageName);
        try {
            t.state = DOWNLOADING;
            changed(e.packageName, true);
            download(e, t, f);
            if (t.cancelled) { f.delete(); return; }

            t.state = VERIFYING;
            changed(e.packageName, true);
            if (!e.sha256.isEmpty()) {
                String got = sha256(f);
                if (!got.equalsIgnoreCase(e.sha256)) {
                    f.delete();
                    throw new Exception("SHA-256 eşleşmedi. Dosya bozuk veya değiştirilmiş olabilir.");
                }
            }
            PackageInfo info = ctx.getPackageManager().getPackageArchiveInfo(f.getPath(), 0);
            if (info == null) {
                f.delete();
                throw new Exception("İndirilen dosya geçerli bir APK değil.");
            }
            if (!e.packageName.equals(info.packageName)) {
                f.delete();
                throw new Exception("APK paket adı (" + info.packageName + ") katalogdakiyle aynı değil.");
            }

            t.state = INSTALLING;
            t.progress = -1;
            changed(e.packageName, true);
            install(e.packageName, f);
        } catch (Exception ex) {
            if (t.cancelled) return;
            t.state = FAILED;
            t.error = CatalogRepo.describe(ex);
            changed(e.packageName, true);
        }
    }

    private void download(AppEntry e, Task t, File f) throws Exception {
        String url = e.apkUrl;
        HttpURLConnection c = null;
        for (int redirects = 0; redirects < 8; redirects++) {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "FastStore/1.0 (Android)");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                url = new URL(new URL(url), c.getHeaderField("Location")).toString();
                c.disconnect();
                continue;
            }
            if (code != 200) {
                c.disconnect();
                throw new Exception("İndirme başarısız (HTTP " + code + ")");
            }
            break;
        }
        long total = c.getContentLengthLong();
        if (total <= 0) total = e.size;
        t.total = total;
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(f)) {
            byte[] buf = new byte[65536];
            int n;
            long done = 0;
            while ((n = in.read(buf)) > 0) {
                if (t.cancelled) return;
                out.write(buf, 0, n);
                done += n;
                t.done = done;
                t.progress = total > 0 ? (int) Math.min(100, done * 100 / total) : -1;
                changed(e.packageName, false);
            }
        } finally {
            c.disconnect();
        }
    }

    static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void install(String pkg, File f) throws Exception {
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(pkg);
        params.setSize(f.length());
        if (Build.VERSION.SDK_INT >= 31
                && CatalogRepo.prefs(ctx).getBoolean(CatalogRepo.KEY_SILENT, true)) {
            // Magazanin daha once yukledigi uygulamalar Android 12+ uzerinde onaysiz guncellenebilir
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            params.setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER);
        }
        int id = pi.createSession(params);
        PackageInstaller.Session s = pi.openSession(id);
        try {
            try (InputStream in = new FileInputStream(f);
                 OutputStream out = s.openWrite("base.apk", 0, f.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
            }
            Intent i = new Intent(ctx, InstallResultReceiver.class);
            i.putExtra(InstallResultReceiver.EXTRA_PKG, pkg);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent p = PendingIntent.getBroadcast(ctx, id, i, flags);
            s.commit(p.getIntentSender());
        } catch (Exception ex) {
            s.abandon();
            throw ex;
        } finally {
            s.close();
        }
    }

    /** Kurulum bittiginde (basarili/basarisiz) cagrilir. */
    void onInstallFinished(String pkg, boolean success, String message, boolean aborted) {
        if (success || aborted) {
            apkFile(pkg).delete();
            remove(pkg);
        } else {
            apkFile(pkg).delete();
            fail(pkg, message == null ? "Kurulum başarısız" : message);
        }
    }
}
