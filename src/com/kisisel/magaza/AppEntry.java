package com.kisisel.magaza;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Katalogdaki tek bir uygulama. */
final class AppEntry {
    String packageName;
    String name;
    String author;
    String description;
    String changelog;
    String category;
    String versionName;
    long versionCode;
    long size;
    String apkUrl;
    String iconUrl;
    String sha256;
    String sourceName;
    final List<String> screenshots = new ArrayList<>();

    /** Katalog JSON nesnesinden okur; goreli adresler katalog adresine gore cozulur. */
    static AppEntry parse(JSONObject o, URL base, String sourceName) throws Exception {
        AppEntry e = new AppEntry();
        e.packageName = o.optString("packageName", o.optString("id", "")).trim();
        if (e.packageName.isEmpty()) throw new IllegalArgumentException("packageName eksik");
        e.name = o.optString("name", e.packageName);
        e.author = o.optString("author", "");
        e.description = o.optString("description", "");
        e.changelog = o.optString("changelog", "");
        e.category = o.optString("category", "Diğer");
        if (e.category.trim().isEmpty()) e.category = "Diğer";
        e.versionName = o.optString("versionName", "");
        e.versionCode = o.optLong("versionCode", 0);
        e.size = o.optLong("size", 0);
        e.sha256 = o.optString("sha256", "").trim().toLowerCase(java.util.Locale.ROOT);
        String apk = o.optString("apkUrl", "");
        if (apk.isEmpty()) throw new IllegalArgumentException(e.packageName + ": apkUrl eksik");
        e.apkUrl = resolve(base, apk);
        String icon = o.optString("iconUrl", "");
        e.iconUrl = icon.isEmpty() ? null : resolve(base, icon);
        JSONArray shots = o.optJSONArray("screenshots");
        if (shots != null) {
            for (int i = 0; i < shots.length(); i++) {
                String s = shots.optString(i, "");
                if (!s.isEmpty()) e.screenshots.add(resolve(base, s));
            }
        }
        e.sourceName = sourceName;
        return e;
    }

    JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("packageName", packageName);
        o.put("name", name);
        o.put("author", author);
        o.put("description", description);
        o.put("changelog", changelog);
        o.put("category", category);
        o.put("versionName", versionName);
        o.put("versionCode", versionCode);
        o.put("size", size);
        o.put("apkUrl", apkUrl);
        if (iconUrl != null) o.put("iconUrl", iconUrl);
        o.put("sha256", sha256);
        o.put("source", sourceName);
        JSONArray a = new JSONArray();
        for (String s : screenshots) a.put(s);
        o.put("screenshots", a);
        return o;
    }

    static AppEntry fromCache(JSONObject o) throws Exception {
        AppEntry e = parse(o, null, o.optString("source", ""));
        return e;
    }

    private static String resolve(URL base, String s) throws Exception {
        if (base == null) return s;
        return new URL(base, s).toString();
    }
}
