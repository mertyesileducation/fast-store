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
import android.widget.Button;
import android.widget.Toast;

/** Yukle / guncelle / ac / iptal dugmelerinin ortak mantigi. */
final class Actions {
    private Actions() {}

    static final int NONE = 0, INSTALL = 1, UPDATE = 2, OPEN = 3, CANCEL = 4, RETRY = 5, WAIT = 6,
            INCOMPATIBLE = 7;

    static int action(Activity a, AppEntry e) {
        InstallManager.Task t = InstallManager.get(a).task(e.packageName);
        if (t != null) {
            if (t.state == InstallManager.FAILED) return RETRY;
            if (t.state == InstallManager.INSTALLING || t.state == InstallManager.VERIFYING) return WAIT;
            return CANCEL;
        }
        long v = CatalogRepo.installedVersion(a, e.packageName);
        if (v < 0) return e.compatible() ? INSTALL : INCOMPATIBLE;
        if (e.versionCode > v) return e.compatible() ? UPDATE : INCOMPATIBLE;
        if (e.packageName.equals(a.getPackageName())) return NONE;
        if (a.getPackageManager().getLaunchIntentForPackage(e.packageName) != null) return OPEN;
        return NONE;
    }

    static String label(Activity a, AppEntry e, int action, boolean compact) {
        InstallManager.Task t = InstallManager.get(a).task(e.packageName);
        switch (action) {
            case INSTALL: return "Yükle";
            case UPDATE: return "Güncelle";
            case OPEN: return "Aç";
            case RETRY: return "Tekrar dene";
            case INCOMPATIBLE: return "Uyumsuz";
            case WAIT:
                return t != null && t.state == InstallManager.VERIFYING ? "Doğrulanıyor" : "Yükleniyor";
            case CANCEL:
                if (t != null && t.progress >= 0) return compact ? "%" + t.progress : "İptal  •  %" + t.progress;
                return compact ? "Sırada" : "İptal";
            default: return "Yüklü";
        }
    }

    static void bind(Activity a, Button b, AppEntry e, boolean compact) {
        int act = action(a, e);
        b.setText(label(a, e, act, compact));
        b.setEnabled(act != NONE && act != WAIT && act != INCOMPATIBLE);
        boolean filled = act == INSTALL || act == UPDATE || act == OPEN;
        int color = act == RETRY ? Ui.DANGER : (act == NONE || act == WAIT || act == INCOMPATIBLE) ? Ui.muted()
                : act == CANCEL ? Ui.primaryOn() : Ui.PRIMARY;
        Ui.style(a, b, color, filled);
        b.setAlpha(act == INCOMPATIBLE ? 0.6f : 1f);
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
                    .setMessage(e.name + " " + Ui.size(a, e.size) + " boyutunda. Mobil veriyle indirilsin mi?")
                    .setPositiveButton("İndir", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { InstallManager.get(a).enqueue(e); }
                    })
                    .setNegativeButton("Wi-Fi'yi bekle", null)
                    .show();
            return;
        }
        InstallManager.get(a).enqueue(e);
        askNotificationPermission(a);
    }

    static void updateAll(Activity a) {
        if (!ensureInstallPermission(a)) return;
        AppEntry self = null;
        for (AppEntry e : CatalogRepo.get(a).updates()) {
            if (!e.compatible()) continue;
            if (e.packageName.equals(a.getPackageName())) { self = e; continue; }
            InstallManager.get(a).enqueue(e);
        }
        if (self != null) InstallManager.get(a).enqueue(self);
        askNotificationPermission(a);
    }

    static boolean ensureInstallPermission(final Activity a) {
        if (a.getPackageManager().canRequestPackageInstalls()) return true;
        new AlertDialog.Builder(a)
                .setTitle("Bir izin gerekiyor")
                .setMessage("Uygulama yükleyebilmek için Fast Store'a \"Bilinmeyen uygulamaları yükle\" izni vermelisin.\n\n"
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
                != PackageManager.PERMISSION_GRANTED && !Prefs.sp(a).getBoolean("bildirim_soruldu", false)) {
            Prefs.put(a, "bildirim_soruldu", true);
            a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
    }

    static void uninstall(Activity a, AppEntry e) {
        a.startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + e.packageName)));
    }

    static void share(Activity a, AppEntry e) {
        String text = e.name + (e.shortDescription.isEmpty() ? "" : ": " + e.shortDescription)
                + "\n\nFast Store'da: " + e.apkUrl;
        a.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, e.name).putExtra(Intent.EXTRA_TEXT, text), "Paylaş"));
    }

    static void openDetail(Activity a, AppEntry e) {
        a.startActivity(new Intent(a, DetailActivity.class).putExtra(DetailActivity.EXTRA_PKG, e.packageName));
    }
}
