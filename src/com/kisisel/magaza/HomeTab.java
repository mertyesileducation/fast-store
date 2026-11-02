package com.kisisel.magaza;

import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

/** Ana sayfa: vitrin, kategoriler ve uygulama raflari. */
final class HomeTab extends Tab {
    private LinearLayout col;
    private ScrollView scroll;
    private String category; // null: tumu
    private HorizontalScrollView carousel;
    private LinearLayout dots;
    private int featureCount, featurePage, featureWidth;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable autoAdvance = new Runnable() {
        @Override public void run() {
            if (carousel != null && featureCount > 1) {
                featurePage = (featurePage + 1) % featureCount;
                carousel.smoothScrollTo(featurePage * (featureWidth + dp(12)), 0);
                updateDots();
            }
            h.postDelayed(this, 6000);
        }
    };

    HomeTab(MainActivity a) { super(a); }

    @Override
    View build() {
        scroll = new ScrollView(a);
        scroll.setBackgroundColor(Ui.bg());
        col = Ui.vertical(a);
        col.setPadding(0, dp(4), 0, dp(24));
        scroll.addView(col);
        return scroll;
    }

    @Override
    void render() {
        if (col == null) return;
        h.removeCallbacks(autoAdvance);
        col.removeAllViews();
        group.clear();
        carousel = null;
        CatalogRepo repo = CatalogRepo.get(a);

        if (repo.isEmpty()) {
            if (repo.isLoading() || repo.lastError() == null) skeleton();
            else error(repo.lastError());
            return;
        }

        selfUpdateBanner();
        if (category == null) {
            updatesCard();
            featured(repo.featured());
        }
        categoryChips(repo);

        if (category != null) {
            List<AppEntry> list = repo.inCategory(category);
            col.addView(Ui.title(a, category + " (" + list.size() + ")"));
            for (AppEntry e : list) col.addView(group.add(new AppViews.Row(a, e, false)).view);
        } else {
            if (repo.apps().size() >= 2) {
                rail("Yeni eklenenler", repo.newest(10));
                rail("Son güncellenenler", repo.recentlyUpdated(10));
            }
            for (Map.Entry<String, List<AppEntry>> m : repo.byCategory().entrySet()) {
                rail(m.getKey(), m.getValue());
            }
            col.addView(Ui.title(a, "Tüm uygulamalar"));
            for (AppEntry e : repo.apps()) col.addView(group.add(new AppViews.Row(a, e, false)).view);
        }
        footer(repo);
        if (carousel != null && featureCount > 1) h.postDelayed(autoAdvance, 6000);
    }

    // ---- Bolumler ----
    private void selfUpdateBanner() {
        AppEntry self = CatalogRepo.get(a).find(a.getPackageName());
        if (self == null || !CatalogRepo.hasUpdate(a, self)) return;
        LinearLayout c = Ui.horizontal(a);
        c.setBackground(Ui.round(Ui.soft(), dp(16)));
        c.setPadding(dp(16), dp(14), dp(12), dp(14));
        LinearLayout t = Ui.vertical(a);
        t.addView(Ui.text(a, "Fast Store " + self.versionName + " hazır", 15, Ui.text(), true));
        t.addView(Ui.text(a, "Yeni özellikler ve iyileştirmeler için güncelle.", 13, Ui.muted(), false));
        c.addView(t, Ui.weight(1));
        Button b = Ui.button(a, "", Ui.PRIMARY, true);
        AppViews.Bindable bb = bindButton(b, self);
        group.add(bb);
        c.addView(b, Ui.lp(-2, dp(36)));
        col.addView(c, Ui.lp(-1, -2, dp(16), dp(8), dp(16), dp(4)));
    }

    private void updatesCard() {
        List<AppEntry> ups = CatalogRepo.get(a).updates();
        int n = 0;
        for (AppEntry e : ups) if (!e.packageName.equals(a.getPackageName())) n++;
        if (n == 0) return;
        LinearLayout c = Ui.horizontal(a);
        c.setBackground(Ui.card(a, 16));
        c.setPadding(dp(16), dp(12), dp(12), dp(12));
        android.widget.ImageView ic = new android.widget.ImageView(a);
        ic.setImageDrawable(new Icons(Icons.UPDATE, Ui.primaryOn()));
        c.addView(ic, Ui.lp(dp(24), dp(24)));
        TextView t = Ui.text(a, n + " uygulama için güncelleme var", 14.5f, Ui.text(), true);
        t.setPadding(dp(12), 0, dp(8), 0);
        c.addView(t, Ui.weight(1));
        Button b = Ui.button(a, "Görüntüle", Ui.primaryOn(), false);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { a.showTab(MainActivity.TAB_UPDATES); }
        });
        c.addView(b, Ui.lp(-2, dp(36)));
        col.addView(c, Ui.lp(-1, -2, dp(16), dp(8), dp(16), dp(4)));
    }

    private AppViews.Bindable bindButton(final Button b, final AppEntry e) {
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Actions.perform(a, e);
                Actions.bind(a, b, e, true);
            }
        });
        Actions.bind(a, b, e, true);
        return new AppViews.Bindable() {
            @Override public String pkg() { return e.packageName; }
            @Override public void bind() { Actions.bind(a, b, e, true); }
        };
    }

    private void featured(List<AppEntry> list) {
        if (list.isEmpty()) return;
        featureCount = list.size();
        featurePage = 0;
        int screen = a.getResources().getDisplayMetrics().widthPixels;
        featureWidth = Math.min(screen - dp(list.size() > 1 ? 56 : 32), dp(520));
        carousel = new HorizontalScrollView(a);
        carousel.setHorizontalScrollBarEnabled(false);
        carousel.setClipToPadding(false);
        carousel.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout row = Ui.horizontal(a);
        row.setGravity(Gravity.TOP);
        for (int i = 0; i < list.size(); i++) {
            AppViews.Feature f = group.add(new AppViews.Feature(a, list.get(i), featureWidth));
            row.addView(f.view, Ui.lp(featureWidth, -2, 0, 0, i < list.size() - 1 ? dp(12) : 0, 0));
        }
        carousel.addView(row);
        carousel.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN) h.removeCallbacks(autoAdvance);
                if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                    final int step = featureWidth + dp(12);
                    featurePage = Math.max(0, Math.min(featureCount - 1, Math.round(carousel.getScrollX() / (float) step)));
                    carousel.post(new Runnable() {
                        @Override public void run() { carousel.smoothScrollTo(featurePage * step, 0); }
                    });
                    updateDots();
                    h.postDelayed(autoAdvance, 8000);
                }
                return false;
            }
        });
        col.addView(carousel, Ui.lp(-1, -2, 0, dp(10), 0, 0));
        dots = Ui.horizontal(a);
        dots.setGravity(Gravity.CENTER);
        if (list.size() > 1) {
            for (int i = 0; i < list.size(); i++) {
                View d = new View(a);
                dots.addView(d, Ui.lp(dp(6), dp(6), dp(3), 0, dp(3), 0));
            }
            col.addView(dots, Ui.lp(-1, -2, 0, dp(10), 0, 0));
            updateDots();
        }
    }

    private void updateDots() {
        if (dots == null) return;
        for (int i = 0; i < dots.getChildCount(); i++) {
            View d = dots.getChildAt(i);
            boolean on = i == featurePage;
            d.setBackground(Ui.round(on ? Ui.PRIMARY : Ui.border(), dp(3)));
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) d.getLayoutParams();
            p.width = dp(on ? 18 : 6);
            d.setLayoutParams(p);
        }
    }

    private void categoryChips(CatalogRepo repo) {
        Map<String, List<AppEntry>> cats = repo.byCategory();
        if (cats.size() < 1) return;
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.horizontal(a);
        row.setPadding(dp(16), 0, dp(16), 0);
        addChip(row, "Tümü", null);
        for (String c : cats.keySet()) addChip(row, c, c);
        hs.addView(row);
        col.addView(hs, Ui.lp(-1, -2, 0, dp(16), 0, 0));
    }

    private void addChip(LinearLayout row, String label, final String cat) {
        boolean sel = cat == null ? category == null : cat.equals(category);
        TextView c = Ui.chip(a, label, sel);
        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                category = cat;
                render();
                scroll.scrollTo(0, 0);
            }
        });
        row.addView(c, Ui.lp(-2, -2, 0, 0, dp(8), 0));
    }

    private void rail(String title, List<AppEntry> list) {
        if (list.isEmpty()) return;
        LinearLayout head = Ui.horizontal(a);
        head.setPadding(dp(16), dp(22), dp(8), dp(4));
        head.addView(Ui.text(a, title, 18, Ui.text(), true), Ui.weight(1));
        col.addView(head);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.horizontal(a);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(10), 0, dp(10), 0);
        for (AppEntry e : list) row.addView(group.add(new AppViews.Tile(a, e)).view, Ui.lp(-2, -2, 0, 0, dp(4), 0));
        hs.addView(row);
        col.addView(hs);
    }

    private void footer(CatalogRepo repo) {
        String when = repo.lastUpdated() > 0
                ? DateUtils.getRelativeTimeSpanString(repo.lastUpdated(), System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS).toString() : "henüz";
        TextView t = Ui.text(a, repo.storeName() + "  •  " + repo.apps().size() + " uygulama  •  Katalog " + when + " güncellendi",
                12, Ui.muted(), false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(28), dp(16), dp(8));
        col.addView(t, Ui.lp(-1, -2));
    }

    private void skeleton() {
        LinearLayout c = Ui.vertical(a);
        c.setPadding(dp(16), dp(12), dp(16), 0);
        c.addView(Ui.skeleton(a, -1, 200, 20));
        LinearLayout chips = Ui.horizontal(a);
        for (int i = 0; i < 3; i++) chips.addView(Ui.skeleton(a, 80, 32, 16), Ui.lp(dp(80), dp(32), 0, 0, dp(8), 0));
        c.addView(chips, Ui.lp(-1, -2, 0, dp(18), 0, 0));
        for (int i = 0; i < 5; i++) {
            LinearLayout r = Ui.horizontal(a);
            r.addView(Ui.skeleton(a, 52, 52, 12), Ui.lp(dp(52), dp(52)));
            LinearLayout t = Ui.vertical(a);
            t.setPadding(dp(14), 0, 0, 0);
            t.addView(Ui.skeleton(a, 160, 14, 6));
            t.addView(Ui.skeleton(a, 110, 12, 6), Ui.lp(dp(110), dp(12), 0, dp(8), 0, 0));
            r.addView(t);
            c.addView(r, Ui.lp(-1, -2, 0, dp(18), 0, 0));
        }
        col.addView(c);
    }

    private void error(String msg) {
        LinearLayout c = Ui.vertical(a);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(32), dp(80), dp(32), dp(32));
        View ic = new View(a);
        ic.setBackground(new Icons(Icons.WARNING, Ui.muted()));
        c.addView(ic, Ui.lp(dp(64), dp(64)));
        TextView t = Ui.text(a, "Mağaza yüklenemedi", 20, Ui.text(), true);
        t.setPadding(0, dp(16), 0, dp(6));
        c.addView(t);
        TextView m = Ui.text(a, msg, 14, Ui.muted(), false);
        m.setGravity(Gravity.CENTER);
        c.addView(m);
        Button b = Ui.button(a, "Tekrar dene", Ui.PRIMARY, true);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { CatalogRepo.get(a).refreshAsync(); }
        });
        c.addView(b, Ui.lp(-2, dp(44), 0, dp(20), 0, 0));
        col.addView(c, Ui.lp(-1, -2));
    }

    @Override void onHide() { h.removeCallbacks(autoAdvance); }

    @Override
    boolean onBack() {
        if (category != null) {
            category = null;
            render();
            return true;
        }
        if (scroll != null && scroll.getScrollY() > 0) {
            scroll.smoothScrollTo(0, 0);
            return true;
        }
        return false;
    }

    void scrollTop() { if (scroll != null) scroll.smoothScrollTo(0, 0); }
}
