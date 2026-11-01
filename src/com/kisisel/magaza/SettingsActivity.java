package com.kisisel.magaza;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URL;

public class SettingsActivity extends Activity {
    private EditText sources;
    private TextView permStatus;
    private Button permBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.styleBars(this);
        setTitle("Ayarlar");
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(build());
    }

    private int dp(float v) { return Ui.dp(this, v); }

    private View build() {
        final SharedPreferences p = CatalogRepo.prefs(this);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.bg(this));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(16), dp(20), dp(32));
        sv.addView(col);

        // Katalog kaynaklari
        LinearLayout c1 = card(col, "Katalog adresleri");
        c1.addView(Ui.text(this, "Her satıra bir katalog.json adresi yaz. Birden fazla katalog birleştirilir.",
                13, Ui.sub(this), false));
        sources = new EditText(this);
        sources.setText(p.getString(CatalogRepo.KEY_SOURCES, ""));
        sources.setHint("https://ornek.com/magaza/katalog.json");
        sources.setTextSize(14);
        sources.setTextColor(Ui.text(this));
        sources.setHintTextColor(Ui.sub(this));
        sources.setMinLines(3);
        sources.setGravity(Gravity.TOP);
        sources.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        sources.setBackground(Ui.round(Ui.bg(this), dp(10)));
        sources.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(10);
        c1.addView(sources, slp);
        Button save = Ui.button(this, "Kaydet ve yenile", Ui.PRIMARY, true);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveSources(); }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(44));
        blp.topMargin = dp(12);
        c1.addView(save, blp);

        // Guncellemeler
        LinearLayout c2 = card(col, "Güncellemeler");
        sw(c2, "Güncellemeleri otomatik kontrol et", "Yaklaşık 6 saatte bir arka planda kontrol eder, bildirim gösterir.",
                CatalogRepo.KEY_AUTO, true);
        sw(c2, "Sadece Wi-Fi ile kontrol et", null, CatalogRepo.KEY_WIFI, false);
        if (Build.VERSION.SDK_INT >= 31) {
            sw(c2, "Onaysız güncelleme (Android 12+)",
                    "Fast Store'un yüklediği uygulamaların güncellemeleri onay sormadan kurulur.",
                    CatalogRepo.KEY_SILENT, true);
        }

        // Izinler
        LinearLayout c3 = card(col, "İzinler");
        permStatus = Ui.text(this, "", 14, Ui.text(this), false);
        c3.addView(permStatus);
        permBtn = Ui.button(this, "İzni ver", Ui.PRIMARY, false);
        permBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, dp(40));
        plp.topMargin = dp(10);
        c3.addView(permBtn, plp);

        // Depolama
        LinearLayout c4 = card(col, "Depolama");
        Button clear = Ui.button(this, "Önbelleği temizle", Ui.DANGER, false);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ImageLoader.get(SettingsActivity.this).clear();
                CatalogRepo.get(SettingsActivity.this).clearCache();
                CatalogRepo.get(SettingsActivity.this).refreshAsync();
                Toast.makeText(SettingsActivity.this, "Önbellek temizlendi", Toast.LENGTH_SHORT).show();
            }
        });
        c4.addView(clear, new LinearLayout.LayoutParams(-2, dp(40)));

        // Hakkinda
        LinearLayout c5 = card(col, "Hakkında");
        String ver = "1.0";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        c5.addView(Ui.text(this, "Fast Store " + ver + "\nKendi uygulamaların için kişisel uygulama mağazası.\n\n"
                + "İpucu: Fast Store'un kendi APK'sını da kataloğa eklersen mağaza kendini de güncelleyebilir.",
                13, Ui.sub(this), false));
        return sv;
    }

    private LinearLayout card(LinearLayout parent, String title) {
        TextView h = Ui.text(this, title, 13, Ui.PRIMARY, true);
        h.setAllCaps(true);
        h.setLetterSpacing(0.06f);
        h.setPadding(dp(4), dp(18), 0, dp(8));
        parent.addView(h);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.round(Ui.card(this), dp(14)));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        parent.addView(c, new LinearLayout.LayoutParams(-1, -2));
        return c;
    }

    private void sw(LinearLayout parent, String title, String desc, final String key, boolean def) {
        Switch s = new Switch(this);
        s.setText(title);
        s.setTextSize(15);
        s.setTextColor(Ui.text(this));
        s.setChecked(CatalogRepo.prefs(this).getBoolean(key, def));
        s.setPadding(0, dp(6), 0, desc == null ? dp(6) : 0);
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                CatalogRepo.prefs(SettingsActivity.this).edit().putBoolean(key, on).apply();
                App.scheduleUpdateCheck(SettingsActivity.this);
            }
        });
        parent.addView(s, new LinearLayout.LayoutParams(-1, -2));
        if (desc != null) {
            TextView d = Ui.text(this, desc, 12.5f, Ui.sub(this), false);
            d.setPadding(0, 0, dp(56), dp(8));
            parent.addView(d);
        }
    }

    private void saveSources() {
        StringBuilder clean = new StringBuilder();
        for (String line : sources.getText().toString().split("\\s+")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (!line.startsWith("http://") && !line.startsWith("https://")) line = "https://" + line;
            try {
                new URL(line);
            } catch (Exception ex) {
                Toast.makeText(this, "Geçersiz adres: " + line, Toast.LENGTH_LONG).show();
                return;
            }
            if (clean.length() > 0) clean.append('\n');
            clean.append(line);
        }
        CatalogRepo.prefs(this).edit().putString(CatalogRepo.KEY_SOURCES, clean.toString()).apply();
        sources.setText(clean);
        CatalogRepo.get(this).refreshAsync();
        Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean ok = getPackageManager().canRequestPackageInstalls();
        permStatus.setText(ok ? "✓ Uygulama yükleme izni verildi"
                : "✕ Uygulama yükleme izni verilmedi. Uygulama kurabilmek için gerekli.");
        permStatus.setTextColor(ok ? Ui.SUCCESS : Ui.DANGER);
        permBtn.setVisibility(ok ? View.GONE : View.VISIBLE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
