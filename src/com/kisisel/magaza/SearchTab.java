package com.kisisel.magaza;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Arama sekmesi. */
final class SearchTab extends Tab {
    private EditText input;
    private ImageButton clear;
    private LinearLayout results;
    private String query = "";

    SearchTab(MainActivity a) { super(a); }

    @Override
    View build() {
        LinearLayout root = Ui.vertical(a);
        root.setBackgroundColor(Ui.bg());

        LinearLayout box = Ui.horizontal(a);
        box.setBackground(Ui.round(Ui.surface(), dp(26)));
        box.setElevation(dp(1));
        box.setPadding(dp(16), 0, dp(4), 0);
        View si = new View(a);
        si.setBackground(new Icons(Icons.SEARCH, Ui.muted()));
        box.addView(si, Ui.lp(dp(22), dp(22)));
        input = new EditText(a);
        input.setHint("Uygulama, kategori veya geliştirici ara");
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setTextSize(16);
        input.setTextColor(Ui.text());
        input.setHintTextColor(Ui.muted());
        input.setBackground(null);
        input.setPadding(dp(12), dp(14), dp(8), dp(14));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void onTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString();
                clear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                render();
            }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                Prefs.addRecentSearch(a, query);
                hideKeyboard();
                return true;
            }
        });
        box.addView(input, Ui.weight(1));
        clear = Ui.iconButton(a, Icons.CLOSE, Ui.muted());
        clear.setVisibility(View.GONE);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                input.setText("");
                showKeyboard();
            }
        });
        box.addView(clear, Ui.lp(dp(44), dp(44)));
        root.addView(box, Ui.lp(-1, -2, dp(16), dp(12), dp(16), dp(8)));

        ScrollView sv = new ScrollView(a);
        results = Ui.vertical(a);
        results.setPadding(0, 0, 0, dp(24));
        sv.addView(results);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    @Override
    void render() {
        if (results == null) return;
        results.removeAllViews();
        group.clear();
        CatalogRepo repo = CatalogRepo.get(a);
        if (query.trim().isEmpty()) {
            suggestions(repo);
            return;
        }
        List<AppEntry> found = repo.search(query);
        if (found.isEmpty()) {
            LinearLayout c = Ui.vertical(a);
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            c.setPadding(dp(32), dp(60), dp(32), 0);
            View ic = new View(a);
            ic.setBackground(new Icons(Icons.SEARCH, Ui.muted()));
            c.addView(ic, Ui.lp(dp(56), dp(56)));
            TextView t = Ui.text(a, "\"" + query.trim() + "\" için sonuç bulunamadı", 16, Ui.text(), true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(16), 0, dp(6));
            c.addView(t);
            TextView m = Ui.text(a, "Farklı bir kelime dene veya yazımı kontrol et.", 14, Ui.muted(), false);
            m.setGravity(Gravity.CENTER);
            c.addView(m);
            results.addView(c, Ui.lp(-1, -2));
            return;
        }
        TextView count = Ui.text(a, found.size() + " sonuç", 13, Ui.muted(), false);
        count.setPadding(dp(16), dp(4), dp(16), dp(4));
        results.addView(count);
        for (AppEntry e : found) {
            final AppViews.Row r = group.add(new AppViews.Row(a, e, false));
            final AppEntry fe = e;
            r.view.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Prefs.addRecentSearch(a, query);
                    Actions.openDetail(a, fe);
                }
            });
            results.addView(r.view);
        }
    }

    private void suggestions(CatalogRepo repo) {
        List<String> recent = Prefs.recentSearches(a);
        if (!recent.isEmpty()) {
            LinearLayout head = Ui.horizontal(a);
            head.setPadding(dp(16), dp(12), dp(8), dp(4));
            head.addView(Ui.text(a, "Son aramalar", 16, Ui.text(), true), Ui.weight(1));
            TextView clr = Ui.text(a, "Temizle", 14, Ui.primaryOn(), true);
            clr.setPadding(dp(8), dp(6), dp(8), dp(6));
            clr.setBackground(Ui.selectable(a, 8));
            clr.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Prefs.clearRecentSearches(a);
                    render();
                }
            });
            head.addView(clr);
            results.addView(head);
            for (final String q : recent) {
                LinearLayout r = Ui.horizontal(a);
                r.setPadding(dp(16), dp(12), dp(16), dp(12));
                r.setBackground(Ui.selectable(a, 0));
                View ic = new View(a);
                ic.setBackground(new Icons(Icons.HISTORY, Ui.muted()));
                r.addView(ic, Ui.lp(dp(20), dp(20)));
                TextView t = Ui.text(a, q, 15, Ui.text(), false);
                t.setPadding(dp(16), 0, 0, 0);
                r.addView(t, Ui.weight(1));
                r.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        input.setText(q);
                        input.setSelection(q.length());
                        hideKeyboard();
                    }
                });
                results.addView(r);
            }
        }
        if (repo.isEmpty()) return;
        results.addView(Ui.title(a, "Kategorilere göz at"));
        LinearLayout grid = Ui.vertical(a);
        grid.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout row = null;
        int i = 0;
        for (final String c : repo.byCategory().keySet()) {
            if (i % 2 == 0) {
                row = Ui.horizontal(a);
                grid.addView(row, Ui.lp(-1, -2, 0, 0, 0, dp(10)));
            }
            TextView t = Ui.text(a, c, 15, Ui.text(), true);
            t.setPadding(dp(16), dp(18), dp(16), dp(18));
            t.setBackground(Ui.ripple(a, Ui.card(a, 14), 14));
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    input.setText(c);
                    input.setSelection(c.length());
                    hideKeyboard();
                }
            });
            LinearLayout.LayoutParams lp = Ui.weight(1);
            if (i % 2 == 0) lp.rightMargin = dp(5); else lp.leftMargin = dp(5);
            row.addView(t, lp);
            i++;
        }
        if (i % 2 == 1 && row != null) row.addView(new View(a), Ui.weight(1));
        results.addView(grid);
    }

    void focus() {
        if (input == null) return;
        input.requestFocus();
        showKeyboard();
    }

    private void showKeyboard() {
        input.post(new Runnable() {
            @Override public void run() {
                ((InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE))
                        .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void hideKeyboard() {
        input.clearFocus();
        ((InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    @Override void onHide() { if (input != null) hideKeyboard(); }

    @Override
    boolean onBack() {
        if (!query.isEmpty()) {
            input.setText("");
            return true;
        }
        return false;
    }
}
