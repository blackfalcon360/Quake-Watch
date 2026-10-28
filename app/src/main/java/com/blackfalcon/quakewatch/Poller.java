package com.blackfalcon.quakewatch;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.LinkedHashSet;
import java.util.List;

/** Checks the feed for new earthquakes, posts notifications, and schedules the next check. */
public class Poller {

    public static final String ACT = "com.blackfalcon.quakewatch.POLL";
    private static final Object LOCK = new Object();

    public static void poll(Context c) {
        synchronized (LOCK) {
            if (!Store.notif(c)) return;
            try {
                long now = System.currentTimeMillis();
                long last = Store.lastCheck(c);
                boolean first = (last == 0);
                String period = (first || now - last > 50L * 60000L) ? "day" : "hour";
                List<Quake> list = Feed.fetch(c, Store.source(c), "all", period, false).list;

                if (Store.useLoc(c) && LocHelper.hasPerm(c)) LocHelper.updateFromLast(c);
                boolean hasLoc = Store.useLoc(c) && Store.hasLoc(c);
                double lat = Store.lat(c);
                double lon = Store.lon(c);
                double radius = Store.radius(c);
                double minNear = Store.minNear(c);
                double worldMin = Store.worldMin(c);

                LinkedHashSet<String> seen = Store.seen(c);
                int sent = 0;
                for (Quake q : list) {
                    if (q.id == null || q.id.length() == 0) continue;
                    if (seen.contains(q.id)) continue;
                    if (now - q.time > 6L * 3600000L) continue;
                    double d = hasLoc ? Geo.km(lat, lon, q.lat, q.lon) : -1;
                    boolean near = hasLoc && d <= radius && q.mag >= minNear;
                    boolean world = worldMin > 0 && q.mag >= worldMin;
                    if (near || world) {
                        seen.add(q.id);
                        if (!first && sent < 5) {
                            Notifier.quake(c, q, d, near);
                            sent++;
                        }
                    }
                }
                Store.saveSeen(c, seen);
                Store.setLastCheck(c, now);
            } catch (Exception e) {
                // network problem: try again at the next scheduled check
            }
        }
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, PollReceiver.class);
        i.setAction(ACT);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void schedule(Context c) {
        if (!Store.notif(c)) return;
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            long at = System.currentTimeMillis() + Math.max(1, Store.interval(c)) * 60000L;
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c));
        } catch (Exception ignore) {
            // alarms not available
        }
    }

    public static void cancel(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pending(c));
        } catch (Exception ignore) {
            // nothing to cancel
        }
    }
}
