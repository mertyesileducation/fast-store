package com.kisisel.magaza;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** PackageInstaller oturumlarinin sonucunu alir. */
public class InstallResultReceiver extends BroadcastReceiver {
    static final String EXTRA_PKG = "pkg";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String pkg = intent.getStringExtra(EXTRA_PKG);
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        InstallManager im = InstallManager.get(ctx);
        InstallManager.Task t = im.task(pkg);
        AppEntry e = CatalogRepo.get(ctx).find(pkg);
        String name = t != null ? t.name : e != null ? e.name : pkg;

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
                    PendingIntent pi = PendingIntent.getActivity(ctx, pkg.hashCode(), confirm,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    Notification n = new Notification.Builder(ctx, App.CH_INSTALL)
                            .setSmallIcon(R.drawable.ic_stat_store)
                            .setColor(Ui.PRIMARY)
                            .setContentTitle(name + " yüklenmeye hazır")
                            .setContentText("Kurulumu onaylamak için dokun")
                            .setContentIntent(pi)
                            .setAutoCancel(true)
                            .build();
                    App.notify(ctx, 1000 + (pkg.hashCode() & 0xFFFF), n);
                }
                return;
            }
            case PackageInstaller.STATUS_SUCCESS: {
                boolean update = t != null && t.update;
                im.onInstallFinished(pkg, true, null, false);
                if (!pkg.equals(ctx.getPackageName())) im.notifyDone(pkg, name, update);
                return;
            }
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                im.onInstallFinished(pkg, false, null, true);
                return;
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                im.onInstallFinished(pkg, false,
                        "Yüklü sürümle çakışma var (farklı imza veya daha yeni sürüm). Önce mevcut uygulamayı kaldır.", false);
                return;
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                im.onInstallFinished(pkg, false, "Bu uygulama cihazınla uyumlu değil.", false);
                return;
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                im.onInstallFinished(pkg, false, "Yeterli depolama alanı yok.", false);
                return;
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                im.onInstallFinished(pkg, false, "Kurulum cihaz tarafından engellendi.", false);
                return;
            case PackageInstaller.STATUS_FAILURE_INVALID:
                im.onInstallFinished(pkg, false, "Uygulama paketi geçersiz.", false);
                return;
            default:
                im.onInstallFinished(pkg, false, msg != null ? msg : "Kurulum başarısız", false);
        }
    }
}
