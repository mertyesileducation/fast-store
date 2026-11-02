package com.kisisel.magaza;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * APK indirme, Fast Protect dogrulamalari ve PackageInstaller ile kurulum.
 * Dogrulamalar: depolama alani, cihaz uyumu, SHA-256, paket adi,
 * gelistirici imzasi (katalog) ve yuklu surumle imza uyumu.
 */
final class InstallManager {
    static final int QUEUED = 1, DOWNLOADING = 2, VERIFYING = 3, INSTALLING = 4, FAILED = 5;

    static final class Task {
        final String pkg, name, version;
        final boolean update;
        volatile int state = QUEUED;
        volatile int progress = -1;
        volatile long done, total, speed;
        volatile String error;
        volatile boolean cancelled;
        CountDownLatch finished = new CountDownLatch(1);
        Task(AppEntry e, boolean u) { pkg = e.packageName; name = e.name; version = e.versionName; update = u; }
    }

    interface Listener { void onTaskChanged(String pkg); }

    private static InstallManager instance;

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Task> tasks = new HashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private long lastNotify, lastNotif;

    static synchronized InstallManager get(Context c) {
        if (instance == null) instance = new InstallManager(c.getApplicationContext());
        return instance;
    }

    private InstallManager(Context c) { ctx = c; }

    void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    synchronized Task task(String pkg) { return tasks.get(pkg); }

    synchronized int activeCount() {
        int n = 0;
        for (Task t : tasks.values()) if (t.state != FAILED) n++;
        return n;
    }

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

    boolean enqueue(final AppEntry e) {
        final Task t;
        synchronized (this) {
            Task old = tasks.get(e.packageName);
            if (old != null && old.state != FAILED) return false;
            t = new Task(e, CatalogRepo.installedVersion(ctx, e.packageName) >= 0);
            tasks.put(e.packageName, t);
        }
        changed(e.packageName, true);
        pool.execute(new Runnable() {
            @Override public void run() { process(e, t); }
        });
        return true;
    }

    /** Arka plan isi icin: kuyruga ekler ve kurulum sonucunu bekler. */
    void installAndWait(AppEntry e, long timeoutMin) {
        if (!enqueue(e)) return;
        Task t = task(e.packageName);
        if (t == null) return;
        try {
            t.finished.await(timeoutMin, TimeUnit.MINUTES);
        } catch (InterruptedException ignored) {
        }
    }

    void cancel(String pkg) {
        Task t;
        synchronized (this) { t = tasks.get(pkg); }
        if (t == null || t.state == INSTALLING) return;
        t.cancelled = true;
        remove(pkg);
    }

    synchronized void remove(String pkg) {
        Task t = tasks.remove(pkg);
        if (t != null) t.finished.countDown();
        cancelNotif(pkg);
        changed(pkg, true);
    }

    void fail(String pkg, String msg) {
        Task t;
        synchronized (this) { t = tasks.get(pkg); }
        if (t == null) return;
        t.state = FAILED;
        t.error = msg;
        t.finished.countDown();
        notifyFailed(t);
        changed(pkg, true);
    }

    private File apkFile(String pkg) {
        File d = new File(ctx.getCacheDir(), "apk");
        d.mkdirs();
        return new File(d, pkg + ".apk");
    }

    private void process(AppEntry e, Task t) {
        File f = apkFile(e.packageName);
        try {
            // --- On kontroller ---
            if (!e.compatible()) {
                throw new Exception("Bu uygulama Android " + Ui.androidName(e.minSdk)
                        + " veya üzerini gerektiriyor. Cihazın: Android " + Build.VERSION.RELEASE + ".");
            }
            long free = new StatFs(ctx.getCacheDir().getPath()).getAvailableBytes();
            if (e.size > 0 && free < e.size * 3) {
                throw new Exception("Yeterli depolama alanı yok. En az "
                        + Ui.size(ctx, e.size * 3) + " boş alan gerekli.");
            }
            String installedSigner = installedSigner(e.packageName);
            if (installedSigner != null && !e.signer.isEmpty() && !installedSigner.equals(e.signer)) {
                throw new Exception("Yüklü sürüm farklı bir geliştirici imzasına sahip. "
                        + "Güncellemek için önce mevcut uygulamayı kaldırman gerekir.");
            }

            // --- Indirme ---
            t.state = DOWNLOADING;
            changed(e.packageName, true);
            download(e, t, f);
            if (t.cancelled) { f.delete(); return; }

            // --- Fast Protect dogrulamasi ---
            t.state = VERIFYING;
            t.progress = -1;
            changed(e.packageName, true);
            notifyProgress(t, true);
            if (!e.sha256.isEmpty() && !sha256(f).equalsIgnoreCase(e.sha256)) {
                throw new Exception("Fast Protect: dosya özeti eşleşmedi. Dosya bozuk veya değiştirilmiş olabilir.");
            }
            PackageInfo info = archiveInfo(f);
            if (info == null) throw new Exception("İndirilen dosya geçerli bir uygulama paketi değil.");
            if (!e.packageName.equals(info.packageName)) {
                throw new Exception("Fast Protect: paket adı katalogla uyuşmuyor (" + info.packageName + ").");
            }
            String apkSigner = signerOf(info);
            if (!e.signer.isEmpty() && apkSigner != null && !apkSigner.equals(e.signer)) {
                throw new Exception("Fast Protect: geliştirici imzası doğrulanamadı. Kurulum güvenlik için durduruldu.");
            }

            // --- Kurulum ---
            t.state = INSTALLING;
            changed(e.packageName, true);
            notifyProgress(t, true);
            install(e.packageName, f);
        } catch (Exception ex) {
            f.delete();
            if (t.cancelled) return;
            t.state = FAILED;
            t.error = CatalogRepo.describe(ex);
            t.finished.countDown();
            notifyFailed(t);
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
            c.setRequestProperty("User-Agent", CatalogRepo.userAgent(ctx));
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
        long start = System.currentTimeMillis();
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(f)) {
            byte[] buf = new byte[65536];
            int n;
            long done = 0;
            while ((n = in.read(buf)) > 0) {
                if (t.cancelled) return;
                out.write(buf, 0, n);
                done += n;
                t.done = done;
                long el = Math.max(1, System.currentTimeMillis() - start);
                t.speed = done * 1000 / el;
                t.progress = total > 0 ? (int) Math.min(100, done * 100 / total) : -1;
                changed(e.packageName, false);
                notifyProgress(t, false);
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
        return hex(md.digest());
    }

    static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private PackageInfo archiveInfo(File f) {
        PackageManager pm = ctx.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo pi = pm.getPackageArchiveInfo(f.getPath(), flags);
        if (pi == null) pi = pm.getPackageArchiveInfo(f.getPath(), 0);
        return pi;
    }

    /** Ilk imza sertifikasinin SHA-256 ozeti (kucuk harf hex). */
    static String signerOf(PackageInfo pi) {
        try {
            Signature[] sigs = null;
            if (Build.VERSION.SDK_INT >= 28 && pi.signingInfo != null) {
                sigs = pi.signingInfo.hasMultipleSigners() ? pi.signingInfo.getApkContentsSigners()
                        : pi.signingInfo.getSigningCertificateHistory();
            }
            if (sigs == null || sigs.length == 0) sigs = pi.signatures;
            if (sigs == null || sigs.length == 0) return null;
            // getSigningCertificateHistory en eskiden yeniye siralidir; guncel imza sonuncudur
            Signature s = sigs[sigs.length - 1];
            return hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray()));
        } catch (Exception e) {
            return null;
        }
    }

    String installedSigner(String pkg) {
        try {
            int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            return signerOf(ctx.getPackageManager().getPackageInfo(pkg, flags));
        } catch (Exception e) {
            return null;
        }
    }

    private void install(String pkg, File f) throws Exception {
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(pkg);
        params.setSize(f.length());
        params.setInstallReason(PackageManager.INSTALL_REASON_USER);
        if (Build.VERSION.SDK_INT >= 31 && Prefs.silent(ctx)) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        if (Build.VERSION.SDK_INT >= 34) {
            params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE);
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
            Intent i = new Intent(ctx, InstallResultReceiver.class).putExtra(InstallResultReceiver.EXTRA_PKG, pkg);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            s.commit(PendingIntent.getBroadcast(ctx, id, i, flags).getIntentSender());
        } catch (Exception ex) {
            s.abandon();
            throw ex;
        } finally {
            s.close();
        }
    }

    void onInstallFinished(String pkg, boolean success, String message, boolean aborted) {
        apkFile(pkg).delete();
        Task t = task(pkg);
        if (success && t != null) {
            History.add(ctx, pkg, t.name, t.version, t.update ? History.UPDATE : History.INSTALL);
        }
        if (success || aborted) {
            remove(pkg);
        } else {
            fail(pkg, message == null ? "Kurulum başarısız" : message);
        }
    }

    // ---- Bildirimler ----
    private int notifId(String pkg) { return 1000 + (pkg.hashCode() & 0xFFFF); }

    private PendingIntent openDetail(String pkg) {
        Intent i = new Intent(ctx, DetailActivity.class).putExtra(DetailActivity.EXTRA_PKG, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, notifId(pkg), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void notifyProgress(Task t, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastNotif < 700) return;
        lastNotif = now;
        String text;
        switch (t.state) {
            case DOWNLOADING:
                text = t.total > 0 ? Ui.size(ctx, t.done) + " / " + Ui.size(ctx, t.total)
                        + (t.speed > 0 ? " • " + Ui.size(ctx, t.speed) + "/sn" : "") : "İndiriliyor…";
                break;
            case VERIFYING: text = "Fast Protect ile doğrulanıyor…"; break;
            case INSTALLING: text = "Yükleniyor…"; break;
            default: text = "Sırada";
        }
        Notification n = new Notification.Builder(ctx, App.CH_DOWNLOADS)
                .setSmallIcon(R.drawable.ic_stat_store)
                .setColor(Ui.PRIMARY)
                .setContentTitle(t.name)
                .setContentText(text)
                .setProgress(100, Math.max(0, t.progress), t.progress < 0 || t.state != DOWNLOADING)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setContentIntent(openDetail(t.pkg))
                .build();
        App.notify(ctx, notifId(t.pkg), n);
    }

    private void notifyFailed(Task t) {
        Notification n = new Notification.Builder(ctx, App.CH_INSTALL)
                .setSmallIcon(R.drawable.ic_stat_store)
                .setColor(Ui.DANGER)
                .setContentTitle(t.name + " yüklenemedi")
                .setContentText(t.error)
                .setStyle(new Notification.BigTextStyle().bigText(t.error))
                .setAutoCancel(true)
                .setContentIntent(openDetail(t.pkg))
                .build();
        App.notify(ctx, notifId(t.pkg), n);
    }

    void cancelNotif(String pkg) {
        ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(notifId(pkg));
    }

    void notifyDone(String pkg, String name, boolean update) {
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        PendingIntent pi = launch == null ? openDetail(pkg) : PendingIntent.getActivity(ctx, notifId(pkg), launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(ctx, App.CH_INSTALL)
                .setSmallIcon(R.drawable.ic_stat_store)
                .setColor(Ui.SUCCESS)
                .setContentTitle(name + (update ? " güncellendi" : " yüklendi"))
                .setContentText(launch != null ? "Açmak için dokun" : "Fast Store")
                .setAutoCancel(true)
                .setTimeoutAfter(60_000)
                .setContentIntent(pi)
                .build();
        App.notify(ctx, notifId(pkg), n);
    }
}
