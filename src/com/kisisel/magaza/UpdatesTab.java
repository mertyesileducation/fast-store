package com.kisisel.magaza;

import android.content.Intent;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Guncellemeler sekmesi. */
final class UpdatesTab extends Tab {
    private LinearLayout col;

    UpdatesTab(MainActivity a) { super(a); }

    @Override
    View build() {
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(Ui.bg());
        col = Ui.vertical(a);
        col.setPadding(0, dp(4), 0, dp(24));
        sv.addView(col);
        return sv;
    }

    @Override
    void render() {
        if (col == null) return;
        col.removeAllViews();
        group.clear();
        CatalogRepo repo = CatalogRepo.get(a);
        List<AppEntry> ups = repo.updates();

        // Ozet karti
        LinearLayout card = Ui.vertical(a);
        card.setBackground(Ui.card(a, 18));
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout top = Ui.horizontal(a);
        LinearLayout texts = Ui.vertical(a);
        long total = 0;
        for (AppEntry e : ups) total += e.size;
        texts.addView(Ui.text(a, ups.isEmpty() ? "Her şey güncel" : ups.size() + " güncelleme mevcut", 19, Ui.text(), true));
        String sub = ups.isEmpty() ? "Tüm uygulamalar son sürümde" : "Toplam " + Ui.size(a, total);
        texts.addView(Ui.text(a, sub, 13, Ui.muted(), false));
        top.addView(texts, Ui.weight(1));
        if (ups.size() > 0) {
            Button all = Ui.button(a, "Tümünü güncelle", Ui.PRIMARY, true);
            all.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Actions.updateAll(a);
                    group.rebind(null);
                }
            });
            top.addView(all, Ui.lp(-2, dp(38)));
        } else {
            View ok = new View(a);
            ok.setBackground(new Icons(Icons.CHECK, Ui.SUCCESS));
            top.addView(ok, Ui.lp(dp(32), dp(32)));
        }
        card.addView(top);
        card.addView(Ui.divider(a), Ui.lp(-1, Math.max(1, dp(0.8f)), 0, dp(14), 0, dp(10)));

        // Otomatik guncelleme durumu
        LinearLayout auto = Ui.horizontal(a);
        auto.setBackground(Ui.selectable(a, 8));
        View ai = new View(a);
        ai.setBackground(new Icons(Icons.SHIELD, Ui.primaryOn()));
        auto.addView(ai, Ui.lp(dp(20), dp(20)));
        String[] modes = {"Kapalı", "Sadece Wi-Fi'de", "Her zaman"};
        TextView at = Ui.text(a, "Otomatik güncelleme: " + modes[Prefs.autoUpdate(a)], 13.5f, Ui.text(), false);
        at.setPadding(dp(10), 0, 0, 0);
        auto.addView(at, Ui.weight(1));
        View ch = new View(a);
        ch.setBackground(new Icons(Icons.CHEVRON, Ui.muted()));
        auto.addView(ch, Ui.lp(dp(18), dp(18)));
        auto.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { a.startActivity(new Intent(a, SettingsActivity.class)); }
        });
        card.addView(auto, Ui.lp(-1, dp(32)));

        LinearLayout check = Ui.horizontal(a);
        long last = Prefs.lastCheck(a);
        String when = last > 0 ? DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS).toString() : "henüz yapılmadı";
        TextView lt = Ui.text(a, repo.isLoading() ? "Kontrol ediliyor…" : "Son kontrol: " + when, 12.5f, Ui.muted(), false);
        check.addView(lt, Ui.weight(1));
        TextView now = Ui.text(a, "Şimdi kontrol et", 13.5f, Ui.primaryOn(), true);
        now.setPadding(dp(8), dp(6), dp(4), dp(6));
        now.setBackground(Ui.selectable(a, 8));
        now.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { CatalogRepo.get(a).refreshAsync(); }
        });
        check.addView(now);
        card.addView(check, Ui.lp(-1, -2, 0, dp(4), 0, 0));
        col.addView(card, Ui.lp(-1, -2, dp(16), dp(8), dp(16), dp(8)));

        if (!ups.isEmpty()) {
            col.addView(Ui.title(a, "Bekleyen güncellemeler"));
            for (AppEntry e : ups) col.addView(group.add(new AppViews.Row(a, e, true)).view);
        } else if (!repo.isEmpty()) {
            List<AppEntry> recent = repo.recentlyUpdated(5);
            col.addView(Ui.title(a, "Mağazada son güncellenenler"));
            for (AppEntry e : recent) col.addView(group.add(new AppViews.Row(a, e, true)).view);
        }
        TextView note = Ui.text(a, "Fast Protect: her güncelleme kurulmadan önce dosya özeti ve geliştirici imzası doğrulanır.",
                12, Ui.muted(), false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(24), dp(24), dp(24), 0);
        col.addView(note);
    }
}
