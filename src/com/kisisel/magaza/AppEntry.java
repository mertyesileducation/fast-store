package com.kisisel.magaza;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Katalogdaki tek bir uygulama. */
final class AppEntry {
    String packageName, name, author, shortDescription, description, changelog, category;
    String versionName, apkUrl, iconUrl, bannerUrl, sha256, signer, website, email, privacy;
    String sourceName;
    long versionCode, size, added, updated;
    int minSdk, targetSdk;
    boolean featured;
    final List<String> screenshots = new ArrayList<>();
    final List<String> permissions = new ArrayList<>();
    final List<String> tags = new ArrayList<>();
    /** Ham JSON (onbellege yazmak icin; adresler cozulmus halde). */
    JSONObject raw;

    static AppEntry parse(JSONObject o, URL base, String sourceName) throws Exception {
        AppEntry e = new AppEntry();
        e.packageName = o.optString("packageName", o.optString("id", "")).trim();
        if (e.packageName.isEmpty()) throw new IllegalArgumentException("packageName eksik");
        e.name = o.optString("name", e.packageName);
        e.author = o.optString("author", "");
        e.shortDescription = o.optString("shortDescription", "");
        e.description = o.optString("description", "");
        e.changelog = o.optString("changelog", "");
        e.category = o.optString("category", "Diğer").trim();
        if (e.category.isEmpty()) e.category = "Diğer";
        e.versionName = o.optString("versionName", "");
        e.versionCode = o.optLong("versionCode", 0);
        e.size = o.optLong("size", 0);
        e.minSdk = o.optInt("minSdk", 1);
        e.targetSdk = o.optInt("targetSdk", 0);
        e.sha256 = o.optString("sha256", "").trim().toLowerCase(Locale.ROOT);
        e.signer = o.optString("signer", "").trim().toLowerCase(Locale.ROOT).replace(":", "");
        e.featured = o.optBoolean("featured", false);
        e.website = o.optString("website", "");
        e.email = o.optString("email", "");
        e.privacy = o.optString("privacy", "");
        e.added = date(o.optString("added", ""));
        e.updated = date(o.optString("updated", ""));
        String apk = o.optString("apkUrl", "");
        if (apk.isEmpty()) throw new IllegalArgumentException(e.packageName + ": apkUrl eksik");
        e.apkUrl = resolve(base, apk);
        e.iconUrl = optUrl(o, "iconUrl", base);
        e.bannerUrl = optUrl(o, "bannerUrl", base);
        list(o.optJSONArray("screenshots"), e.screenshots, base);
        list(o.optJSONArray("permissions"), e.permissions, null);
        list(o.optJSONArray("tags"), e.tags, null);
        e.sourceName = sourceName;

        // cozulmus adreslerle kopya
        JSONObject r = new JSONObject(o.toString());
        r.put("apkUrl", e.apkUrl);
        if (e.iconUrl != null) r.put("iconUrl", e.iconUrl);
        if (e.bannerUrl != null) r.put("bannerUrl", e.bannerUrl);
        JSONArray s = new JSONArray();
        for (String x : e.screenshots) s.put(x);
        r.put("screenshots", s);
        r.put("_source", sourceName);
        e.raw = r;
        return e;
    }

    static AppEntry fromCache(JSONObject o) throws Exception {
        return parse(o, null, o.optString("_source", ""));
    }

    private static String optUrl(JSONObject o, String key, URL base) throws Exception {
        String v = o.optString(key, "");
        return v.isEmpty() ? null : resolve(base, v);
    }

    private static void list(JSONArray a, List<String> out, URL base) throws Exception {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "").trim();
            if (!s.isEmpty()) out.add(base == null ? s : resolve(base, s));
        }
    }

    private static long date(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(s).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String resolve(URL base, String s) throws Exception {
        return base == null ? s : new URL(base, s).toString();
    }

    String subtitle() {
        return shortDescription.isEmpty() ? category : shortDescription;
    }

    boolean compatible() {
        return android.os.Build.VERSION.SDK_INT >= minSdk;
    }
}
