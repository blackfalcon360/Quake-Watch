package com.blackfalcon.quakewatch;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;

public class LocHelper {

    public static boolean hasPerm(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Reads the newest "last known" location from the system and saves it if it is newer. */
    public static boolean updateFromLast(Context c) {
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return false;
            Location best = null;
            String[] provs = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER};
            for (String p : provs) {
                try {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (Exception ignore) {
                    // provider missing or permission not granted for it
                }
            }
            if (best != null && (!Store.hasLoc(c) || best.getTime() > Store.locTime(c))) {
                Store.saveLoc(c, best.getLatitude(), best.getLongitude(), best.getTime());
                return true;
            }
        } catch (Exception ignore) {
            // no location available
        }
        return false;
    }

    /** Asks for one fresh fix. The callback runs on the main thread when a fix arrives. */
    public static void requestFresh(final Context c, final Runnable done) {
        final LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;
        final ArrayList<LocationListener> ls = new ArrayList<LocationListener>();
        final boolean[] fin = {false};
        String[] provs = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER};
        for (String p : provs) {
            try {
                if (!lm.isProviderEnabled(p)) continue;
                LocationListener l = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location loc) {
                        if (loc == null) return;
                        Store.saveLoc(c, loc.getLatitude(), loc.getLongitude(), System.currentTimeMillis());
                        if (!fin[0]) {
                            fin[0] = true;
                            for (LocationListener x : ls) {
                                try {
                                    lm.removeUpdates(x);
                                } catch (Exception ignore) {
                                    // already removed
                                }
                            }
                            if (done != null) done.run();
                        }
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {
                    }

                    @Override
                    public void onProviderEnabled(String provider) {
                    }

                    @Override
                    public void onProviderDisabled(String provider) {
                    }
                };
                ls.add(l);
                lm.requestSingleUpdate(p, l, Looper.getMainLooper());
            } catch (Exception ignore) {
                // provider not usable
            }
        }
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                for (LocationListener x : ls) {
                    try {
                        lm.removeUpdates(x);
                    } catch (Exception ignore) {
                        // already removed
                    }
                }
            }
        }, 25000);
    }
}
