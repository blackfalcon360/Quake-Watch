package com.blackfalcon.quakewatch;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/** Pure helper functions (no Android classes) so they can be tested separately. */
public class Geo {

    /** Great-circle distance in km (haversine). */
    public static double km(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0088;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Initial bearing from point 1 to point 2, 0..360 degrees. */
    public static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        double b = Math.toDegrees(Math.atan2(y, x));
        return (b + 360.0) % 360.0;
    }

    /** 0=N 1=NE 2=E 3=SE 4=S 5=SW 6=W 7=NW */
    public static int sector(double bearing) {
        return (int) Math.floor(((bearing + 22.5) % 360.0) / 45.0);
    }

    public static String fmt1(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    public static String fmtDist(double km) {
        if (km < 10) return String.format(Locale.US, "%.1f", km);
        return String.format(Locale.US, "%.0f", km);
    }

    /** 0 micro, 1 minor, 2 light, 3 moderate, 4 strong, 5 major, 6 great */
    public static int magClass(double m) {
        if (m < 2.0) return 0;
        if (m < 4.0) return 1;
        if (m < 5.0) return 2;
        if (m < 6.0) return 3;
        if (m < 7.0) return 4;
        if (m < 8.0) return 5;
        return 6;
    }

    /** 0:<2  1:2-3.9  2:4-4.9  3:5-5.9  4:6+ */
    public static int bucket5(double m) {
        if (m < 2.0) return 0;
        if (m < 4.0) return 1;
        if (m < 5.0) return 2;
        if (m < 6.0) return 3;
        return 4;
    }

    public static int magColor(double m) {
        if (m < 2.0) return 0xFF607D8B;
        if (m < 3.0) return 0xFF43A047;
        if (m < 4.0) return 0xFFB59A00;
        if (m < 5.0) return 0xFFFB8C00;
        if (m < 6.0) return 0xFFE53935;
        if (m < 7.0) return 0xFFB71C1C;
        return 0xFF8E24AA;
    }

    /** 0 shallow (<70 km), 1 intermediate (70-300), 2 deep (>300) */
    public static int depthClass(double d) {
        if (d < 70.0) return 0;
        if (d <= 300.0) return 1;
        return 2;
    }

    /** Parses "2026-10-27T08:14:03.5Z" (also +00:00) to epoch millis; 0 on failure. */
    public static long parseIso(String s) {
        try {
            String t = s.trim();
            if (t.endsWith("Z")) t = t.substring(0, t.length() - 1);
            if (t.endsWith("+00:00")) t = t.substring(0, t.length() - 6);
            long ms = 0;
            int dot = t.indexOf('.');
            if (dot >= 0) {
                String frac = t.substring(dot + 1);
                t = t.substring(0, dot);
                while (frac.length() < 3) frac = frac + "0";
                ms = Long.parseLong(frac.substring(0, 3));
            }
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(t).getTime() + ms;
        } catch (Exception e) {
            return 0;
        }
    }
}
