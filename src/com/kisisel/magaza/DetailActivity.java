package com.kisisel.magaza;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class DetailActivity extends Activity implements InstallManager.Listener {
    static final String EXTRA_PKG = "pkg";

    private AppEntry e;
    private Button primary, uninstall;
    private ProgressBar progress;
    private TextView status, installedRow;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refresh(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.styleBars(this);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        e = CatalogRepo.get(this).find(getIntent().getStringExtra(EXTRA_PKG));
        if (e == null) {
            Toast.makeText(this, "Uygulama bulunamadı", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setTitle(e.name);
        setContentView(build());
    }

    private int dp(float v) { return Ui.dp(this, v); }

    private View build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.bg(this));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(20), dp(20), dp(32));
        sv.addView(col);

        // Baslik
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setClipToOutline(true);
        icon.setBackground(Ui.round(Ui.chip(this), dp(18)));
        ImageLoader.get(this).load(e.iconUrl, icon, Ui.chip(this), dp(80));
        head.addView(icon, new LinearLayout.LayoutParams(dp(80), dp(80)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(16), 0, 0, 0);
        titles.addView(Ui.text(this, e.name, 22, Ui.text(this), true));
        if (!e.author.isEmpty()) titles.addView(Ui.text(this, e.author, 14, Ui.PRIMARY, true));
        String meta = e.category + (e.size > 0 ? " · " + Formatter.formatShortFileSize(this, e.size) : "")
                + (e.versionName.isEmpty() ? "" : " · v" + e.versionName);
        titles.addView(Ui.text(this, meta, 13, Ui.sub(this), false));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(head);

        // Butonlar
        LinearLayout btns = new LinearLayout(this);
        btns.setPadding(0, dp(20), 0, 0);
        uninstall = Ui.button(this, "Kaldır", Ui.DANGER, false);
        uninstall.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Actions.uninstall(DetailActivity.this, e); }
        });
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(0, dp(44), 1);
        ulp.rightMargin = dp(12);
        btns.addView(uninstall, ulp);
        primary = Ui.button(this, "", Ui.PRIMARY, true);
        primary.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Actions.perform(DetailActivity.this, e);
                refresh();
            }
        });
        btns.addView(primary, new LinearLayout.LayoutParams(0, dp(44), 1));
        col.addView(btns);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(6));
        plp.topMargin = dp(14);
        col.addView(progress, plp);
        status = Ui.text(this, "", 13, Ui.sub(this), false);
        col.addView(status);

        // Ekran goruntuleri
        if (!e.screenshots.isEmpty()) {
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(this);
            for (String s : e.screenshots) {
                ImageView iv = new ImageView(this);
                iv.setAdjustViewBounds(true);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setClipToOutline(true);
                iv.setBackground(Ui.round(Ui.chip(this), dp(12)));
                ImageLoader.get(this).load(s, iv, Ui.chip(this), dp(400));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(320));
                lp.rightMargin = dp(10);
                iv.setMinimumWidth(dp(150));
                row.addView(iv, lp);
            }
            hs.addView(row);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
            hlp.topMargin = dp(20);
            col.addView(hs, hlp);
        }

        if (!e.changelog.isEmpty()) section(col, "Yenilikler", e.changelog);
        section(col, "Hakkında", e.description.isEmpty() ? "Açıklama eklenmemiş." : e.description);

        // Bilgi tablosu
        TextView h = Ui.text(this, "Bilgiler", 17, Ui.text(this), true);
        h.setPadding(0, dp(24), 0, dp(8));
        col.addView(h);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setBackground(Ui.round(Ui.card(this), dp(14)));
        info.setPadding(dp(16), dp(8), dp(16), dp(8));
        row(info, "Paket adı", e.packageName);
        row(info, "Katalog sürümü", e.versionName + " (" + e.versionCode + ")");
        installedRow = row(info, "Yüklü sürüm", "");
        if (e.size > 0) row(info, "Boyut", Formatter.formatFileSize(this, e.size));
        row(info, "Doğrulama", e.sha256.isEmpty() ? "SHA-256 yok (önerilmez)" : "SHA-256 ile doğrulanır ✓");
        row(info, "Kaynak", e.sourceName);
        long upd = CatalogRepo.get(this).lastUpdated();
        if (upd > 0) row(info, "Katalog güncellendi",
                DateUtils.getRelativeTimeSpanString(upd).toString());
        col.addView(info);
        return sv;
    }

    private void section(LinearLayout col, String title, String body) {
        TextView h = Ui.text(this, title, 17, Ui.text(this), true);
        h.setPadding(0, dp(24), 0, dp(6));
        col.addView(h);
        TextView t = Ui.text(this, body, 15, Ui.text(this), false);
        t.setLineSpacing(0, 1.25f);
        t.setTextIsSelectable(true);
        col.addView(t);
    }

    private TextView row(LinearLayout parent, String k, String v) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, dp(8), 0, dp(8));
        TextView kv = Ui.text(this, k, 14, Ui.sub(this), false);
        r.addView(kv, new LinearLayout.LayoutParams(0, -2, 1));
        TextView vv = Ui.text(this, v, 14, Ui.text(this), false);
        vv.setGravity(Gravity.END);
        vv.setTextIsSelectable(true);
        r.addView(vv, new LinearLayout.LayoutParams(0, -2, 1.4f));
        parent.addView(r);
        return vv;
    }

    private void refresh() {
        if (e == null) return;
        Actions.bind(this, primary, e);
        String inst = CatalogRepo.installedVersionName(this, e.packageName);
        boolean installed = CatalogRepo.installedVersion(this, e.packageName) >= 0;
        uninstall.setVisibility(installed ? View.VISIBLE : View.GONE);
        installedRow.setText(installed ? (inst == null ? "?" : inst) : "Yüklü değil");

        InstallManager.Task t = InstallManager.get(this).task(e.packageName);
        if (t == null) {
            progress.setVisibility(View.GONE);
            status.setVisibility(View.GONE);
            return;
        }
        status.setVisibility(View.VISIBLE);
        status.setTextColor(t.state == InstallManager.FAILED ? Ui.DANGER : Ui.sub(this));
        progress.setVisibility(t.state == InstallManager.FAILED ? View.GONE : View.VISIBLE);
        progress.setIndeterminate(t.progress < 0);
        progress.setProgress(Math.max(0, t.progress));
        switch (t.state) {
            case InstallManager.QUEUED: status.setText("Sırada bekliyor…"); break;
            case InstallManager.DOWNLOADING:
                status.setText(t.total > 0
                        ? Formatter.formatShortFileSize(this, t.done) + " / " + Formatter.formatShortFileSize(this, t.total)
                        : "İndiriliyor… " + Formatter.formatShortFileSize(this, t.done));
                break;
            case InstallManager.VERIFYING: status.setText("Dosya doğrulanıyor…"); break;
            case InstallManager.INSTALLING: status.setText("Kuruluyor… (onay istenirse onayla)"); break;
            case InstallManager.FAILED: status.setText("Hata: " + t.error); break;
            default:
        }
    }

    @Override public void onTaskChanged(String pkg) {
        if (e != null && e.packageName.equals(pkg)) refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (e == null) return;
        InstallManager.get(this).addListener(this);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(pkgReceiver, f);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (e == null) return;
        InstallManager.get(this).removeListener(this);
        unregisterReceiver(pkgReceiver);
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
