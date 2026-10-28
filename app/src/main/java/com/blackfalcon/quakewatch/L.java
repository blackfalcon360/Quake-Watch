package com.blackfalcon.quakewatch;

import android.content.Context;

import java.util.Locale;

/** Language helper: every UI string is given in English, Urdu and Arabic. */
public class L {

    public static String t(Context c, String en, String ur, String ar) {
        String lang = Store.lang(c);
        if ("ur".equals(lang)) return ur;
        if ("ar".equals(lang)) return ar;
        return en;
    }

    public static boolean rtl(Context c) {
        String lang = Store.lang(c);
        return "ur".equals(lang) || "ar".equals(lang);
    }

    public static String dir(Context c, int i) {
        String[] en = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        String[] ur = {"شمال", "شمال مشرق", "مشرق", "جنوب مشرق", "جنوب", "جنوب مغرب", "مغرب", "شمال مغرب"};
        String[] ar = {"شمال", "شمال شرق", "شرق", "جنوب شرق", "جنوب", "جنوب غرب", "غرب", "شمال غرب"};
        String lang = Store.lang(c);
        String[] a = "ur".equals(lang) ? ur : ("ar".equals(lang) ? ar : en);
        return a[i & 7];
    }

    public static String ago(Context c, long diffMs) {
        long s = Math.max(0, diffMs / 1000);
        if (s < 60) return t(c, "just now", "ابھی", "الآن");
        long m = s / 60;
        if (m < 60) {
            return String.format(Locale.US, t(c, "%d min ago", "%d منٹ پہلے", "منذ %d دقيقة"), m);
        }
        long h = m / 60;
        if (h < 48) {
            return String.format(Locale.US, t(c, "%d h ago", "%d گھنٹے پہلے", "منذ %d ساعة"), h);
        }
        long d = h / 24;
        return String.format(Locale.US, t(c, "%d d ago", "%d دن پہلے", "منذ %d يوم"), d);
    }

    public static String magClass(Context c, int idx) {
        String[] en = {"Micro", "Minor", "Light", "Moderate", "Strong", "Major", "Great"};
        String[] ur = {"خورد", "معمولی", "ہلکا", "درمیانہ", "شدید", "بہت شدید", "انتہائی شدید"};
        String[] ar = {"طفيف جدًا", "صغير", "خفيف", "متوسط", "قوي", "كبير", "هائل"};
        String lang = Store.lang(c);
        String[] a = "ur".equals(lang) ? ur : ("ar".equals(lang) ? ar : en);
        return a[idx];
    }

    public static String depthClass(Context c, int idx) {
        String[] en = {"shallow", "intermediate", "deep"};
        String[] ur = {"کم گہرا", "درمیانی گہرائی", "گہرا"};
        String[] ar = {"ضحل", "متوسط العمق", "عميق"};
        String lang = Store.lang(c);
        String[] a = "ur".equals(lang) ? ur : ("ar".equals(lang) ? ar : en);
        return a[idx];
    }
}
