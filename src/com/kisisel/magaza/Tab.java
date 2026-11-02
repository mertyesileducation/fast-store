package com.kisisel.magaza;

import android.view.View;

/** Alt menudeki sekmelerin temeli. */
abstract class Tab {
    final MainActivity a;
    final AppViews.Group group = new AppViews.Group();
    private View view;

    Tab(MainActivity a) { this.a = a; }

    final View view() {
        if (view == null) view = build();
        return view;
    }

    abstract View build();

    /** Katalog veya yuklu uygulamalar degisti: icerigi yeniden kur. */
    abstract void render();

    void onShow() { render(); }
    void onHide() {}

    /** Kurulum durumu degisti: sadece ilgili gorunumleri guncelle. */
    void onTask(String pkg) { group.rebind(pkg); }

    boolean onBack() { return false; }

    int dp(float v) { return Ui.dp(a, v); }
}
