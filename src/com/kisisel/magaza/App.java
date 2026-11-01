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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

public class App extends Application {
    static final String CH_UPDATES = "guncellemeler";
    static final String CH_INSTALL = "kurulum";
    static final int JOB_ID = 1001;

    private static int started;

    static boolean isForeground() { return started > 0; }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH_UPDATES, "Güncellemeler",
                NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(CH_INSTALL, "Kurulumlar",
                NotificationManager.IMPORTANCE_HIGH));
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity a) { started++; }
            @Override public void onActivityStopped(Activity a) { started--; }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
        scheduleUpdateCheck(this);
    }

    static void scheduleUpdateCheck(Context c) {
        SharedPreferences p = CatalogRepo.prefs(c);
        JobScheduler js = (JobScheduler) c.getSystemService(JOB_SCHEDULER_SERVICE);
        if (!p.getBoolean(CatalogRepo.KEY_AUTO, true)) {
            js.cancel(JOB_ID);
            return;
        }
        int net = p.getBoolean(CatalogRepo.KEY_WIFI, false)
                ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY;
        JobInfo existing = js.getPendingJob(JOB_ID);
        if (existing != null && existing.getNetworkType() == net) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, UpdateCheckJob.class))
                .setPeriodic(6L * 60 * 60 * 1000)
                .setRequiredNetworkType(net)
                .setPersisted(true)
                .build();
        js.schedule(job);
    }

    static void notify(Context c, int id, Notification n) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ((NotificationManager) c.getSystemService(NOTIFICATION_SERVICE)).notify(id, n);
    }
}
