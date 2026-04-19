package com.financialbuddy.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * BootReceiver
 * Ensures the NotificationListenerService is alive after device reboot.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {
            Log.d("BootReceiver", "Device booted — notification listener will auto-reconnect");
            // NotificationListenerService reconnects automatically on boot
            // No explicit start needed — Android rebinds it if permission is granted
        }
    }
}
