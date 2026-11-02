package com.kisisel.magaza;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Kurulum / guncelleme / kaldirma gecmisi. */
final class History {
    static final int INSTALL = 0, UPDATE = 1, UNINSTALL = 2;

    static final class Item {
        final String pkg, name, version;
        final int type;
        final long time;
        Item(String p, String n, String v, int t, long ti) { pkg = p; name = n; version = v; type = t; time = ti; }
    }

    private History() {}

    private static final String KEY = "gecmis";

    static synchronized void add(Context c, String pkg, String name, String version, int type) {
        try {
            JSONArray a = new JSONArray(Prefs.sp(c).getString(KEY, "[]"));
            JSONObject o = new JSONObject().put("p", pkg).put("n", name).put("v", version == null ? "" : version)
                    .put("t", type).put("z", System.currentTimeMillis());
            JSONArray out = new JSONArray();
            out.put(o);
            for (int i = 0; i < a.length() && i < 99; i++) out.put(a.get(i));
            Prefs.sp(c).edit().putString(KEY, out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static synchronized List<Item> all(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Prefs.sp(c).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Item(o.getString("p"), o.optString("n"), o.optString("v"), o.optInt("t"), o.optLong("z")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static synchronized void clear(Context c) {
        Prefs.sp(c).edit().remove(KEY).apply();
    }

    static String verb(int type) {
        return type == INSTALL ? "Yüklendi" : type == UPDATE ? "Güncellendi" : "Kaldırıldı";
    }
}
