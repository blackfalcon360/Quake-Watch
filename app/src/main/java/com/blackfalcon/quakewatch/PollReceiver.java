package com.blackfalcon.quakewatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PollReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pr = goAsync();
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Poller.poll(app);
                } catch (Throwable ignore) {
                    // never crash in the background
                } finally {
                    try {
                        Poller.schedule(app);
                    } catch (Throwable ignore) {
                        // ignore
                    }
                    pr.finish();
                }
            }
        }).start();
    }
}
