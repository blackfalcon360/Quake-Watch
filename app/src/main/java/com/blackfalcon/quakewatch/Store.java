package com.blackfalcon.quakewatch;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public class Store {

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("quakewatch", Context.MODE_PRIVATE);
    }

    private static String gs(Context c, String k, String d) { return p(c).getString(k, d); }
    private static void ps(Context c, String k, String v) { p(c).edit().putString(k, v).apply(); }
    private static int gi(Context c, String k, int d) { return p(c).getInt(k, d); }
    private static void pin(Context c, String k, int v) { p(c).edit().putInt(k, v).apply(); }
    private static boolean gb(Context c, String k, boolean d) { return p(c).getBoolean(k, d); }
    private static void pb(Context c, String k, boolean v) { p(c).edit().putBoolean(k, v).apply(); }
    private static long gl(Context c, String k, long d) { return p(c).getLong(k, d); }
    private static void pl(Context c, String k, long v) { p(c).edit().putLong(k, v).apply(); }

    private static double gd(Context c, String k, double d) {
        try {
            return Double.parseDouble(p(c).getString(k, Double.toString(d)));
        } catch (Exception e) {
            return d;
        }
    }

    private static void pd(Context c, String k, double v) { ps(c, k, Double.toString(v)); }

    // ---- settings ----
    public static String lang(Context c) { return gs(c, "lang", "en"); }
    public static void setLang(Context c, String v) { ps(c, "lang", v); }

    public static String theme(Context c) { return gs(c, "theme", "dark"); }
    public static void setTheme(Context c, String v) { ps(c, "theme", v); }

    public static String source(Context c) { return gs(c, "source", "usgs"); }
    public static void setSource(Context c, String v) { ps(c, "source", v); }

    public static boolean notif(Context c) { return gb(c, "notif", true); }
    public static void setNotif(Context c, boolean v) { pb(c, "notif", v); }

    public static boolean useLoc(Context c) { return gb(c, "useLoc", true); }
    public static void setUseLoc(Context c, boolean v) { pb(c, "useLoc", v); }

    public static int radius(Context c) { return gi(c, "radius", 300); }
    public static void setRadius(Context c, int v) { pin(c, "radius", v); }

    public static double minNear(Context c) { return gd(c, "minNear", 2.5); }
    public static void setMinNear(Context c, double v) { pd(c, "minNear", v); }

    public static double worldMin(Context c) { return gd(c, "worldMin", 6.0); }
    public static void setWorldMin(Context c, double v) { pd(c, "worldMin", v); }

    public static int interval(Context c) { return gi(c, "interval", 10); }
    public static void setInterval(Context c, int v) { pin(c, "interval", v); }

    public static boolean permAsked(Context c) { return gb(c, "permAsked", false); }
    public static void setPermAsked(Context c) { pb(c, "permAsked", true); }

    // ---- saved location ----
    public static boolean hasLoc(Context c) { return gb(c, "hasLoc", false); }
    public static double lat(Context c) { return gd(c, "lat", 0); }
    public static double lon(Context c) { return gd(c, "lon", 0); }
    public static long locTime(Context c) { return gl(c, "locTime", 0); }

    public static void saveLoc(Context c, double lat, double lon, long timeMs) {
        pd(c, "lat", lat);
        pd(c, "lon", lon);
        pl(c, "locTime", timeMs);
        pb(c, "hasLoc", true);
    }

    // ---- poller state ----
    public static long lastCheck(Context c) { return gl(c, "lastCheck", 0); }
    public static void setLastCheck(Context c, long v) { pl(c, "lastCheck", v); }

    public static LinkedHashSet<String> seen(Context c) {
        LinkedHashSet<String> s = new LinkedHashSet<String>();
        String raw = gs(c, "seen", "");
        if (raw.length() > 0) {
            for (String x : raw.split("\n")) {
                if (x.length() > 0) s.add(x);
            }
        }
        return s;
    }

    public static void saveSeen(Context c, LinkedHashSet<String> s) {
        ArrayList<String> l = new ArrayList<String>(s);
        int from = Math.max(0, l.size() - 600);
        StringBuilder b = new StringBuilder();
        for (int i = from; i < l.size(); i++) {
            if (b.length() > 0) b.append('\n');
            b.append(l.get(i));
        }
        ps(c, "seen", b.toString());
    }
}
