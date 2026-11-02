package com.kisisel.magaza;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Kitaplik: yuklu uygulamalar ve kurulum gecmisi. */
final class LibraryTab extends Tab {
    private LinearLayout col;
    private boolean showHistory;

    LibraryTab(MainActivity a) { super(a); }

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

        // Sekme secici
        LinearLayout seg = Ui.horizontal(a);
        seg.setPadding(dp(16), dp(8), dp(16), dp(4));
        TextView s1 = Ui.chip(a, "Yüklü", !showHistory);
        TextView s2 = Ui.chip(a, "Geçmiş", showHistory);
        s1.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showHistory = false; render(); }
        });
        s2.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showHistory = true; render(); }
        });
        seg.addView(s1, Ui.lp(-2, -2, 0, 0, dp(8), 0));
        seg.addView(s2);
        col.addView(seg);

        if (showHistory) history();
        else installed();
    }

    private void installed() {
        List<AppEntry> list = CatalogRepo.get(a).installed();
        long total = 0;
        for (AppEntry e : list) total += CatalogRepo.installedSize(a, e.packageName);
        LinearLayout card = Ui.horizontal(a);
        card.setBackground(Ui.card(a, 16));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        View ic = new View(a);
        ic.setBackground(new Icons(Icons.PHONE, Ui.primaryOn()));
        card.addView(ic, Ui.lp(dp(26), dp(26)));
        LinearLayout t = Ui.vertical(a);
        t.setPadding(dp(14), 0, 0, 0);
        t.addView(Ui.text(a, list.size() + " uygulama yüklü", 16, Ui.text(), true));
        t.addView(Ui.text(a, "Kapladığı alan (APK): " + Ui.size(a, total), 13, Ui.muted(), false));
        card.addView(t, Ui.weight(1));
        col.addView(card, Ui.lp(-1, -2, dp(16), dp(10), dp(16), dp(6)));

        if (list.isEmpty()) {
            empty("Fast Store'dan henüz bir uygulama yüklemedin.", Icons.LIBRARY);
            return;
        }
        for (final AppEntry e : list) {
            AppViews.Row r = group.add(new AppViews.Row(a, e, false));
            r.view.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    if (e.packageName.equals(a.getPackageName())) return false;
                    new AlertDialog.Builder(a).setTitle(e.name)
                            .setItems(new String[]{"Ayrıntılar", "Kaldır"}, new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) {
                                    if (w == 0) Actions.openDetail(a, e);
                                    else Actions.uninstall(a, e);
                                }
                            }).show();
                    return true;
                }
            });
            col.addView(r.view);
        }
        TextView tip = Ui.text(a, "İpucu: kaldırma seçenekleri için bir uygulamaya basılı tut.", 12, Ui.muted(), false);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(dp(24), dp(16), dp(24), 0);
        col.addView(tip);
    }

    private void history() {
        List<History.Item> items = History.all(a);
        if (items.isEmpty()) {
            empty("Kurulum geçmişi boş.\nFast Store ile yaptığın kurulum ve güncellemeler burada listelenir.", Icons.HISTORY);
            return;
        }
        LinearLayout head = Ui.horizontal(a);
        head.setPadding(dp(16), dp(12), dp(8), dp(4));
        head.addView(Ui.text(a, items.size() + " kayıt", 13, Ui.muted(), false), Ui.weight(1));
        TextView clr = Ui.text(a, "Geçmişi temizle", 13.5f, Ui.DANGER, true);
        clr.setPadding(dp(8), dp(6), dp(8), dp(6));
        clr.setBackground(Ui.selectable(a, 8));
        clr.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                History.clear(a);
                render();
            }
        });
        head.addView(clr);
        col.addView(head);
        for (History.Item it : items) {
            final AppEntry e = CatalogRepo.get(a).find(it.pkg);
            LinearLayout r = Ui.horizontal(a);
            r.setPadding(dp(16), dp(10), dp(16), dp(10));
            r.setBackground(Ui.selectable(a, 0));
            r.addView(Ui.icon(a, e, 40), Ui.lp(dp(40), dp(40)));
            LinearLayout t = Ui.vertical(a);
            t.setPadding(dp(14), 0, 0, 0);
            t.addView(Ui.text(a, it.name, 15, Ui.text(), true));
            t.addView(Ui.text(a, History.verb(it.type) + (it.version.isEmpty() ? "" : "  •  " + it.version) + "  •  "
                    + DateUtils.getRelativeTimeSpanString(it.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                    13, Ui.muted(), false));
            r.addView(t, Ui.weight(1));
            if (e != null) {
                r.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { Actions.openDetail(a, e); }
                });
            }
            col.addView(r);
        }
    }

    private void empty(String msg, int icon) {
        LinearLayout c = Ui.vertical(a);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(32), dp(48), dp(32), 0);
        View ic = new View(a);
        ic.setBackground(new Icons(icon, Ui.muted()));
        c.addView(ic, Ui.lp(dp(56), dp(56)));
        TextView t = Ui.text(a, msg, 14.5f, Ui.muted(), false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(16), 0, 0);
        c.addView(t);
        col.addView(c, Ui.lp(-1, -2));
    }
}
