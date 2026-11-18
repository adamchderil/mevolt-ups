package com.mevolthome.upsmonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class UpsPowerMonitorService extends Service {
    static final String PREFERENCES = "upsmon_connection";
    static final String MONITOR_ENABLED_KEY = "background_monitor_enabled";
    static final String LAST_POWER_STATE_KEY = "last_utility_state";
    static final String ACTION_STOP_MONITOR =
            "com.mevolthome.upsmonitor.action.STOP_MONITOR";
    static final String CHANNEL_ID = "ups_power_monitor";
    static final String EVENT_CHANNEL_ID = "ups_power_events";
    private static final String TAG = "UpsPowerMonitor";
    private static final int SERVICE_NOTIFICATION_ID = 4101;
    private static final int EVENT_NOTIFICATION_ID = 4102;
    private static final long POLL_INTERVAL_SECONDS = 5;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> pollFuture;
    private ScheduledFuture<?> wakeLockRenewalFuture;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "MeVoltUPS:PowerMonitor");
        wakeLock.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_MONITOR.equals(intent.getAction())) {
            disableMonitoring();
            stopForeground(true);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (!isMonitoringEnabled()) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        startForeground(SERVICE_NOTIFICATION_ID,
                buildServiceNotification("Подключаемся к UPSMON PRO…"));
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(TimeUnit.MINUTES.toMillis(10));
        }
        if (executor == null) {
            executor = Executors.newSingleThreadScheduledExecutor();
            pollFuture = executor.scheduleWithFixedDelay(
                    this::pollTelemetry, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
            wakeLockRenewalFuture = executor.scheduleWithFixedDelay(
                    this::renewWakeLock, 5, 5, TimeUnit.MINUTES);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (pollFuture != null) {
            pollFuture.cancel(true);
            pollFuture = null;
        }
        if (wakeLockRenewalFuture != null) {
            wakeLockRenewalFuture.cancel(true);
            wakeLockRenewalFuture = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }

    private void renewWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(TimeUnit.MINUTES.toMillis(10));
        }
    }

    private void pollTelemetry() {
        EncryptedCredentialStore.Credentials credentials = null;
        try {
            credentials = EncryptedCredentialStore.load(this);
            if (credentials == null) {
                disableMonitoring();
                stopSelf();
                return;
            }

            SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
            String host = preferences.getString("host", UpsGatewayClient.DEFAULT_HOST);
            int port = preferences.getInt("port", 443);
            UpsTelemetry telemetry = UpsGatewayClient.fetchTelemetry(
                    host, port, credentials.username, credentials.password);
            processUtilityState(telemetry.utilityState);
            updateServiceNotification("Состояние ИБП отслеживается · опрос каждые 5 сек.");
        } catch (UpsGatewayClient.AuthenticationRequiredException exception) {
            Log.i(TAG, "Сервер отклонил сохранённую учётную запись.");
            clearCredentials();
            notifyUser("Требуется повторный вход",
                    "Откройте MeVolt UPS и войдите снова.");
            stopSelf();
        } catch (IOException exception) {
            Log.w(TAG, "Не удалось получить телеметрию: " + exception.getMessage());
            updateServiceNotification("Нет связи с ИБП · повторная попытка через 5 сек.");
        } catch (GeneralSecurityException exception) {
            Log.e(TAG, "Не удалось расшифровать сохранённые данные входа.", exception);
            clearCredentials();
            notifyUser("Нужен повторный вход",
                    "Защищённые данные входа недоступны. Откройте приложение.");
            stopSelf();
        } finally {
            if (credentials != null) {
                credentials.close();
            }
        }
    }

    private void processUtilityState(int utilityCode) {
        UtilityPowerState.State current = UtilityPowerState.fromUtilityCode(utilityCode);
        int currentCode = UtilityPowerState.persistedCode(current);
        SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        int previousCode = preferences.getInt(LAST_POWER_STATE_KEY, -1);
        UtilityPowerState.State previous = UtilityPowerState.fromUtilityCode(previousCode);
        UtilityPowerState.Transition transition =
                UtilityPowerState.transition(previous, current);
        preferences.edit().putInt(LAST_POWER_STATE_KEY, currentCode).apply();

        if (transition == UtilityPowerState.Transition.POWER_LOST) {
            notifyUser("Отключено сетевое питание",
                    "ИБП перешёл на питание от батареи.");
        } else if (transition == UtilityPowerState.Transition.POWER_RESTORED) {
            notifyUser("Сетевое питание восстановлено",
                    "ИБП снова получает питание от сети.");
        }
    }

    private boolean isMonitoringEnabled() {
        return getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getBoolean(MONITOR_ENABLED_KEY, false);
    }

    private void disableMonitoring() {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                .putBoolean(MONITOR_ENABLED_KEY, false)
                .putInt(LAST_POWER_STATE_KEY, -1)
                .apply();
    }

    private void clearCredentials() {
        disableMonitoring();
        try {
            EncryptedCredentialStore.clear(this);
        } catch (GeneralSecurityException exception) {
            Log.e(TAG, "Не удалось очистить защищённые данные входа.", exception);
        }
    }

    private void notifyUser(String title, String message) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder builder = createNotificationBuilder(EVENT_CHANNEL_ID);
        manager.notify(EVENT_NOTIFICATION_ID, builder
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setContentIntent(openAppPendingIntent())
                .build());
    }

    private void updateServiceNotification(String message) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(SERVICE_NOTIFICATION_ID, buildServiceNotification(message));
    }

    private Notification buildServiceNotification(String message) {
        return createNotificationBuilder(CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("MeVolt UPS · мониторинг в фоне")
                .setContentText(message)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openAppPendingIntent())
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause,
                        "Остановить",
                        stopMonitorPendingIntent()).build())
                .build();
    }

    private PendingIntent stopMonitorPendingIntent() {
        Intent intent = new Intent(this, UpsPowerMonitorService.class);
        intent.setAction(ACTION_STOP_MONITOR);
        return PendingIntent.getService(this, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification.Builder createNotificationBuilder(String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, channelId);
        }
        return new Notification.Builder(this);
    }

    private PendingIntent openAppPendingIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID, "Фоновый мониторинг ИБП", NotificationManager.IMPORTANCE_LOW);
        serviceChannel.setDescription("Показывает, что контроль питания ИБП активен.");
        manager.createNotificationChannel(serviceChannel);

        NotificationChannel eventChannel = new NotificationChannel(
                EVENT_CHANNEL_ID, "Состояние электросети",
                NotificationManager.IMPORTANCE_HIGH);
        eventChannel.setDescription("Уведомления об отключении и восстановлении сетевого питания.");
        manager.createNotificationChannel(eventChannel);
    }
}
