package com.kisisel.magaza;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity implements CatalogRepo.Listener, InstallManager.Listener {
    static final String EXTRA_TAB = "sekme";
    static final int TAB_HOME = 0, TAB_SEARCH = 1, TAB_UPDATES = 2, TAB_LIBRARY = 3;

    private final Tab[] tabs = new Tab[4];
    private FrameLayout host;
    private final Icons[] navIcons = new Icons[4];
    private final TextView[] navLabels = new TextView[4];
    private final View[] navPills = new View[4];
    private TextView badge, headerTitle;
    private LinearLayout searchPill;
    private View loadingLine;
    private int current = -1;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String pkg = i.getData() == null ? null : i.getData().getSchemeSpecificPart();
            if (Intent.ACTION_PACKAGE_REMOVED.equals(i.getAction())
                    && !i.getBooleanExtra(Intent.EXTRA_REPLACING, false) && pkg != null) {
                AppEntry e = CatalogRepo.get(c).find(pkg);
                if (e != null) History.add(c, pkg, e.name, null, History.UNINSTALL);
            }
            onCatalogChanged();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        Ui.applyTheme(this);
        super.onCreate(b);
        Ui.styleWindow(this);
        tabs[TAB_HOME] = new HomeTab(this);
        tabs[TAB_SEARCH] = new SearchTab(this);
        tabs[TAB_UPDATES] = new UpdatesTab(this);
        tabs[TAB_LIBRARY] = new LibraryTab(this);
        setContentView(buildLayout());

        int start = b != null ? b.getInt("sekme", TAB_HOME) : getIntent().getIntExtra(EXTRA_TAB, TAB_HOME);
        showTab(start);
        CatalogRepo repo = CatalogRepo.get(this);
        if (repo.isEmpty() || repo.isStale()) repo.refreshAsync();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i.hasExtra(EXTRA_TAB)) showTab(i.getIntExtra(EXTRA_TAB, TAB_HOME));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("sekme", current);
    }

    private int dp(float v) { return Ui.dp(this, v); }

    private View buildLayout() {
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.bg());

        // Ust cubuk
        LinearLayout top = Ui.horizontal(this);
        top.setPadding(dp(16), dp(8), dp(6), dp(8));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        top.addView(logo, Ui.lp(dp(34), dp(34)));
        headerTitle = Ui.text(this, "Fast Store", 20, Ui.text(), true);
        headerTitle.setPadding(dp(10), 0, 0, 0);
        top.addView(headerTitle, Ui.weight(1));
        searchPill = Ui.horizontal(this);
        searchPill.setBackground(Ui.ripple(this, Ui.round(Ui.surface(), dp(22)), 22));
        searchPill.setElevation(dp(1));
        searchPill.setPadding(dp(14), 0, dp(16), 0);
        View si = new View(this);
        si.setBackground(new Icons(Icons.SEARCH, Ui.muted()));
        searchPill.addView(si, Ui.lp(dp(18), dp(18)));
        TextView st = Ui.text(this, "Ara", 15, Ui.muted(), false);
        st.setPadding(dp(8), 0, 0, 0);
        searchPill.addView(st);
        searchPill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showTab(TAB_SEARCH);
                ((SearchTab) tabs[TAB_SEARCH]).focus();
            }
        });
        top.addView(searchPill, Ui.lp(-2, dp(40), 0, 0, dp(4), 0));
        ImageButton settings = Ui.iconButton(this, Icons.SETTINGS, Ui.text());
        settings.setContentDescription("Ayarlar");
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); }
        });
        top.addView(settings, Ui.lp(dp(44), dp(44)));
        root.addView(top);

        loadingLine = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        ((android.widget.ProgressBar) loadingLine).setIndeterminate(true);
        ((android.widget.ProgressBar) loadingLine).setIndeterminateTintList(
                android.content.res.ColorStateList.valueOf(Ui.PRIMARY));
        root.addView(loadingLine, Ui.lp(-1, dp(3)));

        host = new FrameLayout(this);
        root.addView(host, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(Ui.divider(this));

        // Alt menu
        LinearLayout nav = Ui.horizontal(this);
        nav.setBackgroundColor(Ui.surface());
        nav.setPadding(0, dp(6), 0, dp(6));
        String[] names = {"Ana sayfa", "Ara", "Güncellemeler", "Kitaplık"};
        int[] icons = {Icons.HOME, Icons.SEARCH, Icons.UPDATE, Icons.LIBRARY};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = Ui.vertical(this);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setContentDescription(names[i]);
            FrameLayout iconBox = new FrameLayout(this);
            View pill = new View(this);
            navPills[i] = pill;
            iconBox.addView(pill, new FrameLayout.LayoutParams(dp(60), dp(30), Gravity.CENTER));
            ImageView iv = new ImageView(this);
            navIcons[i] = new Icons(icons[i], Ui.muted());
            iv.setImageDrawable(navIcons[i]);
            iconBox.addView(iv, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            if (i == TAB_UPDATES) {
                badge = Ui.text(this, "", 10, Color.WHITE, true);
                badge.setGravity(Gravity.CENTER);
                badge.setBackground(Ui.round(Ui.DANGER, dp(8)));
                badge.setMinWidth(dp(16));
                badge.setPadding(dp(4), 0, dp(4), 0);
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, dp(16), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                bp.leftMargin = dp(18);
                iconBox.addView(badge, bp);
            }
            item.addView(iconBox, Ui.lp(dp(64), dp(32)));
            TextView t = Ui.text(this, names[i], 12, Ui.muted(), true);
            t.setPadding(0, dp(4), 0, 0);
            t.setSingleLine(true);
            navLabels[i] = t;
            item.addView(t);
            item.setBackground(Ui.selectable(this, 16));
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (current == idx && idx == TAB_HOME) ((HomeTab) tabs[TAB_HOME]).scrollTop();
                    showTab(idx);
                }
            });
            nav.addView(item, Ui.weight(1));
        }
        root.addView(nav, Ui.lp(-1, -2));
        return root;
    }

    void showTab(int i) {
        if (i < 0 || i > 3) i = TAB_HOME;
        if (i == current) return;
        if (current >= 0) {
            tabs[current].onHide();
            tabs[current].view().setVisibility(View.GONE);
        }
        current = i;
        View v = tabs[i].view();
        if (v.getParent() == null) host.addView(v, new FrameLayout.LayoutParams(-1, -1));
        v.setVisibility(View.VISIBLE);
        tabs[i].onShow();
        for (int k = 0; k < 4; k++) {
            boolean on = k == i;
            navIcons[k].setColor(on ? Ui.primaryOn() : Ui.muted());
            navIcons[k].filled(on && (k == TAB_HOME || k == TAB_LIBRARY));
            navLabels[k].setTextColor(on ? Ui.text() : Ui.muted());
            navPills[k].setBackground(on ? Ui.round(Ui.soft(), dp(15)) : null);
        }
        String[] titles = {"Fast Store", "Ara", "Güncellemeler", "Kitaplık"};
        headerTitle.setText(titles[i]);
        searchPill.setVisibility(i == TAB_HOME ? View.VISIBLE : View.GONE);
        updateChrome();
    }

    private void updateChrome() {
        int n = CatalogRepo.get(this).updates().size();
        badge.setText(n > 9 ? "9+" : String.valueOf(n));
        badge.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        loadingLine.setVisibility(CatalogRepo.get(this).isLoading() ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public void onCatalogChanged() {
        updateChrome();
        if (current >= 0) tabs[current].render();
    }

    @Override
    public void onTaskChanged(String pkg) {
        if (current >= 0) tabs[current].onTask(pkg);
        InstallManager.Task t = InstallManager.get(this).task(pkg);
        if (t == null) onCatalogChanged(); // kurulum bitti veya iptal: listeleri tazele
    }

    @Override
    protected void onResume() {
        super.onResume();
        CatalogRepo.get(this).addListener(this);
        InstallManager.get(this).addListener(this);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(pkgReceiver, f);
        onCatalogChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        CatalogRepo.get(this).removeListener(this);
        InstallManager.get(this).removeListener(this);
        unregisterReceiver(pkgReceiver);
    }

    @Override
    public void onBackPressed() {
        if (current >= 0 && tabs[current].onBack()) return;
        if (current != TAB_HOME) {
            showTab(TAB_HOME);
            return;
        }
        super.onBackPressed();
    }
}
