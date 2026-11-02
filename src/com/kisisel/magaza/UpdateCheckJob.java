package com.kisisel.magaza;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.InstallSourceInfo;
import android.net.ConnectivityManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * Periyodik arka plan isi: katalogu yeniler, otomatik guncelleme aciksa
 * Fast Store'un yukledigi uygulamalari gunceller, kalanlar icin bildirim gosterir.
 */
public class UpdateCheckJob extends JobService {
    static final int NOTIF_ID = 42;
    private volatile boolean stopped;

    @Override
    public boolean onStartJob(final JobParameters params) {
        stopped = false;
        new Thread(new Runnable() {
            @Override public void run() {
                boolean retry = false;
                Context c = UpdateCheckJob.this;
                try {
                    CatalogRepo repo = CatalogRepo.get(c);
                    repo.refreshBlocking();
                    List<AppEntry> updates = repo.updates();
                    List<AppEntry> remaining = new ArrayList<>(updates);
                    if (canAutoUpdate(c)) {
                        AppEntry self = null;
                        for (AppEntry e : updates) {
                            if (stopped) break;
                            if (e.packageName.equals(getPackageName())) { self = e; continue; }
                            if (!installedByUs(c, e.packageName)) continue;
                            InstallManager.get(c).installAndWait(e, 10);
                            if (!CatalogRepo.hasUpdate(c, e)) remaining.remove(e);
                        }
                        // Fast Store kendini en son gunceller (surec yeniden baslar)
                        if (self != null && !stopped) InstallManager.get(c).enqueue(self);
                    }
                    if (Prefs.notifyUpdates(c)) showUpdates(c, remaining);
                } catch (Exception e) {
                    retry = true;
                }
                jobFinished(params, retry && !stopped);
            }
        }, "guncelleme-isi").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        stopped = true;
        return true;
    }

    private static boolean canAutoUpdate(Context c) {
        int mode = Prefs.autoUpdate(c);
        if (mode == 0) return false;
        if (mode == 1) {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(CONNECTIVITY_SERVICE);
            if (cm.isActiveNetworkMetered()) return false;
        }
        return Build.VERSION.SDK_INT >= 31 && Prefs.silent(c);
    }

    /** Onaysiz guncelleme sadece kurulum kaynagi Fast Store olan uygulamalarda calisir. */
    static boolean installedByUs(Context c, String pkg) {
        try {
            String installer;
            if (Build.VERSION.SDK_INT >= 30) {
                InstallSourceInfo info = c.getPackageManager().getInstallSourceInfo(pkg);
                installer = info.getInstallingPackageName();
            } else {
                installer = c.getPackageManager().getInstallerPackageName(pkg);
            }
            return c.getPackageName().equals(installer);
        } catch (Exception e) {
            return false;
        }
    }

    static void showUpdates(Context c, List<AppEntry> updates) {
        NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
        if (updates.isEmpty()) {
            nm.cancel(NOTIF_ID);
            return;
        }
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < updates.size(); i++) {
            if (i > 0) names.append(", ");
            names.append(updates.get(i).name);
        }
        Intent open = new Intent(c, MainActivity.class)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_UPDATES)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = updates.size() == 1 ? updates.get(0).name + " için güncelleme var"
                : updates.size() + " uygulama için güncelleme var";
        Notification n = new Notification.Builder(c, App.CH_UPDATES)
                .setSmallIcon(R.drawable.ic_stat_store)
                .setColor(Ui.PRIMARY)
                .setContentTitle(title)
                .setContentText(names.toString())
                .setStyle(new Notification.BigTextStyle().bigText(names.toString()))
                .setNumber(updates.size())
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();
        App.notify(c, NOTIF_ID, n);
    }
}
