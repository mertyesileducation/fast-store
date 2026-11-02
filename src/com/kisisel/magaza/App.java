package com.kisisel.magaza;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import java.util.Arrays;

public class App extends Application {
    static final String CH_UPDATES = "guncellemeler";
    static final String CH_INSTALL = "kurulum";
    static final String CH_DOWNLOADS = "indirmeler";
    static final int JOB_ID = 1001;

    private static int started;

    static boolean isForeground() { return started > 0; }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel up = new NotificationChannel(CH_UPDATES, "Güncellemeler", NotificationManager.IMPORTANCE_DEFAULT);
        up.setDescription("Yeni güncellemeler olduğunda haber verir");
        NotificationChannel ins = new NotificationChannel(CH_INSTALL, "Kurulumlar", NotificationManager.IMPORTANCE_HIGH);
        ins.setDescription("Kurulum onayı, tamamlanan ve başarısız kurulumlar");
        NotificationChannel dl = new NotificationChannel(CH_DOWNLOADS, "İndirmeler", NotificationManager.IMPORTANCE_LOW);
        dl.setDescription("İndirme ilerlemesi");
        dl.setShowBadge(false);
        nm.createNotificationChannels(Arrays.asList(up, ins, dl));
        // eski surumun kanali
        nm.deleteNotificationChannel("guncelleme");

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity a) { started++; }
            @Override public void onActivityStopped(Activity a) { started--; }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
        scheduleUpdateCheck(this, false);
    }

    /** Periyodik kontrol: otomatik guncelleme veya bildirim aciksa calisir. */
    static void scheduleUpdateCheck(Context c, boolean force) {
        JobScheduler js = (JobScheduler) c.getSystemService(JOB_SCHEDULER_SERVICE);
        int mode = Prefs.autoUpdate(c);
        if (mode == 0 && !Prefs.notifyUpdates(c)) {
            js.cancel(JOB_ID);
            return;
        }
        int net = mode == 1 ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY;
        if (mode == 0) net = JobInfo.NETWORK_TYPE_ANY;
        JobInfo existing = js.getPendingJob(JOB_ID);
        if (!force && existing != null && existing.getNetworkType() == net) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, UpdateCheckJob.class))
                .setPeriodic(6L * 60 * 60 * 1000, 60L * 60 * 1000)
                .setRequiredNetworkType(net)
                .setRequiresBatteryNotLow(true)
                .setPersisted(true)
                .build();
        js.schedule(job);
    }

    static boolean canNotify(Context c) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        return ((NotificationManager) c.getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled();
    }

    static void notify(Context c, int id, Notification n) {
        if (!canNotify(c)) return;
        ((NotificationManager) c.getSystemService(NOTIFICATION_SERVICE)).notify(id, n);
    }
}
