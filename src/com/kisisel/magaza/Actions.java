package com.kisisel.magaza;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.format.Formatter;
import android.widget.Button;
import android.widget.Toast;

/** Yukle / guncelle / ac / iptal butonlarinin ortak mantigi. */
final class Actions {
    private Actions() {}

    static final int NONE = 0, INSTALL = 1, UPDATE = 2, OPEN = 3, CANCEL = 4, RETRY = 5, WAIT = 6;

    static int action(Activity a, AppEntry e) {
        InstallManager.Task t = InstallManager.get(a).task(e.packageName);
        if (t != null) {
            if (t.state == InstallManager.FAILED) return RETRY;
            if (t.state == InstallManager.INSTALLING) return WAIT;
            return CANCEL;
        }
        long v = CatalogRepo.installedVersion(a, e.packageName);
        if (v < 0) return INSTALL;
        if (e.versionCode > v) return UPDATE;
        if (a.getPackageManager().getLaunchIntentForPackage(e.packageName) != null) return OPEN;
        return NONE;
    }

    static String label(Activity a, AppEntry e, int action) {
        InstallManager.Task t = InstallManager.get(a).task(e.packageName);
        switch (action) {
            case INSTALL: return "Yükle";
            case UPDATE: return "Güncelle";
            case OPEN: return "Aç";
            case RETRY: return "Tekrar dene";
            case WAIT: return "Kuruluyor…";
            case CANCEL:
                if (t != null && t.state == InstallManager.VERIFYING) return "Doğrulanıyor…";
                if (t != null && t.progress >= 0) return "%" + t.progress + "  ✕";
                if (t != null && t.state == InstallManager.QUEUED) return "Sırada  ✕";
                return "İndiriliyor  ✕";
            default: return "Yüklü";
        }
    }

    static void bind(Activity a, Button b, AppEntry e) {
        int act = action(a, e);
        b.setText(label(a, e, act));
        b.setEnabled(act != NONE && act != WAIT);
        int color = act == UPDATE || act == INSTALL ? Ui.PRIMARY
                : act == RETRY ? Ui.DANGER : act == OPEN ? Ui.SUCCESS : Ui.sub(a);
        Ui.style(a, b, color, act == INSTALL || act == UPDATE);
    }

    static void perform(Activity a, AppEntry e) {
        switch (action(a, e)) {
            case OPEN: {
                Intent i = a.getPackageManager().getLaunchIntentForPackage(e.packageName);
                if (i != null) a.startActivity(i);
                return;
            }
            case CANCEL:
                InstallManager.get(a).cancel(e.packageName);
                return;
            case INSTALL:
            case UPDATE:
            case RETRY:
                startInstall(a, e);
                return;
            default:
        }
    }

    static void startInstall(final Activity a, final AppEntry e) {
        if (!ensureInstallPermission(a)) return;
        ConnectivityManager cm = (ConnectivityManager) a.getSystemService(Activity.CONNECTIVITY_SERVICE);
        if (cm.isActiveNetworkMetered() && e.size > 50L * 1024 * 1024) {
            new AlertDialog.Builder(a)
                    .setTitle("Mobil veri kullanılacak")
                    .setMessage(e.name + " " + Formatter.formatShortFileSize(a, e.size)
                            + " boyutunda. Mobil veriyle indirilsin mi?")
                    .setPositiveButton("İndir", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            InstallManager.get(a).enqueue(e);
                        }
                    })
                    .setNegativeButton("Vazgeç", null)
                    .show();
            return;
        }
        InstallManager.get(a).enqueue(e);
        askNotificationPermission(a);
    }

    static boolean ensureInstallPermission(final Activity a) {
        if (a.getPackageManager().canRequestPackageInstalls()) return true;
        new AlertDialog.Builder(a)
                .setTitle("İzin gerekiyor")
                .setMessage("Uygulama kurabilmek için Fast Store'a \"Bilinmeyen uygulamaları yükle\" izni vermen gerekiyor. "
                        + "Açılan ekranda izni etkinleştirip geri dön.")
                .setPositiveButton("Ayarları aç", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + a.getPackageName())));
                        } catch (Exception ex) {
                            Toast.makeText(a, "Ayarlar açılamadı", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Vazgeç", null)
                .show();
        return false;
    }

    static void askNotificationPermission(Activity a) {
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
    }

    static void uninstall(Activity a, AppEntry e) {
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + e.packageName));
        a.startActivity(i);
    }
}
