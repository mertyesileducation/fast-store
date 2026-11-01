package com.kisisel.magaza;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

/** Katalog kaynaklarini indirir, birlestirir ve onbellekte tutar. */
final class CatalogRepo {
    interface Listener { void onCatalogChanged(); }

    static final String PREFS = "ayarlar";
    static final String KEY_SOURCES = "kaynaklar";
    static final String KEY_AUTO = "otomatik_kontrol";
    static final String KEY_WIFI = "sadece_wifi";
    static final String KEY_SILENT = "sessiz_guncelleme";

    private static CatalogRepo instance;

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile List<AppEntry> apps = new ArrayList<>();
    private volatile boolean loading;
    private volatile String lastError;
    private volatile long lastUpdated;

    static synchronized CatalogRepo get(Context c) {
        if (instance == null) instance = new CatalogRepo(c.getApplicationContext());
        return instance;
    }

    private CatalogRepo(Context c) {
        ctx = c;
        loadCache();
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<String> sources() {
        String raw = prefs(ctx).getString(KEY_SOURCES, "");
        List<String> out = new ArrayList<>();
        for (String s : raw.split("\\s+")) {
            s = s.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    List<AppEntry> apps() { return apps; }
    boolean isLoading() { return loading; }
    String lastError() { return lastError; }
    long lastUpdated() { return lastUpdated; }

    AppEntry find(String pkg) {
        for (AppEntry e : apps) if (e.packageName.equals(pkg)) return e;
        return null;
    }

    void addListener(Listener l) { listeners.add(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    private void notifyListeners() {
        main.post(new Runnable() {
            @Override public void run() {
                for (Listener l : listeners) l.onCatalogChanged();
            }
        });
    }

    /** Arka planda yeniler. */
    void refreshAsync() {
        if (loading) return;
        loading = true;
        notifyListeners();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    refreshBlocking();
                } catch (Exception e) {
                    // refreshBlocking hatayi zaten kaydeder
                } finally {
                    loading = false;
                    notifyListeners();
                }
            }
        }, "katalog").start();
    }

    /** Tum kaynaklari indirir. Hicbiri basarili olmazsa istisna firlatir. */
    synchronized List<AppEntry> refreshBlocking() throws Exception {
        List<String> srcs = sources();
        if (srcs.isEmpty()) {
            lastError = null;
            apps = new ArrayList<>();
            lastUpdated = 0;
            saveCache();
            return apps;
        }
        Map<String, AppEntry> merged = new LinkedHashMap<>();
        StringBuilder errors = new StringBuilder();
        int ok = 0;
        for (String src : srcs) {
            try {
                for (AppEntry e : fetch(src)) {
                    AppEntry prev = merged.get(e.packageName);
                    if (prev == null || e.versionCode > prev.versionCode) merged.put(e.packageName, e);
                }
                ok++;
            } catch (Exception ex) {
                if (errors.length() > 0) errors.append('\n');
                errors.append(src).append(": ").append(describe(ex));
            }
        }
        lastError = errors.length() == 0 ? null : errors.toString();
        if (ok == 0) throw new Exception(lastError);
        List<AppEntry> list = new ArrayList<>(merged.values());
        Collections.sort(list, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        apps = list;
        lastUpdated = System.currentTimeMillis();
        saveCache();
        return list;
    }

    static String describe(Exception ex) {
        if (ex instanceof java.net.UnknownHostException) return "Sunucu bulunamadı (internet bağlantını kontrol et)";
        if (ex instanceof java.net.SocketTimeoutException) return "Zaman aşımı";
        if (ex instanceof org.json.JSONException) return "Katalog dosyası geçerli JSON değil";
        String m = ex.getMessage();
        return m == null ? ex.getClass().getSimpleName() : m;
    }

    private List<AppEntry> fetch(String src) throws Exception {
        URL url = new URL(src);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setUseCaches(false);
        c.setRequestProperty("Cache-Control", "no-cache");
        c.setRequestProperty("User-Agent", "FastStore/1.0 (Android)");
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        String body;
        try (InputStream in = c.getInputStream()) {
            body = readAll(in);
        } finally {
            c.disconnect();
        }
        URL base = c.getURL(); // yonlendirmeden sonraki gercek adres
        body = body.trim();
        JSONArray arr;
        String storeName = url.getHost();
        if (body.startsWith("[")) {
            arr = new JSONArray(body);
        } else {
            JSONObject root = new JSONObject(body);
            storeName = root.optString("name", storeName);
            arr = root.optJSONArray("apps");
            if (arr == null) throw new Exception("\"apps\" listesi bulunamadı");
        }
        List<AppEntry> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            try {
                out.add(AppEntry.parse(o, base, storeName));
            } catch (Exception ignored) {
                // hatali girdileri atla
            }
        }
        return out;
    }

    static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private File cacheFile() { return new File(ctx.getFilesDir(), "katalog.json"); }

    private void saveCache() {
        try {
            JSONObject root = new JSONObject();
            root.put("updated", lastUpdated);
            JSONArray a = new JSONArray();
            for (AppEntry e : apps) a.put(e.toJson());
            root.put("apps", a);
            File tmp = new File(ctx.getFilesDir(), "katalog.json.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
            tmp.renameTo(cacheFile());
        } catch (Exception ignored) {
        }
    }

    private void loadCache() {
        File f = cacheFile();
        if (!f.exists()) return;
        try (InputStream in = new FileInputStream(f)) {
            JSONObject root = new JSONObject(readAll(in));
            lastUpdated = root.optLong("updated", 0);
            JSONArray a = root.getJSONArray("apps");
            List<AppEntry> list = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) list.add(AppEntry.fromCache(a.getJSONObject(i)));
            apps = list;
        } catch (Exception ignored) {
        }
    }

    void clearCache() {
        cacheFile().delete();
        apps = new ArrayList<>();
        lastUpdated = 0;
        notifyListeners();
    }

    List<String> categories() {
        TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (AppEntry e : apps) set.add(e.category);
        return new ArrayList<>(set);
    }

    // ---- Yuklu uygulama bilgileri ----

    /** Yuklu degilse -1 doner. */
    static long installedVersion(Context c, String pkg) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(pkg, 0);
            if (Build.VERSION.SDK_INT >= 28) return pi.getLongVersionCode();
            return pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    static String installedVersionName(Context c, String pkg) {
        try {
            return c.getPackageManager().getPackageInfo(pkg, 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    static boolean hasUpdate(Context c, AppEntry e) {
        long v = installedVersion(c, e.packageName);
        return v >= 0 && e.versionCode > v;
    }

    List<AppEntry> updates() {
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : apps) if (hasUpdate(ctx, e)) out.add(e);
        return out;
    }

    List<AppEntry> installed() {
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : apps) if (installedVersion(ctx, e.packageName) >= 0) out.add(e);
        return out;
    }
}
