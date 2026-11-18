package com.mevolthome.upsmonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;

public final class UpdateInstallReceiver extends BroadcastReceiver {
    private static final String TAG = "MeVoltUpdate";
    private static final String CHANNEL_ID = "mevolt_updates";
    private static final int NOTIFICATION_ID = 4201;

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation;
            if (Build.VERSION.SDK_INT >= 33) {
                confirmation = intent.getParcelableExtra(
                        Intent.EXTRA_INTENT, Intent.class);
            } else {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            }
            if (confirmation == null) {
                Log.e(TAG, "Установщик Android не передал экран подтверждения обновления.");
                return;
            }
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(confirmation);
            return;
        }
        if (status != PackageInstaller.STATUS_SUCCESS) {
            String detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Log.e(TAG, "Установка обновления завершилась ошибкой: " + detail);
            showFailureNotification(context);
        }
    }

    private void showFailureNotification(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Обновления MeVolt UPS", NotificationManager.IMPORTANCE_DEFAULT));
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        manager.notify(NOTIFICATION_ID, builder
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Не удалось установить обновление")
                .setContentText("Откройте приложение и повторите попытку.")
                .setAutoCancel(true)
                .build());
    }
}
