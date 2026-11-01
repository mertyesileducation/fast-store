package com.kisisel.magaza;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** PackageInstaller oturumlarinin sonucunu alir. */
public class InstallResultReceiver extends BroadcastReceiver {
    static final String EXTRA_PKG = "pkg";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String pkg = intent.getStringExtra(EXTRA_PKG);
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        InstallManager im = InstallManager.get(ctx);
        AppEntry e = CatalogRepo.get(ctx).find(pkg);
        String name = e != null ? e.name : pkg;

        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION: {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm == null) {
                    im.onInstallFinished(pkg, false, "Onay ekranı açılamadı", false);
                    return;
                }
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                boolean shown = false;
                if (App.isForeground()) {
                    try {
                        ctx.startActivity(confirm);
                        shown = true;
                    } catch (Exception ignored) {
                    }
                }
                if (!shown) {
                    // Arka plandayken etkinlik baslatilamaz: bildirimle sor
                    PendingIntent pi = PendingIntent.getActivity(ctx, pkg.hashCode(), confirm,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    Notification n = new Notification.Builder(ctx, App.CH_INSTALL)
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                            .setContentTitle(name + " kurulmaya hazır")
                            .setContentText("Kurulumu onaylamak için dokun")
                            .setContentIntent(pi)
                            .setAutoCancel(true)
                            .build();
                    App.notify(ctx, pkg.hashCode(), n);
                }
                return;
            }
            case PackageInstaller.STATUS_SUCCESS:
                im.onInstallFinished(pkg, true, null, false);
                ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(pkg.hashCode());
                Toast.makeText(ctx, name + " yüklendi", Toast.LENGTH_SHORT).show();
                return;
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                im.onInstallFinished(pkg, false, null, true);
                return;
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                im.onInstallFinished(pkg, false,
                        "Yüklü sürümle imza çakışması. Eski sürümü kaldırıp tekrar dene.", false);
                return;
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                im.onInstallFinished(pkg, false, "Bu uygulama cihazınla uyumlu değil.", false);
                return;
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                im.onInstallFinished(pkg, false, "Yeterli depolama alanı yok.", false);
                return;
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                im.onInstallFinished(pkg, false, "Kurulum engellendi.", false);
                return;
            default:
                im.onInstallFinished(pkg, false, msg != null ? msg : "Kurulum başarısız", false);
        }
    }
}
