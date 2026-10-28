package com.blackfalcon.quakewatch;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Downloads and parses earthquake lists (USGS GeoJSON feeds or EMSC FDSN GeoJSON). */
public class Feed {

    public static class Result {
        public List<Quake> list = new ArrayList<Quake>();
        /** 0 = fresh from the internet, otherwise the time the saved copy was downloaded. */
        public long cacheTime = 0;
    }

    public static String usgsUrl(String mag, String period) {
        String m = mag;
        if ("all".equals(m) && "month".equals(period)) m = "2.5";
        return "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/" + m + "_" + period + ".geojson";
    }

    public static String emscUrl(String mag, String period, long now) {
        long span;
        if ("hour".equals(period)) span = 3600000L;
        else if ("day".equals(period)) span = 86400000L;
        else if ("week".equals(period)) span = 7L * 86400000L;
        else span = 30L * 86400000L;
        String m = mag;
        if ("all".equals(m) && "month".equals(period)) m = "2.5";
        String minmag = "all".equals(m) ? "0" : m;
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return "https://www.seismicportal.eu/fdsnws/event/1/query?format=json&limit=1000&orderby=time"
                + "&minmagnitude=" + minmag + "&starttime=" + f.format(new Date(now - span));
    }

    public static Result fetch(Context c, String source, String mag, String period, boolean allowCache) throws Exception {
        boolean emsc = "emsc".equals(source);
        String url = emsc ? emscUrl(mag, period, System.currentTimeMillis()) : usgsUrl(mag, period);
        File cf = new File(c.getCacheDir(), "feed_" + source + "_" + mag + "_" + period + ".json");
        Result r = new Result();
        try {
            String body = http(url);
            r.list = emsc ? parseEmsc(body) : parseUsgs(body);
            r.cacheTime = 0;
            try {
                FileOutputStream fo = new FileOutputStream(cf);
                fo.write(body.getBytes("UTF-8"));
                fo.close();
            } catch (Exception ignore) {
                // cache is optional
            }
            return r;
        } catch (Exception e) {
            if (allowCache && cf.exists()) {
                try {
                    String body = readFile(cf);
                    r.list = emsc ? parseEmsc(body) : parseUsgs(body);
                    r.cacheTime = cf.lastModified();
                    return r;
                } catch (Exception ignore) {
                    // fall through and report the original error
                }
            }
            throw e;
        }
    }

    private static String readFile(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    private static String http(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "QuakeWatch/1.0 (Android)");
        c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            throw new IOException("HTTP " + code);
        }
        InputStream in = c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        c.disconnect();
        return bo.toString("UTF-8");
    }

    private static String str(JSONObject o, String k) {
        if (o.isNull(k)) return "";
        return o.optString(k, "");
    }

    private static void sortNewest(List<Quake> l) {
        Collections.sort(l, new Comparator<Quake>() {
            @Override
            public int compare(Quake a, Quake b) {
                return Long.compare(b.time, a.time);
            }
        });
    }

    static List<Quake> parseUsgs(String body) throws Exception {
        List<Quake> out = new ArrayList<Quake>();
        JSONArray f = new JSONObject(body).getJSONArray("features");
        for (int i = 0; i < f.length(); i++) {
            try {
                JSONObject o = f.getJSONObject(i);
                JSONObject p = o.getJSONObject("properties");
                if (p.isNull("mag")) continue;
                String type = str(p, "type");
                if (type.length() > 0 && !"earthquake".equals(type)) continue;
                JSONArray g = o.getJSONObject("geometry").getJSONArray("coordinates");
                Quake q = new Quake();
                q.id = o.optString("id", "");
                q.mag = p.getDouble("mag");
                q.magType = str(p, "magType");
                q.place = str(p, "place");
                q.time = p.getLong("time");
                q.lon = g.getDouble(0);
                q.lat = g.getDouble(1);
                q.depth = g.length() > 2 ? g.optDouble(2, 0) : 0;
                q.url = str(p, "url");
                q.felt = p.isNull("felt") ? -1 : p.optInt("felt", -1);
                q.tsunami = p.optInt("tsunami", 0);
                q.alert = str(p, "alert");
                q.sig = p.optInt("sig", 0);
                q.status = str(p, "status");
                q.source = "USGS";
                out.add(q);
            } catch (Exception skip) {
                // ignore one bad row
            }
        }
        sortNewest(out);
        return out;
    }

    static List<Quake> parseEmsc(String body) throws Exception {
        List<Quake> out = new ArrayList<Quake>();
        JSONArray f = new JSONObject(body).getJSONArray("features");
        for (int i = 0; i < f.length(); i++) {
            try {
                JSONObject o = f.getJSONObject(i);
                JSONObject p = o.getJSONObject("properties");
                double mag = p.optDouble("mag", Double.NaN);
                if (Double.isNaN(mag)) continue;
                double lat = p.optDouble("lat", Double.NaN);
                double lon = p.optDouble("lon", Double.NaN);
                double depth = p.optDouble("depth", Double.NaN);
                JSONObject geo = o.optJSONObject("geometry");
                if (geo != null) {
                    JSONArray g = geo.optJSONArray("coordinates");
                    if (g != null && g.length() >= 2) {
                        if (Double.isNaN(lon)) lon = g.getDouble(0);
                        if (Double.isNaN(lat)) lat = g.getDouble(1);
                        if (Double.isNaN(depth) && g.length() > 2) depth = Math.abs(g.getDouble(2));
                    }
                }
                if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
                if (Double.isNaN(depth)) depth = 0;
                Quake q = new Quake();
                q.mag = mag;
                q.magType = str(p, "magtype");
                q.place = str(p, "flynn_region");
                q.time = Geo.parseIso(str(p, "time"));
                if (q.time == 0) continue;
                q.lat = lat;
                q.lon = lon;
                q.depth = Math.abs(depth);
                String sid = str(p, "source_id");
                q.id = "emsc:" + (sid.length() > 0 ? sid : str(p, "unid"));
                if (sid.length() > 0) {
                    q.url = "https://www.emsc-csem.org/Earthquake_information/earthquake.php?id=" + sid;
                }
                q.source = "EMSC";
                out.add(q);
            } catch (Exception skip) {
                // ignore one bad row
            }
        }
        sortNewest(out);
        return out;
    }
}
