package com.blackfalcon.quakewatch;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Notifier {

    public static final String CH_ALERT = "quake_alerts";
    public static final String CH_SVC = "quake_service";

    public static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT >= 26) channels26(c);
    }

    @TargetApi(26)
    private static void channels26(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel a = new NotificationChannel(CH_ALERT,
                L.t(c, "Earthquake alerts", "زلزلے کے الرٹس", "تنبيهات الزلازل"),
                NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(a);
        NotificationChannel s = new NotificationChannel(CH_SVC,
                L.t(c, "Background monitoring", "پس منظر میں نگرانی", "المراقبة في الخلفية"),
                NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(s);
    }

    @TargetApi(26)
    private static Notification.Builder builder26(Context c, String channel) {
        return new Notification.Builder(c, channel);
    }

    @SuppressWarnings("deprecation")
    private static Notification.Builder newBuilder(Context c, String channel) {
        if (Build.VERSION.SDK_INT >= 26) return builder26(c, channel);
        return new Notification.Builder(c);
    }

    private static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @SuppressWarnings("deprecation")
    public static Notification serviceNotification(Context c) {
        Notification.Builder b = newBuilder(c, CH_SVC);
        b.setSmallIcon(R.drawable.ic_stat_quake);
        b.setContentTitle(L.t(c, "Earthquake alerts are on", "زلزلے کے الرٹس فعال ہیں", "تنبيهات الزلازل مفعّلة"));
        b.setContentText(String.format(Locale.US,
                L.t(c, "Checking every %d min. Tap to open.",
                        "ہر %d منٹ بعد چیک ہوتا ہے۔ کھولنے کے لیے دبائیں۔",
                        "يتم الفحص كل %d دقيقة. اضغط للفتح."),
                Store.interval(c)));
        b.setOngoing(true);
        b.setOnlyAlertOnce(true);
        b.setContentIntent(openApp(c));
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_LOW);
        return b.build();
    }

    @SuppressWarnings("deprecation")
    public static void quake(Context c, Quake q, double distKm, boolean near) {
        ensureChannels(c);
        String mag = Geo.fmt1(q.mag);
        String title;
        if (near && distKm >= 0) {
            title = String.format(Locale.US,
                    L.t(c, "M%1$s earthquake · %2$s km away",
                            "M%1$s زلزلہ · %2$s کلومیٹر دور",
                            "زلزال M%1$s · على بُعد %2$s كم"),
                    mag, Geo.fmtDist(distKm));
        } else {
            title = String.format(Locale.US,
                    L.t(c, "M%s earthquake", "M%s کا زلزلہ", "زلزال بقوة M%s"), mag);
        }
        String when = new SimpleDateFormat("HH:mm", Locale.US).format(new Date(q.time));
        String body = q.place + "\n" + String.format(Locale.US,
                L.t(c, "Depth %s km", "گہرائی %s کلومیٹر", "العمق %s كم"), Geo.fmt1(q.depth))
                + " · " + when;
        Notification.Builder b = newBuilder(c, CH_ALERT);
        b.setSmallIcon(R.drawable.ic_stat_quake);
        b.setContentTitle(title);
        b.setContentText(q.place);
        b.setStyle(new Notification.BigTextStyle().bigText(body));
        b.setWhen(q.time);
        b.setShowWhen(true);
        b.setAutoCancel(true);
        b.setContentIntent(openApp(c));
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_HIGH);
            b.setDefaults(Notification.DEFAULT_ALL);
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            int id = (q.id.hashCode() & 0x7fffffff) % 100000 + 2000;
            nm.notify(id, b.build());
        }
    }

    public static void test(Context c) {
        Quake q = new Quake();
        q.id = "test-" + System.currentTimeMillis();
        q.mag = 4.6;
        q.depth = 10;
        q.time = System.currentTimeMillis();
        q.place = L.t(c, "Test notification", "ٹیسٹ نوٹیفیکیشن", "إشعار تجريبي");
        quake(c, q, 120, true);
    }
}
