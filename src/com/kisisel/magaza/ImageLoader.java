package com.kisisel.magaza;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bellek + disk onbellekli gorsel yukleyici (yumusak gecisli). */
final class ImageLoader {
    private static ImageLoader instance;

    private final File dir;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final LruCache<String, Bitmap> mem;

    static synchronized ImageLoader get(Context c) {
        if (instance == null) instance = new ImageLoader(c.getApplicationContext());
        return instance;
    }

    private ImageLoader(Context c) {
        dir = new File(c.getCacheDir(), "gorseller");
        dir.mkdirs();
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        mem = new LruCache<String, Bitmap>(maxKb) {
            @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount() / 1024; }
        };
    }

    void load(final String url, final ImageView view, final int placeholder, final int maxPx) {
        view.setTag(url);
        if (url == null) {
            view.setImageDrawable(null);
            return;
        }
        final String key = url + "#" + maxPx;
        Bitmap b = mem.get(key);
        if (b != null) {
            view.setImageBitmap(b);
            return;
        }
        view.setImageDrawable(null);
        pool.execute(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = fetch(url, maxPx);
                if (bmp == null) return;
                mem.put(key, bmp);
                main.post(new Runnable() {
                    @Override public void run() {
                        if (!url.equals(view.getTag())) return;
                        view.setAlpha(0f);
                        view.setImageBitmap(bmp);
                        view.animate().alpha(1f).setDuration(180).start();
                    }
                });
            }
        });
    }

    private Bitmap fetch(String url, int maxPx) {
        try {
            File f = new File(dir, sha1(url));
            if (!f.exists()) {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                if (c.getResponseCode() != 200) return null;
                File tmp = new File(dir, f.getName() + ".tmp");
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                } finally {
                    c.disconnect();
                }
                if (!tmp.renameTo(f)) return null;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= maxPx) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (Exception e) {
            return null;
        }
    }

    long diskSize() {
        long n = 0;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) n += f.length();
        return n;
    }

    void clear() {
        mem.evictAll();
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
    }

    static String sha1(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
