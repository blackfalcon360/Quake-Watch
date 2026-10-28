package com.blackfalcon.quakewatch;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Store.notif(context)) return;
        // schedule first, so checks continue even if the service cannot start
        Poller.schedule(context);
        try {
            Intent s = new Intent(context, QuakeService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startFg(context, s);
            } else {
                context.startService(s);
            }
        } catch (Exception ignore) {
            // the alarm above still keeps checking
        }
    }

    @TargetApi(26)
    private void startFg(Context c, Intent s) {
        c.startForegroundService(s);
    }
}
