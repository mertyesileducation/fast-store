package com.kisisel.magaza;

import android.content.Context;
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
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Katalogu indirir, onbellekte tutar ve sorgular. */
final class CatalogRepo {
    interface Listener { void onCatalogChanged(); }

    /** Magazanin sabit katalog adresi (kullanici degistiremez). */
    static final String CATALOG_URL =
            "https://raw.githubusercontent.com/mertyesileducation/fast-store/main/katalog/katalog.json";

    private static CatalogRepo instance;

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile List<AppEntry> apps = new ArrayList<>();
    private volatile boolean loading;
    private volatile String lastError;
    private volatile long lastUpdated;
    private volatile String storeName = "Fast Store";

    static synchronized CatalogRepo get(Context c) {
        if (instance == null) instance = new CatalogRepo(c.getApplicationContext());
        return instance;
    }

    private CatalogRepo(Context c) {
        ctx = c;
        loadCache();
    }

    List<AppEntry> apps() { return apps; }
    boolean isLoading() { return loading; }
    String lastError() { return lastError; }
    long lastUpdated() { return lastUpdated; }
    String storeName() { return storeName; }
    boolean isEmpty() { return apps.isEmpty(); }

    AppEntry find(String pkg) {
        if (pkg == null) return null;
        for (AppEntry e : apps) if (e.packageName.equals(pkg)) return e;
        return null;
    }

    void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    void notifyListeners() {
        main.post(new Runnable() {
            @Override public void run() {
                for (Listener l : listeners) l.onCatalogChanged();
            }
        });
    }

    boolean isStale() { return System.currentTimeMillis() - lastUpdated > 10 * 60 * 1000; }

    void refreshAsync() {
        if (loading) return;
        loading = true;
        notifyListeners();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    refreshBlocking();
                } catch (Exception ignored) {
                } finally {
                    loading = false;
                    notifyListeners();
                }
            }
        }, "katalog").start();
    }

    synchronized List<AppEntry> refreshBlocking() throws Exception {
        try {
            List<AppEntry> list = fetch(CATALOG_URL);
            Collections.sort(list, new Comparator<AppEntry>() {
                @Override public int compare(AppEntry a, AppEntry b) { return a.name.compareToIgnoreCase(b.name); }
            });
            apps = list;
            lastError = null;
            lastUpdated = System.currentTimeMillis();
            Prefs.put(ctx, Prefs.LAST_CHECK, lastUpdated);
            saveCache();
            return list;
        } catch (Exception ex) {
            lastError = describe(ex);
            throw ex;
        }
    }

    static String describe(Exception ex) {
        if (ex instanceof java.net.UnknownHostException) return "İnternet bağlantısı yok";
        if (ex instanceof java.net.SocketTimeoutException) return "Sunucu yanıt vermedi (zaman aşımı)";
        if (ex instanceof java.net.ConnectException) return "Sunucuya bağlanılamadı";
        if (ex instanceof javax.net.ssl.SSLException) return "Güvenli bağlantı kurulamadı";
        if (ex instanceof org.json.JSONException) return "Katalog dosyası bozuk";
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
        c.setRequestProperty("User-Agent", userAgent(ctx));
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("Sunucu hatası (HTTP " + code + ")");
        String body;
        try (InputStream in = c.getInputStream()) {
            body = readAll(in);
        } finally {
            c.disconnect();
        }
        URL base = c.getURL();
        JSONObject root = new JSONObject(body.trim());
        storeName = root.optString("name", "Fast Store");
        JSONArray arr = root.optJSONArray("apps");
        if (arr == null) throw new Exception("Katalogda uygulama listesi yok");
        List<AppEntry> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            try {
                out.add(AppEntry.parse(o, base, storeName));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    static String userAgent(Context c) {
        String v = "2";
        try { v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception ignored) {}
        return "FastStore/" + v + " (Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ")";
    }

    static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private File cacheFile() { return new File(ctx.getFilesDir(), "katalog_v2.json"); }

    private void saveCache() {
        try {
            JSONObject root = new JSONObject();
            root.put("updated", lastUpdated);
            root.put("name", storeName);
            JSONArray a = new JSONArray();
            for (AppEntry e : apps) a.put(e.raw);
            root.put("apps", a);
            File tmp = new File(ctx.getFilesDir(), "katalog_v2.json.tmp");
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
            storeName = root.optString("name", storeName);
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

    // ---- Sorgular ----

    List<AppEntry> featured() {
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : apps) if (e.featured) out.add(e);
        if (out.isEmpty()) out.addAll(recentlyUpdated(5));
        return out;
    }

    List<AppEntry> newest(int max) {
        List<AppEntry> l = new ArrayList<>(apps);
        Collections.sort(l, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) { return Long.compare(b.added, a.added); }
        });
        return l.subList(0, Math.min(max, l.size()));
    }

    List<AppEntry> recentlyUpdated(int max) {
        List<AppEntry> l = new ArrayList<>(apps);
        Collections.sort(l, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) { return Long.compare(b.updated, a.updated); }
        });
        return l.subList(0, Math.min(max, l.size()));
    }

    /** Kategori -> uygulamalar (kategori adina gore sirali). */
    Map<String, List<AppEntry>> byCategory() {
        Map<String, List<AppEntry>> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (AppEntry e : apps) {
            List<AppEntry> l = m.get(e.category);
            if (l == null) {
                l = new ArrayList<>();
                m.put(e.category, l);
            }
            l.add(e);
        }
        return m;
    }

    List<AppEntry> inCategory(String cat) {
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : apps) if (e.category.equalsIgnoreCase(cat)) out.add(e);
        return out;
    }

    List<AppEntry> similar(AppEntry x, int max) {
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : apps) {
            if (e == x || e.packageName.equals(x.packageName)) continue;
            if (e.category.equalsIgnoreCase(x.category)) out.add(e);
        }
        for (AppEntry e : apps) {
            if (out.size() >= max) break;
            if (!e.packageName.equals(x.packageName) && !out.contains(e)) out.add(e);
        }
        return out.subList(0, Math.min(max, out.size()));
    }

    List<AppEntry> fromAuthor(String author, String exceptPkg) {
        List<AppEntry> out = new ArrayList<>();
        if (author == null || author.isEmpty()) return out;
        for (AppEntry e : apps) {
            if (e.author.equalsIgnoreCase(author) && !e.packageName.equals(exceptPkg)) out.add(e);
        }
        return out;
    }

    /** Ad, etiket, gelistirici ve aciklamaya gore puanlanmis arama. */
    List<AppEntry> search(String query) {
        final String q = norm(query);
        List<AppEntry> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        final Map<AppEntry, Integer> score = new LinkedHashMap<>();
        for (AppEntry e : apps) {
            int s = 0;
            String name = norm(e.name);
            if (name.equals(q)) s += 100;
            else if (name.startsWith(q)) s += 60;
            else if (name.contains(q)) s += 40;
            for (String t : e.tags) if (norm(t).startsWith(q)) s += 25;
            if (norm(e.category).startsWith(q)) s += 20;
            if (norm(e.author).contains(q)) s += 15;
            if (norm(e.shortDescription).contains(q)) s += 10;
            if (norm(e.description).contains(q)) s += 5;
            if (e.packageName.toLowerCase(Locale.ROOT).contains(q)) s += 5;
            if (s > 0) score.put(e, s);
        }
        out.addAll(score.keySet());
        Collections.sort(out, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) { return score.get(b) - score.get(a); }
        });
        return out;
    }

    /** Turkce karakter duyarsiz kucuk harf. */
    static String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase(new Locale("tr", "TR")).replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u')
                .replace('ş', 's').replace('ö', 'o').replace('ç', 'c').trim();
    }

    // ---- Yuklu uygulama bilgileri ----

    static long installedVersion(Context c, String pkg) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(pkg, 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
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

    static long installedSize(Context c, String pkg) {
        try {
            return new File(c.getPackageManager().getApplicationInfo(pkg, 0).sourceDir).length();
        } catch (Exception e) {
            return 0;
        }
    }

    static long firstInstallTime(Context c, String pkg) {
        try {
            return c.getPackageManager().getPackageInfo(pkg, 0).firstInstallTime;
        } catch (Exception e) {
            return 0;
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
