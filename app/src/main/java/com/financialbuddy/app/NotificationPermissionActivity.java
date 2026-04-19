package com.financialbuddy.app;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Transparent trampoline activity to open Notification Listener Settings.
 * Launched from JS bridge when user taps "Grant Access" in the web UI.
 */
public class NotificationPermissionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        finish();
    }
}
