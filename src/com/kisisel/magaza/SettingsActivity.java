package com.kisisel.magaza;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private LinearLayout col;

    @Override
    protected void onCreate(Bundle b) {
        Ui.applyTheme(this);
        super.onCreate(b);
        Ui.styleWindow(this);
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.bg());
        LinearLayout bar = Ui.horizontal(this);
        bar.setPadding(dp(4), dp(4), dp(16), dp(4));
        ImageButton back = Ui.iconButton(this, Icons.BACK, Ui.text());
        back.setContentDescription("Geri");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        bar.addView(back, Ui.lp(dp(44), dp(44)));
        TextView title = Ui.text(this, "Ayarlar", 20, Ui.text(), true);
        title.setPadding(dp(8), 0, 0, 0);
        bar.addView(title);
        root.addView(bar);
        ScrollView sv = new ScrollView(this);
        col = Ui.vertical(this);
        col.setPadding(dp(16), 0, dp(16), dp(32));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private int dp(float v) { return Ui.dp(this, v); }

    private void render() {
        col.removeAllViews();

        // Guncellemeler
        LinearLayout up = section("Uygulama güncellemeleri");
        choice(up, "Otomatik güncelleme", new String[]{"Kapalı", "Sadece Wi-Fi", "Her zaman"},
                Prefs.autoUpdate(this), new Choice() {
                    @Override public void onChoose(int i) {
                        Prefs.put(SettingsActivity.this, Prefs.AUTO_UPDATE, i);
                        App.scheduleUpdateCheck(SettingsActivity.this, true);
                    }
                });
        note(up, Build.VERSION.SDK_INT >= 31
                ? "Fast Store'un yüklediği uygulamalar arka planda kendiliğinden güncellenir. Yaklaşık 6 saatte bir, pil düşük değilken kontrol edilir."
                : "Android 12'den eski sürümlerde güncellemeler onay gerektirir; bunun yerine bildirim gösterilir.");
        sw(up, "Güncelleme bildirimleri", "Yeni sürüm çıktığında haber ver", Prefs.NOTIFY_UPDATES, Prefs.notifyUpdates(this), true);
        if (Build.VERSION.SDK_INT >= 31) {
            sw(up, "Onaysız güncelleme", "Daha önce Fast Store ile yüklenen uygulamaların güncellemeleri onay sormadan kurulur",
                    Prefs.SILENT, Prefs.silent(this), false);
        }

        // Gorunum
        LinearLayout look = section("Görünüm");
        choice(look, "Tema", new String[]{"Sistem", "Açık", "Koyu"}, Prefs.theme(this), new Choice() {
            @Override public void onChoose(int i) {
                if (Prefs.theme(SettingsActivity.this) == i) return;
                Prefs.put(SettingsActivity.this, Prefs.THEME, i);
                Intent restart = new Intent(SettingsActivity.this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(restart);
                startActivity(new Intent(SettingsActivity.this, SettingsActivity.class));
                overridePendingTransition(0, 0);
            }
        });

        // Guvenlik ve izinler
        LinearLayout sec = section("Güvenlik ve izinler");
        boolean canInstall = getPackageManager().canRequestPackageInstalls();
        status(sec, canInstall, canInstall ? "Uygulama yükleme izni verildi" : "Uygulama yükleme izni verilmedi");
        if (!canInstall) action(sec, "İzni ver", null, Ui.primaryOn(), new Runnable() {
            @Override public void run() {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            }
        });
        boolean notif = App.canNotify(this);
        status(sec, notif, notif ? "Bildirimler açık" : "Bildirimler kapalı: indirme ve güncellemeleri göremezsin");
        if (!notif) action(sec, "Bildirimleri aç", null, Ui.primaryOn(), new Runnable() {
            @Override public void run() {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            }
        });
        note(sec, "Fast Protect her kurulumda dosya özetini (SHA-256), paket adını ve geliştirici imzasını katalogla karşılaştırır. Eşleşmezse kurulum durdurulur.");

        // Depolama
        LinearLayout st = section("Depolama");
        long img = ImageLoader.get(this).diskSize();
        action(st, "Görsel önbelleğini temizle", "Şu an " + Ui.size(this, img) + " kullanılıyor", Ui.primaryOn(), new Runnable() {
            @Override public void run() {
                ImageLoader.get(SettingsActivity.this).clear();
                Toast.makeText(SettingsActivity.this, "Önbellek temizlendi", Toast.LENGTH_SHORT).show();
                render();
            }
        });
        action(st, "Arama geçmişini temizle", null, Ui.primaryOn(), new Runnable() {
            @Override public void run() {
                Prefs.clearRecentSearches(SettingsActivity.this);
                Toast.makeText(SettingsActivity.this, "Arama geçmişi temizlendi", Toast.LENGTH_SHORT).show();
            }
        });
        action(st, "Kurulum geçmişini temizle", null, Ui.DANGER, new Runnable() {
            @Override public void run() {
                new AlertDialog.Builder(SettingsActivity.this).setTitle("Geçmiş silinsin mi?")
                        .setPositiveButton("Sil", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) { History.clear(SettingsActivity.this); }
                        }).setNegativeButton("Vazgeç", null).show();
            }
        });

        // Katalog ve hakkinda
        LinearLayout ab = section("Hakkında");
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        ab.addView(Ui.text(this, "Fast Store " + ver, 16, Ui.text(), true));
        ab.addView(Ui.text(this, "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")  •  "
                + Build.MANUFACTURER + " " + Build.MODEL, 12.5f, Ui.muted(), false));
        TextView cat = Ui.text(this, "Katalog: " + CatalogRepo.CATALOG_URL, 12, Ui.muted(), false);
        cat.setTextIsSelectable(true);
        cat.setPadding(0, dp(10), 0, 0);
        ab.addView(cat);
        action(ab, "Kataloğu şimdi yenile", null, Ui.primaryOn(), new Runnable() {
            @Override public void run() {
                CatalogRepo.get(SettingsActivity.this).refreshAsync();
                Toast.makeText(SettingsActivity.this, "Katalog yenileniyor", Toast.LENGTH_SHORT).show();
            }
        });
        action(ab, "Kaynak kodu (GitHub)", null, Ui.primaryOn(), new Runnable() {
            @Override public void run() {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mertyesileducation/fast-store")));
                } catch (Exception ignored) {
                }
            }
        });
    }

    // ---- Yardimcilar ----
    interface Choice { void onChoose(int i); }

    private LinearLayout section(String title) {
        TextView t = Ui.text(this, title, 13, Ui.primaryOn(), true);
        t.setPadding(dp(4), dp(22), 0, dp(8));
        col.addView(t);
        LinearLayout c = Ui.vertical(this);
        c.setBackground(Ui.card(this, 18));
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        col.addView(c, new LinearLayout.LayoutParams(-1, -2));
        return c;
    }

    private void choice(LinearLayout parent, String title, String[] opts, int cur, final Choice cb) {
        parent.addView(Ui.text(this, title, 15, Ui.text(), false));
        LinearLayout row = Ui.horizontal(this);
        for (int i = 0; i < opts.length; i++) {
            final int idx = i;
            TextView c = Ui.chip(this, opts[i], i == cur);
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cb.onChoose(idx);
                    render();
                }
            });
            row.addView(c, Ui.lp(-2, -2, 0, dp(8), dp(8), dp(4)));
        }
        parent.addView(row);
    }

    private void note(LinearLayout parent, String s) {
        TextView t = Ui.text(this, s, 12.5f, Ui.muted(), false);
        t.setPadding(0, dp(8), 0, dp(4));
        parent.addView(t);
    }

    private void sw(LinearLayout parent, String title, String desc, final String key, boolean value, final boolean reschedule) {
        Switch s = new Switch(this);
        s.setText(title);
        s.setTextSize(15);
        s.setTextColor(Ui.text());
        s.setChecked(value);
        s.setThumbTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Ui.PRIMARY, 0xFFF3F4F6}));
        s.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{0x884F46E5, Ui.dark ? 0xFF4B5563 : 0xFFD1D5DB}));
        s.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                Prefs.put(SettingsActivity.this, key, on);
                if (reschedule) App.scheduleUpdateCheck(SettingsActivity.this, true);
            }
        });
        parent.addView(s, Ui.lp(-1, -2, 0, dp(12), 0, 0));
        if (desc != null) {
            TextView d = Ui.text(this, desc, 12.5f, Ui.muted(), false);
            d.setPadding(0, 0, dp(56), 0);
            parent.addView(d);
        }
    }

    private void action(LinearLayout parent, String title, String desc, int color, final Runnable r) {
        LinearLayout row = Ui.vertical(this);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setBackground(Ui.selectable(this, 8));
        row.addView(Ui.text(this, title, 15, color, true));
        if (desc != null) row.addView(Ui.text(this, desc, 12.5f, Ui.muted(), false));
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { r.run(); }
        });
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void status(LinearLayout parent, boolean ok, String text) {
        TextView t = Ui.text(this, (ok ? "✓  " : "✕  ") + text, 14, ok ? Ui.SUCCESS : Ui.DANGER, false);
        t.setPadding(0, dp(6), 0, dp(4));
        parent.addView(t);
    }
}
