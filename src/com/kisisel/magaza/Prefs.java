package com.kisisel.magaza;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** Kullanici ayarlari. */
final class Prefs {
    private Prefs() {}

    static final String THEME = "tema";                  // 0 sistem, 1 acik, 2 koyu
    static final String AUTO_UPDATE = "otomatik_guncelle"; // 0 kapali, 1 wifi, 2 her zaman
    static final String NOTIFY_UPDATES = "guncelleme_bildirimi";
    static final String SILENT = "sessiz_guncelleme";
    static final String RECENT_SEARCH = "son_aramalar";
    static final String LAST_CHECK = "son_kontrol";

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("ayarlar", Context.MODE_PRIVATE);
    }

    static int theme(Context c) { return sp(c).getInt(THEME, 0); }
    static int autoUpdate(Context c) { return sp(c).getInt(AUTO_UPDATE, 1); }
    static boolean notifyUpdates(Context c) { return sp(c).getBoolean(NOTIFY_UPDATES, true); }
    static boolean silent(Context c) { return sp(c).getBoolean(SILENT, true); }
    static long lastCheck(Context c) { return sp(c).getLong(LAST_CHECK, 0); }

    static void put(Context c, String k, int v) { sp(c).edit().putInt(k, v).apply(); }
    static void put(Context c, String k, boolean v) { sp(c).edit().putBoolean(k, v).apply(); }
    static void put(Context c, String k, long v) { sp(c).edit().putLong(k, v).apply(); }

    static List<String> recentSearches(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString(RECENT_SEARCH, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    static void addRecentSearch(Context c, String q) {
        q = q.trim();
        if (q.length() < 2) return;
        List<String> l = recentSearches(c);
        l.remove(q);
        l.add(0, q);
        JSONArray a = new JSONArray();
        for (int i = 0; i < Math.min(8, l.size()); i++) a.put(l.get(i));
        sp(c).edit().putString(RECENT_SEARCH, a.toString()).apply();
    }

    static void clearRecentSearches(Context c) {
        sp(c).edit().remove(RECENT_SEARCH).apply();
    }
}
