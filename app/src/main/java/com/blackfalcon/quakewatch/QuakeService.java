package com.blackfalcon.quakewatch;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public class QuakeService extends Service {

    private static final int NID = 1001;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Notifier.ensureChannels(this);
            Notification n = Notifier.serviceNotification(this);
            if (Build.VERSION.SDK_INT >= 34) {
                startFg34(n);
            } else {
                startForeground(NID, n);
            }
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Store.notif(this)) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Poller.poll(app);
            }
        }).start();
        Poller.schedule(app);
        return START_STICKY;
    }

    @TargetApi(34)
    private void startFg34(Notification n) {
        startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
