package com.kisisel.magaza;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DetailActivity extends Activity implements InstallManager.Listener, CatalogRepo.Listener {
    static final String EXTRA_PKG = "pkg";

    private AppEntry e;
    private Button primary, secondary;
    private ProgressBar progress;
    private TextView status, installedInfo, signerState;
    private LinearLayout progressBox;
    private ImageButton cancelBtn;
    private boolean descExpanded, permsExpanded, contactExpanded;
    private LinearLayout descBox, permBox, contactBox;
    private final AppViews.Group group = new AppViews.Group();

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refresh(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        Ui.applyTheme(this);
        super.onCreate(b);
        Ui.styleWindow(this);
        e = CatalogRepo.get(this).find(getIntent().getStringExtra(EXTRA_PKG));
        if (e == null) {
            Toast.makeText(this, "Uygulama bulunamadı", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(build());
    }

    private int dp(float v) { return Ui.dp(this, v); }

    private View build() {
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.bg());

        // Ust cubuk
        LinearLayout bar = Ui.horizontal(this);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));
        ImageButton back = Ui.iconButton(this, Icons.BACK, Ui.text());
        back.setContentDescription("Geri");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        bar.addView(back, Ui.lp(dp(44), dp(44)));
        bar.addView(new View(this), Ui.weight(1));
        ImageButton share = Ui.iconButton(this, Icons.SHARE, Ui.text());
        share.setContentDescription("Paylaş");
        share.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Actions.share(DetailActivity.this, e); }
        });
        bar.addView(share, Ui.lp(dp(44), dp(44)));
        final ImageButton more = Ui.iconButton(this, Icons.MORE, Ui.text());
        more.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(more); }
        });
        bar.addView(more, Ui.lp(dp(44), dp(44)));
        root.addView(bar);

        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.vertical(this);
        col.setPadding(0, 0, 0, dp(32));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        header(col);
        stats(col);
        actions(col);
        if (!e.compatible()) incompatible(col);
        media(col);
        about(col);
        whatsNew(col);
        permissions(col);
        protect(col);
        contact(col);
        rails(col);
        info(col);
        return root;
    }

    // ---- Baslik ----
    private void header(LinearLayout col) {
        LinearLayout h = Ui.horizontal(this);
        h.setGravity(Gravity.TOP);
        h.setPadding(dp(20), dp(4), dp(20), 0);
        ImageView icon = Ui.icon(this, e, 76);
        icon.setElevation(dp(2));
        h.addView(icon, Ui.lp(dp(76), dp(76)));
        LinearLayout t = Ui.vertical(this);
        t.setPadding(dp(16), 0, 0, 0);
        TextView name = Ui.text(this, e.name, 23, Ui.text(), true);
        t.addView(name);
        if (!e.author.isEmpty()) {
            TextView au = Ui.text(this, e.author, 14.5f, Ui.primaryOn(), true);
            au.setPadding(0, dp(2), 0, 0);
            au.setBackground(Ui.selectable(this, 6));
            au.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showAuthorApps(); }
            });
            t.addView(au);
        }
        LinearLayout badges = Ui.horizontal(this);
        badges.setPadding(0, dp(6), 0, 0);
        if (!e.signer.isEmpty()) badges.addView(badge(Icons.SHIELD, "Doğrulanmış", Ui.SUCCESS));
        if (System.currentTimeMillis() - e.added < 14L * 86400000 && e.added > 0) {
            badges.addView(badge(Icons.STAR, "Yeni", Ui.WARNING), Ui.lp(-2, -2, dp(6), 0, 0, 0));
        }
        t.addView(badges);
        h.addView(t, Ui.weight(1));
        col.addView(h);
    }

    private View badge(int icon, String text, int color) {
        LinearLayout b = Ui.horizontal(this);
        b.setPadding(dp(8), dp(3), dp(10), dp(3));
        b.setBackground(Ui.round((color & 0x00FFFFFF) | 0x22000000, dp(10)));
        View ic = new View(this);
        ic.setBackground(new Icons(icon, color));
        b.addView(ic, Ui.lp(dp(14), dp(14)));
        TextView t = Ui.text(this, text, 12, color, true);
        t.setPadding(dp(4), 0, 0, 0);
        b.addView(t);
        return b;
    }

    // ---- Ozet satiri ----
    private void stats(LinearLayout col) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(dp(12), 0, dp(12), 0);
        stat(row, e.versionName.isEmpty() ? "?" : e.versionName, "Sürüm", false);
        if (e.size > 0) stat(row, Ui.size(this, e.size), "Boyut", true);
        stat(row, Ui.androidName(e.minSdk) + "+", "Android", true);
        stat(row, e.category, "Kategori", true);
        if (e.updated > 0) stat(row, DateFormat.getMediumDateFormat(this).format(new Date(e.updated)), "Güncellendi", true);
        hs.addView(row);
        col.addView(hs, Ui.lp(-1, -2, 0, dp(20), 0, 0));
    }

    private void stat(LinearLayout row, String value, String label, boolean divider) {
        if (divider) {
            View d = new View(this);
            d.setBackgroundColor(Ui.border());
            row.addView(d, Ui.lp(Math.max(1, dp(0.8f)), dp(28)));
        }
        LinearLayout s = Ui.vertical(this);
        s.setGravity(Gravity.CENTER_HORIZONTAL);
        s.setPadding(dp(16), 0, dp(16), 0);
        TextView v = Ui.text(this, value, 14.5f, Ui.text(), true);
        v.setSingleLine(true);
        s.addView(v);
        s.addView(Ui.text(this, label, 12, Ui.muted(), false));
        row.addView(s);
    }

    // ---- Dugmeler ve ilerleme ----
    private void actions(LinearLayout col) {
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(dp(20), dp(20), dp(20), 0);
        secondary = Ui.button(this, "Kaldır", Ui.primaryOn(), false);
        secondary.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmUninstall(); }
        });
        LinearLayout.LayoutParams slp = Ui.weight(1);
        slp.height = dp(44);
        slp.rightMargin = dp(12);
        row.addView(secondary, slp);
        primary = Ui.button(this, "", Ui.PRIMARY, true);
        primary.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Actions.perform(DetailActivity.this, e);
                refresh();
            }
        });
        LinearLayout.LayoutParams plp = Ui.weight(1);
        plp.height = dp(44);
        row.addView(primary, plp);
        col.addView(row);

        progressBox = Ui.vertical(this);
        progressBox.setPadding(dp(20), dp(14), dp(12), 0);
        LinearLayout pr = Ui.horizontal(this);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.PRIMARY));
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.PRIMARY));
        pr.addView(progress, new LinearLayout.LayoutParams(0, dp(6), 1));
        cancelBtn = Ui.iconButton(this, Icons.CLOSE, Ui.muted());
        cancelBtn.setContentDescription("İptal");
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { InstallManager.get(DetailActivity.this).cancel(e.packageName); }
        });
        pr.addView(cancelBtn, Ui.lp(dp(40), dp(40), dp(4), 0, 0, 0));
        progressBox.addView(pr);
        status = Ui.text(this, "", 13, Ui.muted(), false);
        progressBox.addView(status);
        col.addView(progressBox);
    }

    private void incompatible(LinearLayout col) {
        LinearLayout c = Ui.horizontal(this);
        c.setGravity(Gravity.TOP);
        c.setBackground(Ui.round((Ui.WARNING & 0x00FFFFFF) | 0x22000000, dp(14)));
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        View ic = new View(this);
        ic.setBackground(new Icons(Icons.WARNING, Ui.WARNING));
        c.addView(ic, Ui.lp(dp(22), dp(22)));
        TextView t = Ui.text(this, "Bu uygulama cihazınla uyumlu değil. Android " + Ui.androidName(e.minSdk)
                + " veya üzeri gerekiyor; cihazında Android " + Build.VERSION.RELEASE + " var.", 13.5f, Ui.text(), false);
        t.setPadding(dp(12), 0, 0, 0);
        c.addView(t, Ui.weight(1));
        col.addView(c, Ui.lp(-1, -2, dp(20), dp(16), dp(20), 0));
    }

    // ---- Gorseller ----
    private void media(LinearLayout col) {
        final ArrayList<String> imgs = new ArrayList<>(e.screenshots);
        if (imgs.isEmpty() && e.bannerUrl != null) imgs.add(e.bannerUrl);
        if (imgs.isEmpty()) return;
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(dp(20), 0, dp(20), 0);
        int screen = getResources().getDisplayMetrics().widthPixels;
        for (int i = 0; i < imgs.size(); i++) {
            final int idx = i;
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setClipToOutline(true);
            iv.setBackground(Ui.round(Ui.soft(), dp(14)));
            boolean banner = imgs.get(i).equals(e.bannerUrl);
            int w = banner ? Math.min(screen - dp(40), dp(480)) : dp(150);
            int h = banner ? (int) (w / 2.048f) : dp(280);
            ImageLoader.get(this).load(imgs.get(i), iv, Ui.soft(), Math.max(w, h));
            iv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivity(new Intent(DetailActivity.this, ScreenshotActivity.class)
                            .putStringArrayListExtra(ScreenshotActivity.EXTRA_URLS, imgs)
                            .putExtra(ScreenshotActivity.EXTRA_INDEX, idx));
                }
            });
            row.addView(iv, Ui.lp(w, h, 0, 0, dp(10), 0));
        }
        hs.addView(row);
        col.addView(hs, Ui.lp(-1, -2, 0, dp(22), 0, 0));
    }

    // ---- Bolum basligi (istege bagli ok ile) ----
    private LinearLayout section(LinearLayout col, String title, final Runnable onArrow) {
        LinearLayout h = Ui.horizontal(this);
        h.setPadding(dp(20), dp(26), dp(12), dp(8));
        h.addView(Ui.text(this, title, 18, Ui.text(), true), Ui.weight(1));
        if (onArrow != null) {
            ImageButton arrow = Ui.iconButton(this, Icons.CHEVRON, Ui.muted());
            arrow.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { onArrow.run(); }
            });
            h.addView(arrow, Ui.lp(dp(40), dp(40)));
            h.setBackground(Ui.selectable(this, 0));
            h.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { onArrow.run(); }
            });
        }
        col.addView(h);
        LinearLayout body = Ui.vertical(this);
        body.setPadding(dp(20), 0, dp(20), 0);
        col.addView(body);
        return body;
    }

    private void about(LinearLayout col) {
        descBox = section(col, "Bu uygulama hakkında", new Runnable() {
            @Override public void run() {
                descExpanded = !descExpanded;
                renderAbout();
            }
        });
        renderAbout();
    }

    private void renderAbout() {
        descBox.removeAllViews();
        if (!e.shortDescription.isEmpty()) {
            descBox.addView(Ui.text(this, e.shortDescription, 15, Ui.text(), false));
        }
        if (!e.description.isEmpty()) {
            TextView d = Ui.text(this, e.description, 14.5f, Ui.muted(), false);
            d.setLineSpacing(0, 1.3f);
            d.setPadding(0, e.shortDescription.isEmpty() ? 0 : dp(8), 0, 0);
            if (!descExpanded) {
                d.setMaxLines(3);
                d.setEllipsize(TextUtils.TruncateAt.END);
            } else {
                d.setTextIsSelectable(true);
            }
            descBox.addView(d);
            if (!descExpanded) {
                TextView more = Ui.text(this, "Devamını oku", 14, Ui.primaryOn(), true);
                more.setPadding(0, dp(6), 0, 0);
                more.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        descExpanded = true;
                        renderAbout();
                    }
                });
                descBox.addView(more);
            }
        }
        {
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout tags = Ui.horizontal(this);
            List<String> all = new ArrayList<>();
            all.add(e.category);
            for (String t : e.tags) if (!t.equalsIgnoreCase(e.category)) all.add("#" + t);
            for (String t : all) {
                TextView c = Ui.chip(this, t, false);
                c.setTextSize(12.5f);
                tags.addView(c, Ui.lp(-2, -2, 0, 0, dp(8), 0));
            }
            hs.addView(tags);
            descBox.addView(hs, Ui.lp(-1, -2, 0, dp(14), 0, 0));
        }
    }

    private void whatsNew(LinearLayout col) {
        if (e.changelog.isEmpty()) return;
        LinearLayout b = section(col, "Yenilikler", null);
        if (e.updated > 0) {
            b.addView(Ui.text(this, "Son güncelleme: " + DateFormat.getLongDateFormat(this).format(new Date(e.updated))
                    + "  •  Sürüm " + e.versionName, 12.5f, Ui.muted(), false));
        }
        TextView t = Ui.text(this, e.changelog, 14.5f, Ui.text(), false);
        t.setLineSpacing(0, 1.3f);
        t.setPadding(0, dp(6), 0, 0);
        b.addView(t);
    }

    // ---- Izinler ----
    private void permissions(LinearLayout col) {
        permBox = section(col, "İzinler", e.permissions.size() > 4 ? new Runnable() {
            @Override public void run() {
                permsExpanded = !permsExpanded;
                renderPermissions();
            }
        } : null);
        renderPermissions();
    }

    private void renderPermissions() {
        permBox.removeAllViews();
        if (e.permissions.isEmpty()) {
            permBox.addView(Ui.text(this, "Bu uygulama özel bir izin istemiyor.", 14, Ui.muted(), false));
            return;
        }
        LinearLayout card = Ui.vertical(this);
        card.setBackground(Ui.card(this, 16));
        card.setPadding(dp(16), dp(6), dp(16), dp(6));
        // once hassas izinler
        List<String> sorted = new ArrayList<>();
        for (String p : e.permissions) if (Permissions.get(p).sensitive) sorted.add(p);
        for (String p : e.permissions) if (!Permissions.get(p).sensitive) sorted.add(p);
        int limit = permsExpanded ? sorted.size() : Math.min(4, sorted.size());
        for (int i = 0; i < limit; i++) {
            Permissions.Info info = Permissions.get(sorted.get(i));
            LinearLayout r = Ui.horizontal(this);
            r.setGravity(Gravity.TOP);
            r.setPadding(0, dp(10), 0, dp(10));
            View ic = new View(this);
            ic.setBackground(new Icons(info.sensitive ? Icons.WARNING : Icons.CHECK,
                    info.sensitive ? Ui.WARNING : Ui.SUCCESS));
            r.addView(ic, Ui.lp(dp(18), dp(18), 0, dp(2), 0, 0));
            LinearLayout t = Ui.vertical(this);
            t.setPadding(dp(12), 0, 0, 0);
            t.addView(Ui.text(this, info.title, 14.5f, Ui.text(), true));
            t.addView(Ui.text(this, info.desc, 13, Ui.muted(), false));
            r.addView(t, Ui.weight(1));
            card.addView(r);
            if (i < limit - 1) card.addView(Ui.divider(this));
        }
        permBox.addView(card);
        if (sorted.size() > 4) {
            TextView more = Ui.text(this, permsExpanded ? "Daha az göster" : "Tüm izinleri gör (" + sorted.size() + ")",
                    14, Ui.primaryOn(), true);
            more.setPadding(0, dp(10), 0, 0);
            more.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    permsExpanded = !permsExpanded;
                    renderPermissions();
                }
            });
            permBox.addView(more);
        }
    }

    // ---- Fast Protect ----
    private void protect(LinearLayout col) {
        LinearLayout b = section(col, "Fast Protect güvenliği", null);
        LinearLayout card = Ui.vertical(this);
        card.setBackground(Ui.card(this, 16));
        card.setPadding(dp(16), dp(10), dp(16), dp(10));
        protectRow(card, !e.sha256.isEmpty(), "Dosya bütünlüğü",
                e.sha256.isEmpty() ? "Katalogda SHA-256 özeti yok" : "İndirilen dosya SHA-256 özetiyle karşılaştırılır");
        protectRow(card, !e.signer.isEmpty(), "Geliştirici imzası",
                e.signer.isEmpty() ? "Katalogda imza parmak izi yok" : "İmza parmak izi: " + fingerprint(e.signer));
        protectRow(card, true, "Paket kimliği", "APK'daki paket adı katalogla eşleşmeli: " + e.packageName);
        signerState = Ui.text(this, "", 13, Ui.muted(), false);
        signerState.setPadding(dp(30), dp(4), 0, dp(6));
        card.addView(signerState);
        b.addView(card);
    }

    private void protectRow(LinearLayout card, boolean ok, String title, String desc) {
        LinearLayout r = Ui.horizontal(this);
        r.setGravity(Gravity.TOP);
        r.setPadding(0, dp(8), 0, dp(8));
        View ic = new View(this);
        ic.setBackground(new Icons(ok ? Icons.SHIELD : Icons.WARNING, ok ? Ui.SUCCESS : Ui.WARNING));
        r.addView(ic, Ui.lp(dp(18), dp(18), 0, dp(2), 0, 0));
        LinearLayout t = Ui.vertical(this);
        t.setPadding(dp(12), 0, 0, 0);
        t.addView(Ui.text(this, title, 14.5f, Ui.text(), true));
        TextView d = Ui.text(this, desc, 12.5f, Ui.muted(), false);
        d.setTextIsSelectable(true);
        t.addView(d);
        r.addView(t, Ui.weight(1));
        card.addView(r);
    }

    static String fingerprint(String hex) {
        StringBuilder sb = new StringBuilder();
        String h = hex.toUpperCase(java.util.Locale.ROOT);
        for (int i = 0; i < h.length() && i < 32; i += 2) {
            if (i > 0) sb.append(':');
            sb.append(h, i, i + 2);
        }
        return sb.append(h.length() > 32 ? "…" : "").toString();
    }

    // ---- Gelistirici iletisimi ----
    private void contact(LinearLayout col) {
        if (e.website.isEmpty() && e.email.isEmpty() && e.privacy.isEmpty()) return;
        contactBox = section(col, "Geliştirici iletişimi", new Runnable() {
            @Override public void run() {
                contactExpanded = !contactExpanded;
                renderContact();
            }
        });
        contactExpanded = true;
        renderContact();
    }

    private void renderContact() {
        contactBox.removeAllViews();
        if (!contactExpanded) return;
        if (!e.website.isEmpty()) link(contactBox, Icons.GLOBE, "Web sitesi", e.website, Uri.parse(e.website));
        if (!e.email.isEmpty()) link(contactBox, Icons.MAIL, "E-posta", e.email, Uri.parse("mailto:" + e.email));
        if (!e.privacy.isEmpty()) link(contactBox, Icons.LOCK, "Gizlilik politikası", e.privacy, Uri.parse(e.privacy));
    }

    private void link(LinearLayout parent, int icon, String title, String sub, final Uri uri) {
        LinearLayout r = Ui.horizontal(this);
        r.setPadding(0, dp(10), 0, dp(10));
        r.setBackground(Ui.selectable(this, 8));
        View ic = new View(this);
        ic.setBackground(new Icons(icon, Ui.muted()));
        r.addView(ic, Ui.lp(dp(22), dp(22)));
        LinearLayout t = Ui.vertical(this);
        t.setPadding(dp(16), 0, 0, 0);
        t.addView(Ui.text(this, title, 15, Ui.text(), false));
        TextView s = Ui.text(this, sub, 12.5f, Ui.muted(), false);
        s.setSingleLine(true);
        s.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        t.addView(s);
        r.addView(t, Ui.weight(1));
        View op = new View(this);
        op.setBackground(new Icons(Icons.OPEN, Ui.muted()));
        r.addView(op, Ui.lp(dp(18), dp(18)));
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ex) {
                    Toast.makeText(DetailActivity.this, "Açılamadı", Toast.LENGTH_SHORT).show();
                }
            }
        });
        parent.addView(r);
    }

    // ---- Raflar ----
    private void rails(LinearLayout col) {
        CatalogRepo repo = CatalogRepo.get(this);
        List<AppEntry> author = repo.fromAuthor(e.author, e.packageName);
        if (!author.isEmpty()) rail(col, e.author + " geliştiricisinden diğer", author);
        List<AppEntry> sim = repo.similar(e, 10);
        sim.removeAll(author);
        if (!sim.isEmpty()) rail(col, "Bunlar da ilgini çekebilir", sim);
    }

    private void rail(LinearLayout col, String title, List<AppEntry> list) {
        TextView h = Ui.text(this, title, 18, Ui.text(), true);
        h.setPadding(dp(20), dp(26), dp(20), dp(6));
        col.addView(h);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.horizontal(this);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(14), 0, dp(14), 0);
        for (AppEntry x : list) row.addView(group.add(new AppViews.Tile(this, x)).view, Ui.lp(-2, -2, 0, 0, dp(4), 0));
        hs.addView(row);
        col.addView(hs);
    }

    // ---- Uygulama bilgileri ----
    private void info(LinearLayout col) {
        LinearLayout b = section(col, "Uygulama bilgileri", null);
        LinearLayout card = Ui.vertical(this);
        card.setBackground(Ui.card(this, 16));
        card.setPadding(dp(16), dp(6), dp(16), dp(6));
        row(card, "Paket adı", e.packageName);
        row(card, "Sürüm", e.versionName + " (" + e.versionCode + ")");
        installedInfo = row(card, "Yüklü sürüm", "");
        if (e.targetSdk > 0) row(card, "Hedef Android", Ui.androidName(e.targetSdk) + " (API " + e.targetSdk + ")");
        row(card, "En düşük Android", Ui.androidName(e.minSdk) + " (API " + e.minSdk + ")");
        if (e.size > 0) row(card, "İndirme boyutu", Ui.size(this, e.size));
        if (e.added > 0) row(card, "Mağazaya eklendi", DateFormat.getMediumDateFormat(this).format(new Date(e.added)));
        row(card, "Kaynak", e.sourceName);
        b.addView(card);
    }

    private TextView row(LinearLayout parent, String k, String v) {
        LinearLayout r = Ui.horizontal(this);
        r.setPadding(0, dp(9), 0, dp(9));
        r.addView(Ui.text(this, k, 13.5f, Ui.muted(), false), Ui.weight(1));
        TextView vv = Ui.text(this, v, 13.5f, Ui.text(), false);
        vv.setGravity(Gravity.END);
        vv.setTextIsSelectable(true);
        r.addView(vv, Ui.weight(1.4f));
        parent.addView(r);
        return vv;
    }

    // ---- Menu ----
    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        boolean installed = CatalogRepo.installedVersion(this, e.packageName) >= 0;
        m.getMenu().add(0, 1, 0, "Paylaş");
        m.getMenu().add(0, 2, 1, "İndirme bağlantısını kopyala");
        if (installed && !e.packageName.equals(getPackageName())) m.getMenu().add(0, 3, 2, "Kaldır");
        if (installed) m.getMenu().add(0, 4, 3, "Uygulama ayarları (sistem)");
        m.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(android.view.MenuItem item) {
                switch (item.getItemId()) {
                    case 1: Actions.share(DetailActivity.this, e); break;
                    case 2:
                        ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                                .setPrimaryClip(android.content.ClipData.newPlainText("apk", e.apkUrl));
                        Toast.makeText(DetailActivity.this, "Kopyalandı", Toast.LENGTH_SHORT).show();
                        break;
                    case 3: confirmUninstall(); break;
                    case 4:
                        startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + e.packageName)));
                        break;
                    default:
                }
                return true;
            }
        });
        m.show();
    }

    private void confirmUninstall() {
        if (e.packageName.equals(getPackageName())) return;
        new AlertDialog.Builder(this).setTitle(e.name + " kaldırılsın mı?")
                .setMessage("Uygulama ve verileri bu cihazdan silinir.")
                .setPositiveButton("Kaldır", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        Actions.uninstall(DetailActivity.this, e);
                    }
                })
                .setNegativeButton("Vazgeç", null).show();
    }

    private void showAuthorApps() {
        List<AppEntry> list = CatalogRepo.get(this).fromAuthor(e.author, null);
        if (list.size() <= 1) return;
        final String[] names = new String[list.size()];
        final List<AppEntry> fl = list;
        for (int i = 0; i < list.size(); i++) names[i] = list.get(i).name;
        new AlertDialog.Builder(this).setTitle(e.author)
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        if (!fl.get(w).packageName.equals(e.packageName)) Actions.openDetail(DetailActivity.this, fl.get(w));
                    }
                }).show();
    }

    // ---- Durum ----
    private void refresh() {
        if (e == null || primary == null) return;
        Actions.bind(this, primary, e, false);
        boolean installed = CatalogRepo.installedVersion(this, e.packageName) >= 0;
        boolean self = e.packageName.equals(getPackageName());
        InstallManager.Task t = InstallManager.get(this).task(e.packageName);
        secondary.setVisibility(installed && !self && t == null ? View.VISIBLE : View.GONE);
        String iv = CatalogRepo.installedVersionName(this, e.packageName);
        installedInfo.setText(installed ? (iv == null ? "?" : iv) : "Yüklü değil");

        if (installed && !e.signer.isEmpty()) {
            String s = InstallManager.get(this).installedSigner(e.packageName);
            boolean match = s != null && s.equals(e.signer);
            signerState.setText(match ? "✓ Cihazındaki sürüm aynı geliştirici tarafından imzalanmış"
                    : "✕ Cihazındaki sürüm farklı bir imzaya sahip. Mağazadan güncellenemez.");
            signerState.setTextColor(match ? Ui.SUCCESS : Ui.DANGER);
            signerState.setVisibility(View.VISIBLE);
        } else {
            signerState.setVisibility(View.GONE);
        }

        if (t == null) {
            progressBox.setVisibility(View.GONE);
            return;
        }
        progressBox.setVisibility(View.VISIBLE);
        boolean failed = t.state == InstallManager.FAILED;
        progress.setVisibility(failed ? View.GONE : View.VISIBLE);
        cancelBtn.setVisibility(t.state == InstallManager.DOWNLOADING || t.state == InstallManager.QUEUED ? View.VISIBLE : View.GONE);
        progress.setIndeterminate(t.progress < 0);
        progress.setProgress(Math.max(0, t.progress));
        status.setTextColor(failed ? Ui.DANGER : Ui.muted());
        switch (t.state) {
            case InstallManager.QUEUED: status.setText("Sırada bekliyor…"); break;
            case InstallManager.DOWNLOADING:
                status.setText(t.total > 0 ? Ui.size(this, t.done) + " / " + Ui.size(this, t.total)
                        + (t.speed > 0 ? "  •  " + Ui.size(this, t.speed) + "/sn" : "")
                        : "İndiriliyor… " + Ui.size(this, t.done));
                break;
            case InstallManager.VERIFYING: status.setText("Fast Protect: dosya ve imza doğrulanıyor…"); break;
            case InstallManager.INSTALLING: status.setText("Yükleniyor… (onay istenirse onayla)"); break;
            case InstallManager.FAILED: status.setText(t.error); break;
            default:
        }
    }

    @Override public void onTaskChanged(String pkg) {
        if (e != null && e.packageName.equals(pkg)) refresh();
        group.rebind(pkg);
    }

    @Override public void onCatalogChanged() { refresh(); }

    @Override
    protected void onResume() {
        super.onResume();
        if (e == null) return;
        InstallManager.get(this).addListener(this);
        CatalogRepo.get(this).addListener(this);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(pkgReceiver, f);
        refresh();
        group.rebind(null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (e == null) return;
        InstallManager.get(this).removeListener(this);
        CatalogRepo.get(this).removeListener(this);
        unregisterReceiver(pkgReceiver);
    }
}
