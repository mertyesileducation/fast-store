package com.kisisel.magaza;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity
        implements CatalogRepo.Listener, InstallManager.Listener {
    static final String EXTRA_TAB = "sekme";
    static final int TAB_DISCOVER = 0, TAB_UPDATES = 1, TAB_INSTALLED = 2;
    private static final String ALL = "Tümü";

    private CatalogRepo repo;
    private int tab = TAB_DISCOVER;
    private String category = ALL;
    private String query = "";

    private final TextView[] tabViews = new TextView[3];
    private LinearLayout chipRow;
    private HorizontalScrollView chipScroll;
    private TextView emptyView;
    private ProgressBar loadingBar;
    private Button updateAllBtn;
    private ListView list;
    private final Adapter adapter = new Adapter();
    private final List<AppEntry> shown = new ArrayList<>();

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { render(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.styleBars(this);
        repo = CatalogRepo.get(this);
        tab = getIntent().getIntExtra(EXTRA_TAB, TAB_DISCOVER);
        if (b != null) {
            tab = b.getInt("tab", tab);
            category = b.getString("cat", ALL);
        }
        setContentView(buildLayout());
        if (repo.sources().isEmpty()) {
            render();
        } else if (repo.lastUpdated() == 0 || System.currentTimeMillis() - repo.lastUpdated() > 10 * 60 * 1000) {
            repo.refreshAsync();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra(EXTRA_TAB)) selectTab(intent.getIntExtra(EXTRA_TAB, TAB_DISCOVER));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
        out.putString("cat", category);
    }

    @Override
    protected void onResume() {
        super.onResume();
        repo.addListener(this);
        InstallManager.get(this).addListener(this);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(pkgReceiver, f);
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        repo.removeListener(this);
        InstallManager.get(this).removeListener(this);
        unregisterReceiver(pkgReceiver);
    }

    @Override public void onCatalogChanged() { render(); }

    @Override public void onTaskChanged(String pkg) {
        if (tab != TAB_DISCOVER || !TextUtils.isEmpty(query)) { render(); return; }
        // Sadece gorunen satiri guncelle
        for (int i = 0; i < list.getChildCount(); i++) {
            View v = list.getChildAt(i);
            Holder h = (Holder) v.getTag();
            if (h != null && h.entry != null && h.entry.packageName.equals(pkg)) h.bind(h.entry);
        }
        updateTabTitles();
    }

    // ---------- Arayuz ----------

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg(this));

        // Ust bolum: arama ve sekmeler
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(Ui.PRIMARY);
        header.setPadding(dp(16), dp(4), dp(16), 0);

        EditText search = new EditText(this);
        search.setHint("Uygulama ara…");
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setTextColor(Ui.text(this));
        search.setHintTextColor(Ui.sub(this));
        search.setTextSize(15);
        search.setBackground(Ui.round(Ui.card(this), dp(24)));
        search.setPadding(dp(18), dp(10), dp(18), dp(10));
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0);
        search.setCompoundDrawablePadding(dp(8));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                render();
            }
        });
        header.addView(search, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Keşfet", "Güncellemeler", "Yüklü"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = Ui.text(this, names[i], 14, Color.WHITE, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(12), 0, dp(12));
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { selectTab(idx); }
            });
            tabViews[i] = t;
            tabs.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(6);
        header.addView(tabs, tlp);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // Kategori cipleri
        chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(dp(12), dp(10), dp(12), dp(4));
        chipScroll.addView(chipRow);
        root.addView(chipScroll, new LinearLayout.LayoutParams(-1, -2));

        updateAllBtn = Ui.button(this, "Tümünü güncelle", Ui.PRIMARY, true);
        updateAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!Actions.ensureInstallPermission(MainActivity.this)) return;
                for (AppEntry e : repo.updates()) InstallManager.get(MainActivity.this).enqueue(e);
                Actions.askNotificationPermission(MainActivity.this);
            }
        });
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(-1, -2);
        ulp.setMargins(dp(16), dp(12), dp(16), dp(4));
        root.addView(updateAllBtn, ulp);

        loadingBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loadingBar.setIndeterminate(true);
        root.addView(loadingBar, new LinearLayout.LayoutParams(-1, dp(4)));

        FrameLayout body = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setClipToPadding(false);
        list.setPadding(0, dp(4), 0, dp(16));
        list.setSelector(android.R.color.transparent);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                startActivity(new Intent(MainActivity.this, DetailActivity.class)
                        .putExtra(DetailActivity.EXTRA_PKG, shown.get(pos).packageName));
            }
        });
        body.addView(list, new FrameLayout.LayoutParams(-1, -1));

        emptyView = Ui.text(this, "", 15, Ui.sub(this), false);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(32), dp(32), dp(32), dp(32));
        emptyView.setLineSpacing(0, 1.2f);
        body.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    private void selectTab(int t) {
        tab = t;
        render();
        list.setSelection(0);
    }

    private void updateTabTitles() {
        int upd = repo.updates().size();
        tabViews[1].setText(upd > 0 ? "Güncellemeler (" + upd + ")" : "Güncellemeler");
        for (int i = 0; i < 3; i++) {
            tabViews[i].setAlpha(i == tab ? 1f : 0.65f);
            tabViews[i].setBackground(i == tab ? underline() : null);
        }
    }

    private android.graphics.drawable.Drawable underline() {
        android.graphics.drawable.GradientDrawable line = new android.graphics.drawable.GradientDrawable();
        line.setColor(Color.WHITE);
        android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{line});
        ld.setLayerGravity(0, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        ld.setLayerHeight(0, dp(3));
        ld.setLayerInsetLeft(0, dp(24));
        ld.setLayerInsetRight(0, dp(24));
        return ld;
    }

    private void buildChips() {
        chipRow.removeAllViews();
        List<String> cats = new ArrayList<>();
        cats.add(ALL);
        cats.addAll(repo.categories());
        if (!cats.contains(category)) category = ALL;
        for (final String c : cats) {
            TextView chip = Ui.chip(this, c, c.equals(category));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    category = c;
                    render();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            chipRow.addView(chip, lp);
        }
    }

    private void render() {
        if (list == null) return;
        updateTabTitles();
        List<AppEntry> base;
        if (tab == TAB_UPDATES) base = repo.updates();
        else if (tab == TAB_INSTALLED) base = repo.installed();
        else base = repo.apps();

        String q = query.toLowerCase(Locale.getDefault());
        shown.clear();
        for (AppEntry e : base) {
            if (tab == TAB_DISCOVER && !ALL.equals(category) && !category.equalsIgnoreCase(e.category)) continue;
            if (!q.isEmpty() && !(e.name.toLowerCase(Locale.getDefault()).contains(q)
                    || e.packageName.toLowerCase(Locale.ROOT).contains(q)
                    || e.description.toLowerCase(Locale.getDefault()).contains(q)
                    || e.author.toLowerCase(Locale.getDefault()).contains(q))) continue;
            shown.add(e);
        }
        boolean showChips = tab == TAB_DISCOVER && repo.categories().size() > 1;
        chipScroll.setVisibility(showChips ? View.VISIBLE : View.GONE);
        if (showChips) buildChips();
        updateAllBtn.setVisibility(tab == TAB_UPDATES && shown.size() > 1 ? View.VISIBLE : View.GONE);
        loadingBar.setVisibility(repo.isLoading() ? View.VISIBLE : View.INVISIBLE);

        String empty = null;
        if (repo.sources().isEmpty()) {
            empty = "Henüz bir katalog eklenmedi.\n\nSağ üstteki menüden Ayarlar'a girip katalog adresini (katalog.json) ekle.";
        } else if (shown.isEmpty()) {
            if (repo.isLoading()) empty = "Katalog yükleniyor…";
            else if (repo.apps().isEmpty() && repo.lastError() != null)
                empty = "Katalog alınamadı:\n\n" + repo.lastError();
            else if (!q.isEmpty()) empty = "\"" + query + "\" için sonuç yok.";
            else if (tab == TAB_UPDATES) empty = "Tüm uygulamalar güncel ✓";
            else if (tab == TAB_INSTALLED) empty = "Mağazadan yüklenmiş uygulama yok.";
            else empty = "Bu katalogda uygulama yok.";
        }
        emptyView.setText(empty == null ? "" : empty);
        emptyView.setVisibility(empty == null ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Yenile").setIcon(android.R.drawable.ic_popup_sync)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, 2, 1, "Ayarlar");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            if (repo.sources().isEmpty()) startActivity(new Intent(this, SettingsActivity.class));
            else repo.refreshAsync();
            return true;
        }
        if (item.getItemId() == 2) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private int dp(float v) { return Ui.dp(this, v); }

    // ---------- Liste ----------

    private final class Holder {
        AppEntry entry;
        ImageView icon;
        TextView name, sub, badge;
        Button action;
        ProgressBar progress;

        void bind(AppEntry e) {
            entry = e;
            name.setText(e.name);
            StringBuilder s = new StringBuilder();
            if (!e.author.isEmpty()) s.append(e.author).append(" · ");
            s.append(e.category);
            if (e.size > 0) s.append(" · ").append(Formatter.formatShortFileSize(MainActivity.this, e.size));
            String installed = CatalogRepo.installedVersionName(MainActivity.this, e.packageName);
            if (installed != null && CatalogRepo.hasUpdate(MainActivity.this, e)) {
                s.append("\n").append(installed).append(" → ").append(e.versionName);
            } else if (!e.versionName.isEmpty()) {
                s.append("\nSürüm ").append(e.versionName);
            }
            sub.setText(s);
            InstallManager.Task t = InstallManager.get(MainActivity.this).task(e.packageName);
            if (t != null && t.state != InstallManager.FAILED) {
                progress.setVisibility(View.VISIBLE);
                progress.setIndeterminate(t.progress < 0);
                progress.setProgress(Math.max(0, t.progress));
            } else {
                progress.setVisibility(View.GONE);
            }
            if (t != null && t.state == InstallManager.FAILED) {
                badge.setVisibility(View.VISIBLE);
                badge.setText(t.error);
            } else {
                badge.setVisibility(View.GONE);
            }
            Actions.bind(MainActivity.this, action, e);
            ImageLoader.get(MainActivity.this).load(e.iconUrl, icon, Ui.chip(MainActivity.this), dp(52));
        }
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int p) { return shown.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            Holder h;
            if (convert == null) {
                h = new Holder();
                LinearLayout card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(14), dp(12), dp(12), dp(12));
                card.setBackground(Ui.round(Ui.card(MainActivity.this), dp(16)));
                card.setElevation(dp(1));

                h.icon = new ImageView(MainActivity.this);
                h.icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                h.icon.setClipToOutline(true);
                h.icon.setBackground(Ui.round(Ui.chip(MainActivity.this), dp(12)));
                card.addView(h.icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

                LinearLayout mid = new LinearLayout(MainActivity.this);
                mid.setOrientation(LinearLayout.VERTICAL);
                mid.setPadding(dp(14), 0, dp(8), 0);
                h.name = Ui.text(MainActivity.this, "", 16, Ui.text(MainActivity.this), true);
                h.name.setSingleLine(true);
                h.name.setEllipsize(TextUtils.TruncateAt.END);
                h.sub = Ui.text(MainActivity.this, "", 12.5f, Ui.sub(MainActivity.this), false);
                h.sub.setMaxLines(2);
                h.badge = Ui.text(MainActivity.this, "", 12, Ui.DANGER, false);
                h.badge.setMaxLines(2);
                h.progress = new ProgressBar(MainActivity.this, null, android.R.attr.progressBarStyleHorizontal);
                h.progress.setMax(100);
                mid.addView(h.name);
                mid.addView(h.sub);
                mid.addView(h.badge);
                LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dp(6));
                plp.topMargin = dp(6);
                mid.addView(h.progress, plp);
                card.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));

                h.action = Ui.button(MainActivity.this, "", Ui.PRIMARY, true);
                h.action.setFocusable(false);
                final Holder fh = h;
                h.action.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Actions.perform(MainActivity.this, fh.entry);
                        fh.bind(fh.entry);
                    }
                });
                card.addView(h.action, new LinearLayout.LayoutParams(-2, dp(38)));

                FrameLayout wrap = new FrameLayout(MainActivity.this);
                wrap.setPadding(dp(12), dp(5), dp(12), dp(5));
                wrap.addView(card, new FrameLayout.LayoutParams(-1, -2));
                wrap.setTag(h);
                convert = wrap;
            } else {
                h = (Holder) convert.getTag();
            }
            h.bind(shown.get(pos));
            return convert;
        }
    }
}
