package com.kisisel.magaza;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/** Ekran goruntulerini tam ekranda, kaydirarak gosterir. */
public class ScreenshotActivity extends Activity {
    static final String EXTRA_URLS = "adresler";
    static final String EXTRA_INDEX = "sira";

    private HorizontalScrollView pager;
    private TextView counter;
    private int page, count, width;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        final ArrayList<String> urls = getIntent().getStringArrayListExtra(EXTRA_URLS);
        if (urls == null || urls.isEmpty()) {
            finish();
            return;
        }
        count = urls.size();
        page = b != null ? b.getInt("sira") : getIntent().getIntExtra(EXTRA_INDEX, 0);
        width = getResources().getDisplayMetrics().widthPixels;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        pager = new HorizontalScrollView(this);
        pager.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        for (String u : urls) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ImageLoader.get(this).load(u, iv, Color.BLACK, Math.max(width, getResources().getDisplayMetrics().heightPixels));
            row.addView(iv, new LinearLayout.LayoutParams(width, -1));
        }
        pager.addView(row, new FrameLayout.LayoutParams(-2, -1));
        pager.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            @Override public boolean onTouch(View v, MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN) downX = ev.getX();
                if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                    float dx = ev.getX() - downX;
                    if (dx < -width / 6f) page = Math.min(count - 1, page + 1);
                    else if (dx > width / 6f) page = Math.max(0, page - 1);
                    go(true);
                    return true;
                }
                return false;
            }
        });
        root.addView(pager, new FrameLayout.LayoutParams(-1, -1));

        ImageButton close = Ui.iconButton(this, Icons.CLOSE, Color.WHITE);
        close.setBackground(Ui.round(0x66000000, Ui.dp(this, 22)));
        close.setContentDescription("Kapat");
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44), Gravity.TOP | Gravity.START);
        cp.setMargins(Ui.dp(this, 12), Ui.dp(this, 12), 0, 0);
        root.addView(close, cp);

        counter = Ui.text(this, "", 14, Color.WHITE, true);
        counter.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        counter.setBackground(Ui.round(0x66000000, Ui.dp(this, 14)));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        tp.bottomMargin = Ui.dp(this, 28);
        root.addView(counter, tp);
        if (count < 2) counter.setVisibility(View.GONE);

        setContentView(root);
        pager.post(new Runnable() {
            @Override public void run() { go(false); }
        });
    }

    private void go(boolean smooth) {
        if (smooth) pager.smoothScrollTo(page * width, 0);
        else pager.scrollTo(page * width, 0);
        counter.setText((page + 1) + " / " + count);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("sira", page);
    }
}
