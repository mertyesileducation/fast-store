package com.kisisel.magaza;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/** Periyodik olarak kataloglari kontrol edip guncelleme bildirimi gosterir. */
public class UpdateCheckJob extends JobService {
    static final int NOTIF_ID = 42;

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            @Override public void run() {
                boolean retry = false;
                try {
                    CatalogRepo repo = CatalogRepo.get(UpdateCheckJob.this);
                    repo.refreshBlocking();
                    showUpdates(UpdateCheckJob.this, repo.updates());
                } catch (Exception e) {
                    retry = true;
                }
                jobFinished(params, retry);
            }
        }, "guncelleme-kontrol").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return true; }

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
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = updates.size() == 1 ? updates.get(0).name + " için güncelleme var"
                : updates.size() + " güncelleme mevcut";
        Notification n = new Notification.Builder(c, App.CH_UPDATES)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(names.toString())
                .setStyle(new Notification.BigTextStyle().bigText(names.toString()))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();
        App.notify(c, NOTIF_ID, n);
    }
}
