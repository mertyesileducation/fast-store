package com.kisisel.magaza;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/** Kodla arayuz olusturmak icin yardimcilar ve renk paleti. */
final class Ui {
    private Ui() {}

    static final int PRIMARY = 0xFF2563EB;
    static final int PRIMARY_DARK = 0xFF1D4ED8;
    static final int SUCCESS = 0xFF16A34A;
    static final int DANGER = 0xFFDC2626;

    static boolean dark(Context c) {
        int m = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }

    static int bg(Context c) { return dark(c) ? 0xFF0F172A : 0xFFF5F7FB; }
    static int card(Context c) { return dark(c) ? 0xFF1E293B : 0xFFFFFFFF; }
    static int text(Context c) { return dark(c) ? 0xFFF1F5F9 : 0xFF0F172A; }
    static int sub(Context c) { return dark(c) ? 0xFF94A3B8 : 0xFF64748B; }
    static int line(Context c) { return dark(c) ? 0xFF334155 : 0xFFE2E8F0; }
    static int chip(Context c) { return dark(c) ? 0xFF334155 : 0xFFE8EEF9; }

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

    static Drawable ripple(Context c, int color, float radiusDp) {
        GradientDrawable shape = round(color, dp(c, radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), shape,
                round(0xFFFFFFFF, dp(c, radiusDp)));
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    static Button button(Context c, String label, int color, boolean filled) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        style(c, b, color, filled);
        return b;
    }

    static void style(Context c, Button b, int color, boolean filled) {
        if (filled) {
            b.setTextColor(Color.WHITE);
            b.setBackground(ripple(c, color, 20));
        } else {
            GradientDrawable d = round(Color.TRANSPARENT, dp(c, 20));
            d.setStroke(dp(c, 1), color);
            b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), d,
                    round(0xFFFFFFFF, dp(c, 20))));
            b.setTextColor(color);
        }
    }

    static TextView chip(Context c, String s, boolean selected) {
        TextView t = text(c, s, 13, selected ? Color.WHITE : text(c), true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 6));
        t.setBackground(round(selected ? PRIMARY : chip(c), dp(c, 16)));
        return t;
    }

    static void styleBars(Activity a) {
        if (a.getActionBar() != null) {
            a.getActionBar().setBackgroundDrawable(new ColorDrawable(PRIMARY));
            a.getActionBar().setElevation(0);
        }
        a.getWindow().setStatusBarColor(PRIMARY_DARK);
        a.getWindow().setNavigationBarColor(bg(a));
        a.getWindow().getDecorView().setBackgroundColor(bg(a));
        if (!dark(a) && android.os.Build.VERSION.SDK_INT >= 27) {
            a.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(line(c));
        return v;
    }
}
