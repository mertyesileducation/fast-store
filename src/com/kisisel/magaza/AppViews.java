package com.kisisel.magaza;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Uygulama listelerinde kullanilan tekrar kullanilabilir gorunumler. */
final class AppViews {
    private AppViews() {}

    /** Kurulum durumu degistikce yeniden baglanabilen gorunum. */
    interface Bindable {
        String pkg();
        void bind();
    }

    /** Sayfalarin kendi Bindable listelerini tutmasi icin. */
    static final class Group {
        final List<Bindable> items = new ArrayList<>();
        void clear() { items.clear(); }
        <T extends Bindable> T add(T b) { items.add(b); return b; }
        void rebind(String pkg) {
            for (Bindable b : items) if (pkg == null || b.pkg().equals(pkg)) b.bind();
        }
    }

    // ------------------------------------------------------------------
    /** Liste satiri: ikon, ad, alt bilgi, ilerleme ve dugme. */
    static final class Row implements Bindable {
        final LinearLayout view;
        final AppEntry e;
        private final Activity a;
        private final TextView sub;
        private final Button btn;
        private final ProgressBar bar;
        private final boolean showChangelog;
        private final TextView changelog;

        Row(final Activity a, final AppEntry e, boolean showChangelog) {
            this.a = a;
            this.e = e;
            this.showChangelog = showChangelog;
            view = Ui.vertical(a);
            view.setBackground(Ui.selectable(a, 0));
            view.setPadding(dp(16), dp(10), dp(12), dp(10));
            view.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { Actions.openDetail(a, e); }
            });
            LinearLayout top = Ui.horizontal(a);
            top.addView(Ui.icon(a, e, 52), Ui.lp(dp(52), dp(52)));
            LinearLayout mid = Ui.vertical(a);
            mid.setPadding(dp(14), 0, dp(8), 0);
            TextView name = Ui.text(a, e.name, 16, Ui.text(), true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            mid.addView(name);
            sub = Ui.text(a, "", 13, Ui.muted(), false);
            sub.setMaxLines(2);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            mid.addView(sub);
            bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.PRIMARY));
            bar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.PRIMARY));
            mid.addView(bar, Ui.lp(-1, dp(4), 0, dp(6), 0, 0));
            top.addView(mid, Ui.weight(1));
            btn = Ui.button(a, "", Ui.PRIMARY, true);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Actions.perform(a, e);
                    bind();
                }
            });
            top.addView(btn, Ui.lp(-2, dp(36)));
            view.addView(top);
            changelog = Ui.text(a, "", 13, Ui.muted(), false);
            changelog.setPadding(dp(66), dp(6), dp(8), 0);
            changelog.setMaxLines(3);
            changelog.setEllipsize(TextUtils.TruncateAt.END);
            if (showChangelog && !e.changelog.isEmpty()) {
                changelog.setText(e.changelog);
                view.addView(changelog);
            }
            bind();
        }

        private int dp(float v) { return Ui.dp(a, v); }

        @Override public String pkg() { return e.packageName; }

        @Override
        public void bind() {
            InstallManager.Task t = InstallManager.get(a).task(e.packageName);
            String installed = CatalogRepo.installedVersionName(a, e.packageName);
            StringBuilder s = new StringBuilder();
            if (t != null && t.state == InstallManager.FAILED) {
                sub.setTextColor(Ui.DANGER);
                s.append(t.error);
            } else if (t != null) {
                sub.setTextColor(Ui.muted());
                if (t.state == InstallManager.DOWNLOADING && t.total > 0) {
                    s.append(Ui.size(a, t.done)).append(" / ").append(Ui.size(a, t.total));
                } else if (t.state == InstallManager.VERIFYING) {
                    s.append("Fast Protect doğruluyor…");
                } else if (t.state == InstallManager.INSTALLING) {
                    s.append("Yükleniyor…");
                } else {
                    s.append("Sırada bekliyor…");
                }
            } else {
                sub.setTextColor(Ui.muted());
                if (installed != null && CatalogRepo.hasUpdate(a, e)) {
                    s.append(installed).append(" → ").append(e.versionName);
                    if (e.size > 0) s.append("  •  ").append(Ui.size(a, e.size));
                } else {
                    if (!e.author.isEmpty()) s.append(e.author).append("  •  ");
                    s.append(e.category);
                    if (e.size > 0) s.append("  •  ").append(Ui.size(a, e.size));
                    if (installed != null) s.append("  •  Yüklü");
                    if (!e.compatible()) s.append("\nAndroid ").append(Ui.androidName(e.minSdk)).append("+ gerekli");
                }
            }
            sub.setText(s);
            boolean busy = t != null && t.state != InstallManager.FAILED;
            bar.setVisibility(busy ? View.VISIBLE : View.GONE);
            if (busy) {
                bar.setIndeterminate(t.progress < 0);
                bar.setProgress(Math.max(0, t.progress));
            }
            Actions.bind(a, btn, e, true);
        }
    }

    // ------------------------------------------------------------------
    /** Yatay raflardaki kucuk kart. */
    static final class Tile implements Bindable {
        final LinearLayout view;
        final AppEntry e;
        private final Activity a;
        private final TextView meta;

        Tile(final Activity a, final AppEntry e) {
            this.a = a;
            this.e = e;
            view = Ui.vertical(a);
            view.setPadding(Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 8));
            view.setBackground(Ui.selectable(a, 14));
            view.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { Actions.openDetail(a, e); }
            });
            view.addView(Ui.icon(a, e, 84), Ui.lp(Ui.dp(a, 84), Ui.dp(a, 84)));
            TextView name = Ui.text(a, e.name, 13.5f, Ui.text(), false);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(0, Ui.dp(a, 8), 0, 0);
            view.addView(name, Ui.lp(Ui.dp(a, 84), -2));
            meta = Ui.text(a, "", 12, Ui.muted(), false);
            meta.setSingleLine(true);
            view.addView(meta, Ui.lp(Ui.dp(a, 84), -2));
            bind();
        }

        @Override public String pkg() { return e.packageName; }

        @Override
        public void bind() {
            InstallManager.Task t = InstallManager.get(a).task(e.packageName);
            if (t != null && t.state != InstallManager.FAILED) {
                meta.setText(t.progress >= 0 ? "İndiriliyor %" + t.progress : "Yükleniyor…");
                meta.setTextColor(Ui.primaryOn());
            } else if (CatalogRepo.hasUpdate(a, e)) {
                meta.setText("Güncelleme var");
                meta.setTextColor(Ui.primaryOn());
            } else if (CatalogRepo.installedVersion(a, e.packageName) >= 0) {
                meta.setText("Yüklü ✓");
                meta.setTextColor(Ui.SUCCESS);
            } else {
                meta.setText(e.size > 0 ? Ui.size(a, e.size) : e.category);
                meta.setTextColor(Ui.muted());
            }
        }
    }

    // ------------------------------------------------------------------
    /** Vitrin karti: afis + ikon + ad + dugme. */
    static final class Feature implements Bindable {
        final FrameLayout view;
        final AppEntry e;
        private final Activity a;
        private final Button btn;

        Feature(final Activity a, final AppEntry e, int widthPx) {
            this.a = a;
            this.e = e;
            int h = (int) (widthPx / 2.048f);
            view = new FrameLayout(a);
            view.setClipToOutline(true);
            view.setBackground(Ui.round(Ui.soft(), Ui.dp(a, 20)));
            view.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { Actions.openDetail(a, e); }
            });

            View banner;
            if (e.bannerUrl != null) {
                ImageView iv = new ImageView(a);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ImageLoader.get(a).load(e.bannerUrl, iv, Ui.soft(), widthPx);
                banner = iv;
            } else {
                // afis yoksa uretilmis gecis
                View g = new View(a);
                g.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[]{Ui.ACCENT, Ui.PRIMARY}));
                banner = g;
            }
            view.addView(banner, new FrameLayout.LayoutParams(-1, h));

            // alt bilgi seridi
            LinearLayout bottom = Ui.horizontal(a);
            bottom.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 12), Ui.dp(a, 12));
            bottom.setBackgroundColor(Ui.surface());
            bottom.addView(Ui.icon(a, e, 44), Ui.lp(Ui.dp(a, 44), Ui.dp(a, 44)));
            LinearLayout texts = Ui.vertical(a);
            texts.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 8), 0);
            TextView n = Ui.text(a, e.name, 15.5f, Ui.text(), true);
            n.setSingleLine(true);
            texts.addView(n);
            TextView s = Ui.text(a, e.author.isEmpty() ? e.category : e.author + "  •  " + e.category, 12.5f, Ui.muted(), false);
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(s);
            bottom.addView(texts, Ui.weight(1));
            btn = Ui.button(a, "", Ui.PRIMARY, true);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Actions.perform(a, e);
                    bind();
                }
            });
            bottom.addView(btn, Ui.lp(-2, Ui.dp(a, 36)));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            blp.topMargin = h;
            view.addView(bottom, blp);

            if (e.featured) {
                TextView badge = Ui.text(a, "ÖNE ÇIKAN", 10.5f, Color.WHITE, true);
                badge.setLetterSpacing(0.08f);
                badge.setPadding(Ui.dp(a, 8), Ui.dp(a, 3), Ui.dp(a, 8), Ui.dp(a, 3));
                badge.setBackground(Ui.round(0x99000000, Ui.dp(a, 6)));
                FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
                p.setMargins(Ui.dp(a, 12), Ui.dp(a, 12), 0, 0);
                view.addView(badge, p);
            }
            bind();
        }

        @Override public String pkg() { return e.packageName; }

        @Override public void bind() { Actions.bind(a, btn, e, true); }
    }
}
