package com.mevolthome.upsmonitor;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public final class MonitorBootReceiver extends BroadcastReceiver {
    private static final String TAG = "UpsMonitorBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        SharedPreferences preferences = context.getSharedPreferences(
                UpsPowerMonitorService.PREFERENCES, Context.MODE_PRIVATE);
        if (!preferences.getBoolean(
                UpsPowerMonitorService.MONITOR_ENABLED_KEY, false)
                || !EncryptedCredentialStore.hasSavedCredentials(context)
                || !notificationsAllowed(context)) {
            return;
        }

        Intent serviceIntent = new Intent(context, UpsPowerMonitorService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (IllegalStateException | SecurityException exception) {
            Log.e(TAG, "Android не разрешил запуск фонового мониторинга после перезагрузки.",
                    exception);
        }
    }

    private boolean notificationsAllowed(Context context) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            return manager.areNotificationsEnabled();
        }
        return true;
    }
}
