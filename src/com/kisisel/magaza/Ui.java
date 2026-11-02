package com.kisisel.magaza;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Kodla arayuz kurmak icin yardimcilar ve renk paleti. */
final class Ui {
    private Ui() {}

    static final int PRIMARY = 0xFF4F46E5;
    static final int PRIMARY_DARK = 0xFF3730A3;
    static final int ACCENT = 0xFF7C3AED;
    static final int SUCCESS = 0xFF16A34A;
    static final int WARNING = 0xFFD97706;
    static final int DANGER = 0xFFDC2626;

    static boolean dark;

    static boolean computeDark(Context c) {
        int t = Prefs.theme(c);
        if (t == 1) return false;
        if (t == 2) return true;
        int m = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Her etkinligin onCreate'inde super'den once cagrilir. */
    static void applyTheme(Activity a) {
        dark = computeDark(a);
        a.setTheme(dark ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
    }

    static void styleWindow(Activity a) {
        a.getWindow().setStatusBarColor(bg());
        a.getWindow().setNavigationBarColor(surface());
        int flags = 0;
        if (!dark) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 27) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        a.getWindow().getDecorView().setSystemUiVisibility(flags);
        a.getWindow().getDecorView().setBackgroundColor(bg());
    }

    static int bg() { return dark ? 0xFF0B1020 : 0xFFF6F7FB; }
    static int surface() { return dark ? 0xFF151B2E : 0xFFFFFFFF; }
    static int text() { return dark ? 0xFFE5E7EB : 0xFF111827; }
    static int muted() { return dark ? 0xFF9CA3AF : 0xFF6B7280; }
    static int border() { return dark ? 0xFF262F48 : 0xFFE5E7EB; }
    static int soft() { return dark ? 0xFF1E2540 : 0xFFEEF2FF; }
    static int primaryOn() { return dark ? 0xFFA5B4FC : PRIMARY; }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static GradientDrawable card(Context c, float radiusDp) {
        GradientDrawable d = round(surface(), dp(c, radiusDp));
        d.setStroke(Math.max(1, dp(c, 0.8f)), border());
        return d;
    }

    static Drawable ripple(Context c, Drawable content, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(dark ? 0x33FFFFFF : 0x1A111827), content,
                round(0xFFFFFFFF, dp(c, radiusDp)));
    }

    static Drawable selectable(Context c, float radiusDp) {
        return ripple(c, null, radiusDp);
    }

    static Typeface medium() { return Typeface.create("sans-serif-medium", Typeface.NORMAL); }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(medium());
        return t;
    }

    static TextView title(Context c, String s) {
        TextView t = text(c, s, 18, text(), true);
        t.setPadding(dp(c, 16), dp(c, 20), dp(c, 16), dp(c, 10));
        return t;
    }

    static Button button(Context c, String label, int color, boolean filled) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        b.setTypeface(medium());
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setSingleLine(true);
        b.setPadding(dp(c, 18), dp(c, 8), dp(c, 18), dp(c, 8));
        style(c, b, color, filled);
        return b;
    }

    static void style(Context c, Button b, int color, boolean filled) {
        if (filled) {
            b.setTextColor(Color.WHITE);
            b.setBackground(ripple(c, round(color, dp(c, 22)), 22));
        } else {
            GradientDrawable d = round(Color.TRANSPARENT, dp(c, 22));
            d.setStroke(dp(c, 1.2f), dark ? 0xFF3B4566 : 0xFFD1D5DB);
            b.setBackground(ripple(c, d, 22));
            b.setTextColor(color);
        }
    }

    static TextView chip(Context c, String s, boolean selected) {
        TextView t = text(c, s, 13.5f, selected ? Color.WHITE : text(), true);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(dp(c, 14), dp(c, 7), dp(c, 14), dp(c, 7));
        GradientDrawable d = round(selected ? PRIMARY : surface(), dp(c, 18));
        if (!selected) d.setStroke(Math.max(1, dp(c, 0.8f)), border());
        t.setBackground(ripple(c, d, 18));
        return t;
    }

    static ImageButton iconButton(Context c, int icon, int color) {
        ImageButton b = new ImageButton(c);
        b.setImageDrawable(new Icons(icon, color));
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        b.setPadding(dp(c, 10), dp(c, 10), dp(c, 10), dp(c, 10));
        b.setBackground(selectable(c, 22));
        return b;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(border());
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 0.8f))));
        return v;
    }

    static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    static LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    static LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, -2, w); }

    /** Uygulama ikonu icin yuvarlatilmis ImageView. */
    static ImageView icon(Context c, AppEntry e, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setClipToOutline(true);
        iv.setBackground(round(soft(), dp(c, sizeDp * 0.22f)));
        ImageLoader.get(c).load(e == null ? null : e.iconUrl, iv, soft(), dp(c, sizeDp));
        return iv;
    }

    static String size(Context c, long bytes) {
        return bytes > 0 ? Formatter.formatShortFileSize(c, bytes) : "";
    }

    static String androidName(int sdk) {
        switch (sdk) {
            case 21: return "5.0";
            case 22: return "5.1";
            case 23: return "6.0";
            case 24: return "7.0";
            case 25: return "7.1";
            case 26: return "8.0";
            case 27: return "8.1";
            case 28: return "9";
            case 29: return "10";
            case 30: return "11";
            case 31: return "12";
            case 32: return "12L";
            case 33: return "13";
            case 34: return "14";
            case 35: return "15";
            case 36: return "16";
            default: return sdk <= 1 ? "Tümü" : "API " + sdk;
        }
    }

    /** Yuklenirken gosterilen nabiz animasyonlu yer tutucu. */
    static View skeleton(Context c, int wDp, int hDp, float radiusDp) {
        View v = new View(c);
        v.setBackground(round(dark ? 0xFF1E2540 : 0xFFE5E7EB, dp(c, radiusDp)));
        v.setLayoutParams(new LinearLayout.LayoutParams(wDp < 0 ? wDp : dp(c, wDp), dp(c, hDp)));
        ObjectAnimator an = ObjectAnimator.ofFloat(v, "alpha", 1f, 0.45f);
        an.setDuration(800);
        an.setRepeatMode(ValueAnimator.REVERSE);
        an.setRepeatCount(ValueAnimator.INFINITE);
        final ObjectAnimator anim = an;
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View x) { if (!anim.isStarted()) anim.start(); }
            @Override public void onViewDetachedFromWindow(View x) { anim.cancel(); }
        });
        return v;
    }
}
