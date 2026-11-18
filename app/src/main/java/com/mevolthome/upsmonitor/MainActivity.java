package com.mevolthome.upsmonitor;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@SuppressLint("SetTextI18n")
public class MainActivity extends Activity {
    private static final String PREFERENCES = "upsmon_connection";
    private static final String HOST_KEY = "host";
    private static final String PORT_KEY = "port";
    private static final String DEFAULT_HOST = UpsGatewayClient.DEFAULT_HOST;
    private static final int DEFAULT_PORT = 443;
    private static final int POLL_INTERVAL_SECONDS = 2;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
    private static final int REQUEST_INSTALL_PERMISSION = 1002;
    private static final int NAVY = Color.rgb(19, 42, 69);
    private static final int BLUE = Color.rgb(36, 112, 190);
    private static final int TEAL = Color.rgb(25, 155, 151);
    private static final int GREEN = Color.rgb(38, 150, 103);
    private static final int AMBER = Color.rgb(194, 129, 25);
    private static final int BACKGROUND = Color.rgb(243, 246, 250);
    private static final int TEXT = Color.rgb(31, 45, 61);
    private static final int SECONDARY = Color.rgb(104, 119, 137);
    private static final int ELECTRIC_BACKGROUND = Color.rgb(7, 16, 28);
    private static final int ELECTRIC_SURFACE_TOP = Color.rgb(17, 34, 52);
    private static final int ELECTRIC_SURFACE_BOTTOM = Color.rgb(11, 24, 39);
    private static final int ELECTRIC_OUTLINE = Color.rgb(37, 68, 91);
    private static final int ELECTRIC_TEXT = Color.rgb(233, 248, 255);
    private static final int ELECTRIC_MUTED = Color.rgb(145, 169, 191);
    private static final int ELECTRIC_BLUE = Color.rgb(88, 187, 255);
    private static final int ELECTRIC_TEAL = Color.rgb(66, 225, 207);
    private static final int ELECTRIC_GREEN = Color.rgb(120, 235, 197);
    private static final int ELECTRIC_AMBER = Color.rgb(255, 194, 88);
    private static final int ELECTRIC_RED = Color.rgb(255, 130, 130);
    private static final int NAVIGATION_GROUP = 1;
    private static final int NAVIGATION_MONITOR = 1;
    private static final int NAVIGATION_HISTORY = 2;
    private static final int NAVIGATION_CONTROLS = 3;
    private static final int NAVIGATION_USERS = 4;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService networkExecutor =
            Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private final List<MetricCard> metricCards = new ArrayList<>();
    private final List<VoltageGaugeView> voltageGauges = new ArrayList<>();
    private final List<View> animatedIcons = new ArrayList<>();
    private final List<View> animatedReadings = new ArrayList<>();

    private FrameLayout appRoot;
    private FrameLayout contentFrame;
    private LinearLayout shellRoot;
    private TextView toolbarTitle;
    private TextView connectionStatus;
    private ImageButton homeMenuButton;
    private TextView historyStatus;
    private TextView historyContent;
    private TextView batteryValue;
    private TextView updateTime;
    private TextView zeroDataNote;
    private ProgressBar batteryProgress;
    private TextView avrStatus;
    private TextView utilityStatus;
    private TextView upsStatus;
    private EditText setupHostInput;
    private EditText setupPortInput;
    private EditText loginUsernameInput;
    private EditText loginPasswordInput;
    private TextView loginStatus;
    private Button loginButton;
    private ImageButton refreshButton;
    private ImageButton settingsButton;
    private View loginOverlay;
    private LightningOverlayView lightningOverlay;
    private ValueAnimator dashboardAnimator;
    private ValueAnimator batteryNumberAnimator;
    private ValueAnimator batteryProgressAnimator;
    private String currentUsername;
    private char[] activePassword;
    private File pendingUpdateFile;
    private double displayedBatteryPercent;

    private String connectedHost;
    private int connectedPort = DEFAULT_PORT;
    private UpsTelemetry latestTelemetry;
    private ScheduledFuture<?> pollFuture;
    private int pollGeneration;
    private boolean showingControls;
    private boolean showingHistory;
    private boolean showingUsers;
    private boolean activityResumed;
    private boolean isAuthenticated;
    private boolean isAdminUser;
    private boolean dashboardTheme;
    private boolean hasBatteryValue;
    private int historyGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.content.SharedPreferences preferences =
                getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        String savedHost = preferences.getString(HOST_KEY, DEFAULT_HOST);
        String normalizedHost = UpsServerAddress.normalizeHttpsHost(savedHost);
        if (normalizedHost == null) {
            connectedHost = DEFAULT_HOST;
            preferences.edit().putString(HOST_KEY, connectedHost).apply();
        } else {
            connectedHost = normalizedHost;
        }
        connectedPort = preferences.getInt(PORT_KEY, DEFAULT_PORT);
        if (connectedPort != DEFAULT_PORT) {
            connectedPort = DEFAULT_PORT;
            preferences.edit().putInt(PORT_KEY, connectedPort).apply();
        }

        buildShell();
        if (connectedHost == null || connectedHost.isEmpty()) {
            renderConnectionSetup();
        } else {
            renderMonitor();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        startPolling();
        if (isAuthenticated && getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getBoolean(UpsPowerMonitorService.MONITOR_ENABLED_KEY, false)) {
            startBackgroundMonitor();
        }
        startDashboardAnimations();
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        stopPolling();
        stopDashboardAnimations();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopPolling();
        stopDashboardAnimations();
        networkExecutor.shutdownNow();
        updateExecutor.shutdownNow();
        if (activePassword != null) {
            Arrays.fill(activePassword, '\0');
            activePassword = null;
        }
        super.onDestroy();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        shellRoot = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.setPadding(dp(16), dp(10), dp(16), dp(8));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);

        homeMenuButton = toolbarIconButton(
                R.drawable.ic_home_24, "Открыть разделы", BACKGROUND);
        LinearLayout.LayoutParams homeParams =
                new LinearLayout.LayoutParams(dp(42), dp(42));
        homeParams.rightMargin = dp(8);
        toolbar.addView(homeMenuButton, homeParams);
        homeMenuButton.setVisibility(View.GONE);
        homeMenuButton.setOnClickListener(view -> showNavigationMenu(view));

        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);
        titleGroup.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        toolbarTitle = text("Koval Electronics", 19, NAVY, true);
        toolbarTitle.setSingleLine(true);
        toolbarTitle.setEllipsize(TextUtils.TruncateAt.END);
        titleGroup.addView(toolbarTitle);
        toolbar.addView(titleGroup);

        refreshButton = toolbarIconButton(
                R.drawable.ic_refresh_24, "Обновить", BLUE);
        refreshButton.setOnClickListener(view -> {
            if (showingHistory) {
                fetchHistoryOnce();
            } else {
                fetchOnce();
            }
        });
        toolbar.addView(refreshButton);

        settingsButton = toolbarIconButton(
                R.drawable.ic_settings_24, "Настройки", NAVY);
        LinearLayout.LayoutParams settingsParams =
                new LinearLayout.LayoutParams(dp(42), dp(42));
        settingsParams.leftMargin = dp(6);
        settingsButton.setLayoutParams(settingsParams);
        settingsButton.setOnClickListener(view -> showSettingsDialog());
        toolbar.addView(settingsButton);
        root.addView(toolbar);

        connectionStatus = text("Ожидание подключения", 13, SECONDARY, false);
        connectionStatus.setPadding(dp(2), dp(8), dp(2), dp(8));
        root.addView(connectionStatus);

        contentFrame = new FrameLayout(this);
        root.addView(contentFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        appRoot = new FrameLayout(this);
        appRoot.addView(root, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout splash = createSplashScreen();
        appRoot.addView(splash, new FrameLayout.LayoutParams(-1, -1));
        setContentView(appRoot);
        mainHandler.postDelayed(() -> splash.animate()
                .alpha(0f)
                .setDuration(350)
                .withEndAction(() -> {
                    appRoot.removeView(splash);
                    continueAfterSplash();
                })
                .start(), 3000);
    }

    private void setDashboardTheme(boolean enabled) {
        if (dashboardTheme == enabled) {
            if (enabled && connectionStatus != null
                    && !animatedReadings.contains(connectionStatus)) {
                animatedReadings.add(connectionStatus);
            }
            return;
        }
        if (!enabled) {
            stopDashboardAnimations();
            animatedIcons.clear();
            animatedReadings.clear();
            voltageGauges.clear();
            lightningOverlay = null;
        }
        dashboardTheme = enabled;
        int backgroundColor = enabled ? ELECTRIC_BACKGROUND : BACKGROUND;
        if (shellRoot != null) {
            shellRoot.setBackgroundColor(backgroundColor);
        }
        if (contentFrame != null) {
            contentFrame.setBackgroundColor(backgroundColor);
        }
        if (toolbarTitle != null) {
            toolbarTitle.setTextColor(enabled ? ELECTRIC_TEXT : NAVY);
        }
        if (connectionStatus != null) {
            connectionStatus.setTextColor(enabled ? ELECTRIC_MUTED : SECONDARY);
            if (enabled && !animatedReadings.contains(connectionStatus)) {
                animatedReadings.add(connectionStatus);
            }
        }
        if (homeMenuButton != null) {
            homeMenuButton.setBackgroundTintList(ColorStateList.valueOf(
                    enabled ? ELECTRIC_SURFACE_TOP : BACKGROUND));
            homeMenuButton.setImageTintList(ColorStateList.valueOf(
                    enabled ? ELECTRIC_TEAL : NAVY));
        }
        if (refreshButton != null) {
            refreshButton.setBackgroundTintList(ColorStateList.valueOf(
                    enabled ? ELECTRIC_SURFACE_TOP : BACKGROUND));
            refreshButton.setImageTintList(ColorStateList.valueOf(
                    enabled ? ELECTRIC_BLUE : BLUE));
        }
        if (settingsButton != null) {
            settingsButton.setBackgroundTintList(ColorStateList.valueOf(
                    enabled ? ELECTRIC_SURFACE_TOP : BACKGROUND));
            settingsButton.setImageTintList(ColorStateList.valueOf(
                    enabled ? ELECTRIC_TEXT : NAVY));
        }
        getWindow().setStatusBarColor(enabled ? ELECTRIC_BACKGROUND : Color.WHITE);
        getWindow().setNavigationBarColor(enabled ? ELECTRIC_BACKGROUND : Color.WHITE);
        int systemUiVisibility = enabled ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (!enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            systemUiVisibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(systemUiVisibility);
    }

    private void startDashboardAnimations() {
        if (!activityResumed || !dashboardTheme || showingControls
                || showingHistory || showingUsers) {
            return;
        }
        if (dashboardAnimator == null) {
            dashboardAnimator = ValueAnimator.ofFloat(0f, 1f);
            dashboardAnimator.setDuration(3400);
            dashboardAnimator.setRepeatCount(ValueAnimator.INFINITE);
            dashboardAnimator.setInterpolator(new LinearInterpolator());
            dashboardAnimator.addUpdateListener(animator -> {
                float phase = (float) animator.getAnimatedValue();
                for (int index = 0; index < animatedIcons.size(); index++) {
                    View icon = animatedIcons.get(index);
                    double angle = (phase + index * 0.19f) * Math.PI * 2;
                    float wave = (float) ((Math.sin(angle) + 1d) * 0.5d);
                    icon.setTranslationY(-dp(2) * wave);
                    icon.setAlpha(0.9f + 0.1f * wave);
                }
                for (int index = 0; index < animatedReadings.size(); index++) {
                    View reading = animatedReadings.get(index);
                    double angle = (phase + index * 0.13f) * Math.PI * 2;
                    float wave = (float) ((Math.sin(angle) + 1d) * 0.5d);
                    float scale = 1f + 0.008f * wave;
                    reading.setScaleX(scale);
                    reading.setScaleY(scale);
                    reading.setAlpha(0.94f + 0.06f * wave);
                }
            });
        }
        if (!dashboardAnimator.isStarted()) {
            dashboardAnimator.start();
        }
        if (lightningOverlay != null) {
            lightningOverlay.startAnimation();
        }
        for (VoltageGaugeView gauge : voltageGauges) {
            gauge.startAnimation();
        }
    }

    private void stopDashboardAnimations() {
        if (dashboardAnimator != null) {
            dashboardAnimator.cancel();
            dashboardAnimator = null;
        }
        if (batteryNumberAnimator != null) {
            batteryNumberAnimator.cancel();
            batteryNumberAnimator = null;
        }
        if (batteryProgressAnimator != null) {
            batteryProgressAnimator.cancel();
            batteryProgressAnimator = null;
        }
        for (MetricCard metric : metricCards) {
            metric.cancelAnimations();
        }
        for (View icon : animatedIcons) {
            icon.setTranslationY(0f);
            icon.setAlpha(1f);
        }
        for (View reading : animatedReadings) {
            reading.setScaleX(1f);
            reading.setScaleY(1f);
            reading.setAlpha(1f);
        }
        if (lightningOverlay != null) {
            lightningOverlay.stopAnimation();
        }
        for (VoltageGaugeView gauge : voltageGauges) {
            gauge.stopAnimation();
        }
    }

    private TextView electricIcon(String symbol, int color, int sizeSp) {
        TextView icon = text(symbol, sizeSp, color, true);
        icon.setGravity(Gravity.CENTER);
        icon.setShadowLayer(dp(5), 0, 0,
                Color.argb(90, Color.red(color), Color.green(color), Color.blue(color)));
        animatedIcons.add(icon);
        return icon;
    }

    private GradientDrawable electricCardBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{ELECTRIC_SURFACE_TOP, ELECTRIC_SURFACE_BOTTOM});
        background.setCornerRadius(dp(17));
        background.setStroke(dp(1), ELECTRIC_OUTLINE);
        return background;
    }

    private GradientDrawable electricBatteryBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(16, 36, 58), Color.rgb(16, 60, 82),
                        Color.rgb(16, 45, 69)});
        background.setCornerRadius(dp(20));
        background.setStroke(dp(1), Color.argb(75, 88, 209, 255));
        return background;
    }

    private void animateBatteryValue(double target) {
        if (batteryNumberAnimator != null) {
            batteryNumberAnimator.cancel();
            batteryNumberAnimator = null;
        }
        if (!hasBatteryValue || displayedBatteryPercent == target) {
            batteryValue.setText(formatNumber(target) + "%");
            displayedBatteryPercent = target;
            hasBatteryValue = true;
            return;
        }
        batteryNumberAnimator = ValueAnimator.ofFloat(
                (float) displayedBatteryPercent, (float) target);
        batteryNumberAnimator.setDuration(650);
        batteryNumberAnimator.addUpdateListener(animator -> {
            displayedBatteryPercent = (float) animator.getAnimatedValue();
            batteryValue.setText(formatNumber(displayedBatteryPercent) + "%");
        });
        batteryNumberAnimator.start();
    }

    private void animateBatteryProgress(int target) {
        if (batteryProgressAnimator != null) {
            batteryProgressAnimator.cancel();
        }
        batteryProgressAnimator = ValueAnimator.ofInt(
                batteryProgress.getProgress(), target);
        batteryProgressAnimator.setDuration(650);
        batteryProgressAnimator.addUpdateListener(animator ->
                batteryProgress.setProgress((int) animator.getAnimatedValue()));
        batteryProgressAnimator.start();
    }

    private void setAnimatedText(TextView view, String value) {
        if (view == null || value.contentEquals(view.getText())) {
            return;
        }
        view.animate().cancel();
        view.setText(value);
        view.setAlpha(0.72f);
        view.animate().alpha(1f).setDuration(280).start();
    }

    private int connectionStatusColor(boolean connected) {
        if (dashboardTheme) {
            return connected ? ELECTRIC_GREEN : ELECTRIC_RED;
        }
        return connected ? GREEN : Color.rgb(184, 67, 67);
    }

    private void continueAfterSplash() {
        checkForUpdates();
        connectionStatus.setText("Восстанавливаем защищённый вход…");
        networkExecutor.execute(() -> {
            EncryptedCredentialStore.Credentials savedCredentials = null;
            String loadError = null;
            try {
                savedCredentials = EncryptedCredentialStore.load(this);
            } catch (GeneralSecurityException | IOException exception) {
                loadError = "Не удалось восстановить вход: "
                        + (exception.getMessage() == null
                        ? "защищённые данные недоступны."
                        : exception.getMessage());
                try {
                    EncryptedCredentialStore.clear(this);
                } catch (GeneralSecurityException clearException) {
                    loadError += " Данные хранилища не удалось очистить.";
                }
            }

            EncryptedCredentialStore.Credentials credentials = savedCredentials;
            String error = loadError;
            mainHandler.post(() -> {
                if (isFinishing()) {
                    if (credentials != null) {
                        credentials.close();
                    }
                    return;
                }
                if (credentials == null) {
                    showLoginScreen();
                    if (error != null) {
                        showMessage("Нужен повторный вход", error);
                    }
                    return;
                }

                currentUsername = credentials.username;
                activePassword = credentials.password.clone();
                credentials.close();
                isAuthenticated = true;
                isAdminUser = false;
                renderMonitor();
                connectionStatus.setText("Восстановлен вход · подключаемся к серверу…");
                startPolling();
                validateRestoredCredentials();
                if (getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                        .getBoolean(UpsPowerMonitorService.MONITOR_ENABLED_KEY, false)) {
                    startBackgroundMonitor();
                }
            });
        });
    }

    private void validateRestoredCredentials() {
        if (activePassword == null || currentUsername == null) {
            return;
        }
        String username = currentUsername;
        char[] password = activePassword.clone();
        networkExecutor.execute(() -> {
            UpsGatewayClient.AuthenticationResult result = null;
            String error = null;
            try {
                result = UpsGatewayClient.authenticate(
                        connectedHost, connectedPort, username, password);
            } catch (IOException exception) {
                error = exception.getMessage();
            } finally {
                Arrays.fill(password, '\0');
            }

            UpsGatewayClient.AuthenticationResult authenticationResult = result;
            String authenticationError = error;
            mainHandler.post(() -> {
                if (isFinishing() || !isAuthenticated) {
                    return;
                }
                if (authenticationResult == null) {
                    connectionStatus.setText("Нет связи с сервером авторизации"
                            + (authenticationError == null ? "." : " · " + authenticationError));
                    connectionStatus.setTextColor(connectionStatusColor(false));
                    return;
                }
                if (!authenticationResult.accepted) {
                    expireSession();
                    return;
                }
                isAdminUser = authenticationResult.admin;
            });
        });
    }

    private void startBackgroundMonitor() {
        if (!isAuthenticated) {
            return;
        }

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            android.content.SharedPreferences preferences =
                    getSharedPreferences(PREFERENCES, MODE_PRIVATE);
            boolean alreadyAsked = preferences.getBoolean(
                    "notification_permission_asked", false);
            if (!alreadyAsked) {
                preferences.edit()
                        .putBoolean("notification_permission_asked", true)
                        .apply();
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION);
            } else {
                stopService(new Intent(this, UpsPowerMonitorService.class));
                if (!preferences.getBoolean("notification_permission_warning_shown", false)) {
                    preferences.edit()
                            .putBoolean("notification_permission_warning_shown", true)
                            .apply();
                    showMessage("Уведомления отключены",
                            "Разрешите уведомления для MeVolt UPS в настройках Android, "
                                    + "чтобы получать оповещения о питании.");
                }
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NotificationManager manager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (!manager.areNotificationsEnabled()) {
                stopService(new Intent(this, UpsPowerMonitorService.class));
                android.content.SharedPreferences preferences =
                        getSharedPreferences(PREFERENCES, MODE_PRIVATE);
                if (!preferences.getBoolean("notification_settings_warning_shown", false)) {
                    preferences.edit()
                            .putBoolean("notification_settings_warning_shown", true)
                            .apply();
                    showMessage("Уведомления отключены",
                            "Включите уведомления MeVolt UPS в системных настройках.");
                }
                return;
            }
        }

        Intent serviceIntent = new Intent(this, UpsPowerMonitorService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (IllegalStateException exception) {
            showMessage("Не удалось запустить мониторинг",
                    "Android ограничил запуск фоновой службы. Откройте приложение и повторите.");
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATION_PERMISSION) {
            return;
        }
        if (grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                    .putBoolean("notification_permission_warning_shown", false)
                    .apply();
            startBackgroundMonitor();
        } else {
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                    .putBoolean(UpsPowerMonitorService.MONITOR_ENABLED_KEY, false)
                    .putBoolean("notification_permission_warning_shown", true)
                    .apply();
            showMessage("Уведомления не разрешены",
                    "Фоновый мониторинг не запущен. Разрешите уведомления в настройках Android.");
        }
    }

    private void expireSession() {
        if (!isAuthenticated) {
            return;
        }
        isAuthenticated = false;
        isAdminUser = false;
        currentUsername = null;
        stopPolling();
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                .putBoolean(UpsPowerMonitorService.MONITOR_ENABLED_KEY, false)
                .putInt(UpsPowerMonitorService.LAST_POWER_STATE_KEY, -1)
                .apply();
        stopService(new Intent(this, UpsPowerMonitorService.class));
        if (activePassword != null) {
            Arrays.fill(activePassword, '\0');
            activePassword = null;
        }
        try {
            EncryptedCredentialStore.clear(this);
        } catch (GeneralSecurityException exception) {
            Toast.makeText(this, "Не удалось удалить сохранённые данные входа.",
                    Toast.LENGTH_LONG).show();
        }
        showLoginScreen();
        if (loginStatus != null) {
            loginStatus.setText("Сессия истекла. Войдите снова.");
        }
    }

    private void checkForUpdates() {
        updateExecutor.execute(() -> {
            GithubReleaseClient.UpdateInfo update;
            try {
                String currentVersion = getPackageManager()
                        .getPackageInfo(getPackageName(), 0).versionName;
                update = GithubReleaseClient.checkForUpdate(currentVersion);
            } catch (IOException | JSONException
                     | android.content.pm.PackageManager.NameNotFoundException exception) {
                String message = exception.getMessage() == null
                        ? "Проверьте подключение к интернету."
                        : exception.getMessage();
                mainHandler.post(() -> {
                    if (!isFinishing()) {
                        Toast.makeText(this, "Не удалось проверить обновления: " + message,
                                Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }

            if (update != null) {
                mainHandler.post(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("Доступно обновление " + update.version)
                            .setMessage("Скачать новую версию из GitHub? "
                                    + "Перед установкой Android попросит подтверждение.")
                            .setPositiveButton("Скачать и установить",
                                    (dialog, which) -> downloadAndInstall(update))
                            .setNegativeButton("Позже", null)
                            .show();
                });
            }
        });
    }

    private void downloadAndInstall(GithubReleaseClient.UpdateInfo update) {
        ProgressBar progress = new ProgressBar(this);
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("Загрузка обновления")
                .setMessage("Скачиваем APK из GitHub…")
                .setView(progress)
                .setCancelable(false)
                .create();
        progressDialog.show();

        updateExecutor.execute(() -> {
            File apk = null;
            String error = null;
            try {
                apk = GithubReleaseClient.download(this, update);
            } catch (IOException exception) {
                error = exception.getMessage() == null
                        ? "Не удалось загрузить APK."
                        : exception.getMessage();
            }

            File downloadedApk = apk;
            String downloadError = error;
            mainHandler.post(() -> {
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                if (isFinishing()) {
                    if (downloadedApk != null) {
                        downloadedApk.delete();
                    }
                    return;
                }
                if (downloadError != null) {
                    showMessage("Ошибка загрузки", downloadError);
                    return;
                }
                installUpdate(downloadedApk);
            });
        });
    }

    private void installUpdate(File apk) {
        if (apk == null || !apk.isFile()) {
            showMessage("Файл обновления не найден",
                    "Повторите проверку обновлений при следующем запуске.");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            pendingUpdateFile = apk;
            Intent settingsIntent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(settingsIntent, REQUEST_INSTALL_PERMISSION);
            return;
        }

        PackageInstaller installer = getPackageManager().getPackageInstaller();
        int sessionId = -1;
        PackageInstaller.Session session = null;
        try {
            PackageInstaller.SessionParams parameters =
                    new PackageInstaller.SessionParams(
                            PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            if (Build.VERSION.SDK_INT >= 29) {
                parameters.setAppPackageName(getPackageName());
            }
            sessionId = installer.createSession(parameters);
            session = installer.openSession(sessionId);
            try (FileInputStream input = new FileInputStream(apk);
                 OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                session.fsync(output);
            }

            Intent statusIntent = new Intent(this, UpdateInstallReceiver.class);
            int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            pendingIntentFlags |= Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_IMMUTABLE;
            PendingIntent statusPendingIntent = PendingIntent.getBroadcast(
                    this, sessionId, statusIntent, pendingIntentFlags);
            session.commit(statusPendingIntent.getIntentSender());
            session.close();
            session = null;
            if (!apk.delete() && apk.exists()) {
                Toast.makeText(this,
                        "APK передан установщику; временный файл не удалось удалить.",
                        Toast.LENGTH_LONG).show();
            }
        } catch (IOException | SecurityException exception) {
            if (session != null) {
                session.close();
            }
            if (sessionId >= 0) {
                installer.abandonSession(sessionId);
            }
            showMessage("Не удалось начать установку",
                    exception.getMessage() == null
                            ? "Проверьте разрешение на установку приложений из этого источника."
                            : exception.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_INSTALL_PERMISSION) {
            return;
        }

        File apk = pendingUpdateFile;
        pendingUpdateFile = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && getPackageManager().canRequestPackageInstalls()) {
            installUpdate(apk);
        } else {
            if (apk != null) {
                apk.delete();
            }
            showMessage("Установка не разрешена",
                    "Разрешите установку приложений из MeVolt UPS в настройках Android.");
        }
    }

    private FrameLayout createSplashScreen() {
        FrameLayout splash = new FrameLayout(this);
        splash.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{NAVY, Color.rgb(10, 91, 112)}));

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER);
        brand.setOrientation(LinearLayout.VERTICAL);

        TextView title = text("Koval Electronics", 31, Color.WHITE, true);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        brand.addView(title);

        View accent = new View(this);
        accent.setBackground(roundRect(TEAL, 4, TEAL, 0));
        LinearLayout.LayoutParams accentParams =
                new LinearLayout.LayoutParams(dp(54), dp(4));
        accentParams.topMargin = dp(15);
        accentParams.gravity = Gravity.CENTER_HORIZONTAL;
        brand.addView(accent, accentParams);

        TextView subtitle = text("POWER MONITORING", 11,
                Color.rgb(195, 220, 232), true);
        subtitle.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(-2, -2);
        subtitleParams.topMargin = dp(14);
        brand.addView(subtitle, subtitleParams);

        splash.addView(brand, new FrameLayout.LayoutParams(-1, -1));
        return splash;
    }

    private void showLoginScreen() {
        if (loginOverlay != null && loginOverlay.getParent() != null) {
            return;
        }
        homeMenuButton.setVisibility(View.GONE);
        FrameLayout overlay = new FrameLayout(this);
        loginOverlay = overlay;
        overlay.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{NAVY, Color.rgb(10, 91, 112)}));

        ScrollView scroll = new ScrollView(this);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setGravity(Gravity.CENTER);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView brand = text("Koval Electronics", 25, Color.WHITE, true);
        brand.setGravity(Gravity.CENTER);
        wrapper.addView(brand);

        LinearLayout form = card(Color.WHITE, 22);
        LinearLayout.LayoutParams formParams =
                new LinearLayout.LayoutParams(-1, -2);
        formParams.topMargin = dp(26);
        form.addView(text("Вход в приложение", 20, TEXT, true));
        addTopMargin(form, text("Войдите под своей учётной записью",
                13, SECONDARY, false), 5);

        loginUsernameInput = new EditText(this);
        loginUsernameInput.setSingleLine(true);
        loginUsernameInput.setHint("Логин");
        loginUsernameInput.setText("admin");
        loginUsernameInput.setTextColor(TEXT);
        loginUsernameInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        loginUsernameInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        addTopMargin(form, loginUsernameInput, 14);

        loginPasswordInput = new EditText(this);
        loginPasswordInput.setSingleLine(true);
        loginPasswordInput.setHint("Пароль");
        loginPasswordInput.setTextColor(TEXT);
        loginPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        loginPasswordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        addTopMargin(form, loginPasswordInput, 4);

        loginStatus = text("", 12, Color.rgb(184, 67, 67), false);
        addTopMargin(form, loginStatus, 6);

        loginButton = button("Войти", BLUE);
        loginButton.setOnClickListener(view -> authenticate());
        addTopMargin(form, loginButton, 8);
        wrapper.addView(form, formParams);

        TextView privacy = text("Адрес сервера сохраняется на этом устройстве.",
                12, Color.rgb(195, 220, 232), false);
        privacy.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams privacyParams =
                new LinearLayout.LayoutParams(-1, -2);
        privacyParams.topMargin = dp(16);
        wrapper.addView(privacy, privacyParams);

        scroll.addView(wrapper);
        overlay.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        appRoot.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(250).start();
        loginPasswordInput.requestFocus();
    }

    private void authenticate() {
        String username = loginUsernameInput.getText().toString().trim()
                .toLowerCase(Locale.ROOT);
        char[] password = loginPasswordInput.getText().toString().toCharArray();
        loginButton.setEnabled(false);
        loginStatus.setText("Проверка данных…");
        networkExecutor.execute(() -> {
            boolean accepted = false;
            boolean adminAccount = false;
            String errorMessage = null;
            String credentialSaveWarning = null;
            char[] sessionPassword = null;
            try {
                if (!username.matches("[A-Za-z0-9._-]{3,32}") || password.length == 0) {
                    errorMessage = "Введите корректные логин и пароль.";
                } else {
                    HttpURLConnection connection = null;
                    try {
                        URL url = gatewayUrl("auth");
                        connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("GET");
                        connection.setConnectTimeout(8000);
                        connection.setReadTimeout(8000);
                        connection.setUseCaches(false);
                        setAuthorization(connection, username, password);
                        int responseCode = connection.getResponseCode();
                        accepted = responseCode == HttpURLConnection.HTTP_NO_CONTENT;
                        if (accepted) {
                            adminAccount = "admin".equalsIgnoreCase(
                                    connection.getHeaderField("X-MeVolt-Role"));
                            sessionPassword = password.clone();
                            try {
                                EncryptedCredentialStore.save(this, username, password);
                            } catch (GeneralSecurityException | IOException saveException) {
                                credentialSaveWarning = saveException.getMessage() == null
                                        ? "Не удалось сохранить вход на устройстве."
                                        : saveException.getMessage();
                            }
                        } else if (responseCode != HttpURLConnection.HTTP_UNAUTHORIZED) {
                            errorMessage = "Сервер авторизации вернул HTTP " + responseCode + ".";
                        }
                    } finally {
                        if (connection != null) {
                            connection.disconnect();
                        }
                    }
                }
            } catch (IOException exception) {
                errorMessage = exception.getMessage() == null
                        ? "Не удалось связаться с сервером авторизации."
                        : "Нет связи с сервером авторизации: " + exception.getMessage();
            } finally {
                Arrays.fill(password, '\0');
            }

            boolean loginAccepted = accepted;
            boolean isAdmin = adminAccount;
            String loginError = errorMessage;
            String saveWarning = credentialSaveWarning;
            char[] authenticatedPassword = sessionPassword;
            mainHandler.post(() -> {
                if (isFinishing()) {
                    if (authenticatedPassword != null) {
                        Arrays.fill(authenticatedPassword, '\0');
                    }
                    return;
                }
                if (!loginAccepted) {
                    loginStatus.setText(loginError == null
                            ? "Неверный логин или пароль."
                            : loginError);
                    loginPasswordInput.setText("");
                    loginButton.setEnabled(true);
                    return;
                }

                isAuthenticated = true;
                isAdminUser = isAdmin;
                currentUsername = username;
                activePassword = authenticatedPassword;
                homeMenuButton.setVisibility(View.VISIBLE);
                getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                        .putBoolean(UpsPowerMonitorService.MONITOR_ENABLED_KEY, true)
                        .putInt(UpsPowerMonitorService.LAST_POWER_STATE_KEY, -1)
                        .apply();
                loginStatus.setText("");
                loginPasswordInput.setText("");
                View currentLoginOverlay = loginOverlay;
                if (saveWarning != null) {
                    Toast.makeText(this,
                            "Вход выполнен, но автоматический вход не сохранён: " + saveWarning,
                            Toast.LENGTH_LONG).show();
                }
                currentLoginOverlay.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction(() -> {
                            appRoot.removeView(currentLoginOverlay);
                            loginOverlay = null;
                            if (connectedHost == null || connectedHost.isEmpty()) {
                                renderConnectionSetup();
                            }
                            startPolling();
                            startBackgroundMonitor();
                        })
                        .start();
            });
        });
    }

    private void renderConnectionSetup() {
        setDashboardTheme(false);
        showingHistory = false;
        showingControls = false;
        showingUsers = false;
        contentFrame.removeAllViews();
        homeMenuButton.setVisibility(View.GONE);
        connectionStatus.setText("Введите домен защищённого сервера UPSMON.");

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = pageBody();
        LinearLayout card = card(BACKGROUND, 24);
        card.setBackground(roundRect(Color.WHITE, 20, Color.rgb(225, 232, 240), 1));
        card.addView(text("Подключение", 20, TEXT, true));

        TextView help = text(
                "Подключение работает через интернет без VPN-приложения на телефоне. Компьютер с UPSMON PRO должен быть включён и подключён к интернету.",
                14, SECONDARY, false);
        addTopMargin(card, help, 8);

        setupHostInput = new EditText(this);
        setupHostInput.setSingleLine(true);
        setupHostInput.setHint("Домен HTTPS-сервера");
        setupHostInput.setInputType(InputType.TYPE_CLASS_TEXT);
        setupHostInput.setText(connectedHost == null ? DEFAULT_HOST : connectedHost);
        setupHostInput.setTextColor(TEXT);
        setupHostInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        addTopMargin(card, setupHostInput, 14);

        setupPortInput = new EditText(this);
        setupPortInput.setSingleLine(true);
        setupPortInput.setHint("HTTPS-порт");
        setupPortInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        setupPortInput.setText(String.valueOf(DEFAULT_PORT));
        setupPortInput.setTextColor(TEXT);
        setupPortInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        addTopMargin(card, setupPortInput, 4);

        Button connectButton = button("Подключиться", BLUE);
        connectButton.setOnClickListener(view -> connectFromSetup());
        addTopMargin(card, connectButton, 12);

        body.addView(card);
        TextView privacy = text(
                "Адрес сервера сохраняется на телефоне. Данные и вход передаются по HTTPS.",
                12, SECONDARY, false);
        addTopMargin(body, privacy, 12);
        scroll.addView(body);
        contentFrame.addView(scroll);
    }

    private void prepareThemedPage() {
        stopDashboardAnimations();
        animatedIcons.clear();
        animatedReadings.clear();
        voltageGauges.clear();
        lightningOverlay = null;
        setDashboardTheme(true);
        homeMenuButton.setVisibility(isAuthenticated ? View.VISIBLE : View.GONE);
    }

    private void renderMonitor() {
        prepareThemedPage();
        showingControls = false;
        showingHistory = false;
        showingUsers = false;
        contentFrame.removeAllViews();
        homeMenuButton.setVisibility(isAuthenticated ? View.VISIBLE : View.GONE);
        metricCards.clear();
        hasBatteryValue = false;

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = pageBody();

        LinearLayout batteryCard = card(ELECTRIC_SURFACE_TOP, 20);
        batteryCard.setBackground(electricBatteryBackground());
        LinearLayout batteryHeader = new LinearLayout(this);
        batteryHeader.setGravity(Gravity.CENTER_VERTICAL);
        batteryHeader.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout batteryTitles = new LinearLayout(this);
        batteryTitles.setOrientation(LinearLayout.VERTICAL);
        batteryTitles.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout batteryLabel = new LinearLayout(this);
        batteryLabel.setGravity(Gravity.CENTER_VERTICAL);
        batteryLabel.setOrientation(LinearLayout.HORIZONTAL);
        batteryLabel.addView(electricIcon("ϟ", ELECTRIC_TEAL, 15),
                new LinearLayout.LayoutParams(dp(20), dp(20)));
        batteryLabel.addView(text("Заряд батареи", 15, ELECTRIC_TEXT, true));
        batteryTitles.addView(batteryLabel);
        addTopMargin(batteryTitles,
                text("Данные UPSMON PRO", 12, ELECTRIC_MUTED, false), 2);
        batteryHeader.addView(batteryTitles);

        batteryValue = text("--%", 34, ELECTRIC_TEXT, true);
        batteryValue.setShadowLayer(dp(8), 0, 0, Color.argb(75, 88, 187, 255));
        animatedReadings.add(batteryValue);
        batteryHeader.addView(batteryValue);
        batteryCard.addView(batteryHeader);

        batteryProgress = progressBar(ELECTRIC_TEAL);
        addTopMargin(batteryCard, batteryProgress, 10);
        updateTime = text("Ожидание первых показаний…", 12, ELECTRIC_MUTED, false);
        addTopMargin(batteryCard, updateTime, 6);
        body.addView(batteryCard);

        TextView metricsTitle = text("Показатели", 17, ELECTRIC_TEXT, true);
        addTopMargin(body, metricsTitle, 18);

        addVoltageMetricRow(body,
                new MetricSpec("Входное напряжение", "В", ELECTRIC_BLUE, false, "ϟ"),
                new MetricSpec("Выходное напряжение", "В", ELECTRIC_TEAL, false, "ϟ"));
        addMetricRow(body,
                new MetricSpec("Нагрузка", "%", ELECTRIC_AMBER, true, "ϟ"),
                new MetricSpec("Частота входа", "Гц", ELECTRIC_BLUE, false, "⌁"));
        addMetricRow(body,
                new MetricSpec("Частота выхода", "Гц", ELECTRIC_TEAL, false, "⌁"),
                new MetricSpec("Уровень батареи", "%", ELECTRIC_GREEN, true, "ϟ"));

        zeroDataNote = text("", 12, ELECTRIC_AMBER, false);
        zeroDataNote.setVisibility(View.GONE);
        addTopMargin(body, zeroDataNote, 8);

        TextView stateTitle = text("Состояния", 17, ELECTRIC_TEXT, true);
        addTopMargin(body, stateTitle, 18);
        LinearLayout stateRow = new LinearLayout(this);
        stateRow.setOrientation(LinearLayout.HORIZONTAL);
        stateRow.addView(stateCard("Режим AVR", "—", "⌁"), weightedParams(1, 1));
        stateRow.addView(stateCard("Источник питания", "—", "ϟ"), weightedParams(1, 1));
        stateRow.addView(stateCard("ИБП", "—", "◈"), weightedParams(1, 1));
        avrStatus = (TextView) ((LinearLayout) stateRow.getChildAt(0)).getChildAt(1);
        utilityStatus = (TextView) ((LinearLayout) stateRow.getChildAt(1))
                .getChildAt(1);
        upsStatus = (TextView) ((LinearLayout) stateRow.getChildAt(2)).getChildAt(1);
        body.addView(stateRow);

        TextView info = text(
                "Сервер передаёт заряд батареи, но не отдельный флаг её исправности. Неизвестные коды состояния отображаются без догадок.",
                11, ELECTRIC_MUTED, false);
        addTopMargin(body, info, 8);

        lightningOverlay = new LightningOverlayView(this);
        contentFrame.addView(lightningOverlay,
                new FrameLayout.LayoutParams(-1, -1));
        scroll.addView(body);
        contentFrame.addView(scroll);
        if (latestTelemetry != null) {
            renderTelemetry(latestTelemetry, new Date());
        }
        startDashboardAnimations();
    }

    private void renderControls() {
        prepareThemedPage();
        showingControls = true;
        showingHistory = false;
        showingUsers = false;
        contentFrame.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = pageBody();

        LinearLayout notice = card(ELECTRIC_SURFACE_TOP, 18);
        notice.setBackground(electricCardBackground());
        notice.addView(text("Команды пока недоступны", 17, ELECTRIC_TEXT, true));
        addTopMargin(notice, text(
                "Встроенный веб-сервер UPSMON PRO предоставляет только показания. В найденной странице есть опрос GET /ups.txt, но нет команд для управления ИБП.",
                13, ELECTRIC_MUTED, false), 6);
        body.addView(notice);

        TextView actionsTitle = text("Функции UPSMON PRO", 17, ELECTRIC_TEXT, true);
        addTopMargin(body, actionsTitle, 18);
        body.addView(controlCard("Тест батареи", "10-секундный тест", "Нет API-команды"));
        addTopMargin(body, controlCard("Звуковой сигнал", "Вкл. / выкл.", "Нет API-команды"), 10);
        addTopMargin(body, controlCard("Green Mode", "Режим энергосбережения", "Нет API-команды"), 10);
        addTopMargin(body, controlCard("Ручной bypass", "Если поддерживается моделью", "Нет API-команды"), 10);

        Button details = button("Что нужно для активации?", ELECTRIC_BLUE);
        details.setOnClickListener(view -> showControlRequirements());
        addTopMargin(body, details, 16);

        TextView liveInfo = text(
                "Мониторинг продолжает обновляться, пока открыта вкладка управления.",
                12, ELECTRIC_MUTED, false);
        addTopMargin(body, liveInfo, 12);

        scroll.addView(body);
        contentFrame.addView(scroll);
    }

    private void renderHistory() {
        prepareThemedPage();
        showingControls = false;
        showingHistory = true;
        showingUsers = false;
        contentFrame.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = pageBody();
        LinearLayout card = card(ELECTRIC_SURFACE_TOP, 20);
        card.setBackground(electricCardBackground());
        card.addView(text("История событий UPSMON PRO", 18, ELECTRIC_TEXT, true));

        historyStatus = text("Загрузка журнала…", 13, ELECTRIC_MUTED, false);
        addTopMargin(card, historyStatus, 8);

        historyContent = text("", 14, ELECTRIC_TEXT, false);
        historyContent.setLineSpacing(dp(3), 1f);
        addTopMargin(card, historyContent, 12);
        body.addView(card);

        TextView help = text(
                "Журнал читается напрямую из EventRecord.CSV. На компьютере должен работать read-only сервер из README.",
                12, ELECTRIC_MUTED, false);
        addTopMargin(body, help, 10);

        scroll.addView(body);
        contentFrame.addView(scroll);
        fetchHistoryOnce();
    }

    private void renderUsers() {
        if (!isAdminUser) {
            renderMonitor();
            return;
        }

        prepareThemedPage();
        showingControls = false;
        showingHistory = false;
        showingUsers = true;
        contentFrame.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = pageBody();
        LinearLayout card = card(ELECTRIC_SURFACE_TOP, 20);
        card.setBackground(electricCardBackground());
        card.addView(text("Пользователи приложения", 18, ELECTRIC_TEXT, true));
        addTopMargin(card, text("Вход в мониторинг и историю UPSMON PRO",
                13, ELECTRIC_MUTED, false), 4);
        addTopMargin(card, text("Вы вошли как " + currentUsername,
                12, ELECTRIC_BLUE, true), 8);

        LinearLayout accounts = new LinearLayout(this);
        accounts.setOrientation(LinearLayout.VERTICAL);
        addTopMargin(card, accounts, 10);
        refreshUserList(accounts);
        body.addView(card);

        Button createUser = button("Создать пользователя", ELECTRIC_BLUE);
        createUser.setOnClickListener(view -> showCreateUserDialog());
        addTopMargin(body, createUser, 14);

        TextView note = text(
                "Только администратор может создавать учётные записи. Сервер хранит пароли только в виде индивидуальных хешей.",
                12, ELECTRIC_MUTED, false);
        addTopMargin(body, note, 10);

        scroll.addView(body);
        contentFrame.addView(scroll);
    }

    private void refreshUserList(LinearLayout accounts) {
        accounts.removeAllViews();
        accounts.addView(accountRow("Загрузка списка пользователей…"));
        networkExecutor.execute(() -> {
            HttpURLConnection connection = null;
            List<String> usernames = new ArrayList<>();
            String error = null;
            try {
                connection = (HttpURLConnection) gatewayUrl("users").openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setUseCaches(false);
                setAuthorization(connection, currentUsername, activePassword);
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Сервер вернул HTTP " + responseCode + ".");
                }
                JSONObject response = new JSONObject(
                        readBoundedBody(connection.getInputStream(), 16 * 1024));
                JSONArray users = response.getJSONArray("users");
                for (int index = 0; index < users.length(); index++) {
                    usernames.add(users.getString(index));
                }
            } catch (IOException | JSONException exception) {
                error = exception.getMessage() == null
                        ? "Не удалось загрузить список пользователей."
                        : exception.getMessage();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            List<String> loadedUsernames = usernames;
            String loadError = error;
            mainHandler.post(() -> {
                if (isFinishing() || !showingUsers || accounts.getParent() == null) {
                    return;
                }
                accounts.removeAllViews();
                if (loadError != null) {
                    accounts.addView(accountRow("Не удалось загрузить список: " + loadError));
                    return;
                }
                for (String username : loadedUsernames) {
                    addTopMargin(accounts, accountRow(username
                            + ("admin".equals(username) ? " · администратор" : "")), 8);
                }
            });
        });
    }

    private LinearLayout accountRow(String username) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(roundRect(ELECTRIC_SURFACE_BOTTOM, 12, ELECTRIC_OUTLINE, 1));

        TextView accountName = text(username, 14, ELECTRIC_TEXT, true);
        row.addView(accountName);
        return row;
    }

    private void showCreateUserDialog() {
        if (!isAdminUser) {
            showMessage("Нет доступа", "Создавать пользователей может только администратор.");
            return;
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(4));

        EditText usernameInput = new EditText(this);
        usernameInput.setSingleLine(true);
        usernameInput.setHint("Логин (3–32 символа)");
        usernameInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        form.addView(usernameInput);

        EditText passwordInput = new EditText(this);
        passwordInput.setSingleLine(true);
        passwordInput.setHint("Пароль (не менее 8 символов)");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addTopMargin(form, passwordInput, 4);

        EditText confirmInput = new EditText(this);
        confirmInput.setSingleLine(true);
        confirmInput.setHint("Повторите пароль");
        confirmInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addTopMargin(form, confirmInput, 4);

        TextView status = text("", 12, Color.rgb(184, 67, 67), false);
        addTopMargin(form, status, 5);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Новый пользователь")
                .setView(form)
                .setPositiveButton("Создать", null)
                .setNegativeButton("Отмена", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String normalizedUsername = usernameInput.getText().toString().trim()
                            .toLowerCase(Locale.ROOT);
                    if (!normalizedUsername.matches("[a-z0-9._-]{3,32}")) {
                        usernameInput.setError("Используйте 3–32 латинских буквы, цифры, точку, дефис или _.");
                        return;
                    }
                    if ("admin".equals(normalizedUsername)) {
                        usernameInput.setError("Логин admin зарезервирован.");
                        return;
                    }

                    char[] password = passwordInput.getText().toString().toCharArray();
                    char[] confirmation = confirmInput.getText().toString().toCharArray();
                    if (password.length < 8) {
                        passwordInput.setError("Минимум 8 символов.");
                        java.util.Arrays.fill(password, '\0');
                        java.util.Arrays.fill(confirmation, '\0');
                        return;
                    }
                    if (!java.util.Arrays.equals(password, confirmation)) {
                        confirmInput.setError("Пароли не совпадают.");
                        java.util.Arrays.fill(password, '\0');
                        java.util.Arrays.fill(confirmation, '\0');
                        return;
                    }
                    java.util.Arrays.fill(confirmation, '\0');

                    Button createButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    createButton.setEnabled(false);
                    status.setTextColor(SECONDARY);
                    status.setText("Создание пользователя…");
                    networkExecutor.execute(() -> {
                        boolean saved = false;
                        String error = null;
                        HttpURLConnection connection = null;
                        try {
                            connection = (HttpURLConnection) gatewayUrl("users").openConnection();
                            connection.setRequestMethod("POST");
                            connection.setConnectTimeout(8000);
                            connection.setReadTimeout(8000);
                            connection.setUseCaches(false);
                            connection.setDoOutput(true);
                            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                            setAuthorization(connection, currentUsername, activePassword);
                            byte[] requestBody;
                            try {
                                JSONObject request = new JSONObject();
                                request.put("username", normalizedUsername);
                                request.put("password", new String(password));
                                requestBody = request.toString().getBytes(StandardCharsets.UTF_8);
                            } catch (JSONException exception) {
                                throw new IOException("Не удалось подготовить запрос создания пользователя.",
                                        exception);
                            }
                            connection.setFixedLengthStreamingMode(requestBody.length);
                            try (OutputStream output = connection.getOutputStream()) {
                                output.write(requestBody);
                            } finally {
                                Arrays.fill(requestBody, (byte) 0);
                            }
                            int responseCode = connection.getResponseCode();
                            saved = responseCode == HttpURLConnection.HTTP_CREATED;
                            if (!saved) {
                                error = responseCode == HttpURLConnection.HTTP_CONFLICT
                                        ? "Пользователь с таким логином уже существует."
                                        : "Сервер не создал пользователя (HTTP "
                                        + responseCode + ").";
                            }
                        } catch (IOException exception) {
                            error = exception.getMessage() == null
                                    ? "Не удалось связаться с сервером пользователей."
                                    : exception.getMessage();
                        } finally {
                            Arrays.fill(password, '\0');
                            if (connection != null) {
                                connection.disconnect();
                            }
                        }

                        boolean result = saved;
                        String resultError = error;
                        mainHandler.post(() -> {
                            if (isFinishing() || !dialog.isShowing()) {
                                return;
                            }
                            if (!result) {
                                status.setTextColor(Color.rgb(184, 67, 67));
                                status.setText(resultError);
                                createButton.setEnabled(true);
                                return;
                            }

                            dialog.dismiss();
                            renderUsers();
                        });
                    });
                }));
        dialog.show();
    }

    private void addMetricRow(LinearLayout parent, MetricSpec left, MetricSpec right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        MetricCard leftCard = createMetricCard(left);
        MetricCard rightCard = createMetricCard(right);
        row.addView(leftCard.container, weightedParams(1, 5));
        row.addView(rightCard.container, weightedParams(1, 5));
        addTopMargin(parent, row, 10);
    }

    private void addVoltageMetricRow(
            LinearLayout parent, MetricSpec left, MetricSpec right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        MetricCard leftCard = createVoltageMetricCard(left);
        MetricCard rightCard = createVoltageMetricCard(right);
        row.addView(leftCard.container, weightedParams(1, 5));
        row.addView(rightCard.container, weightedParams(1, 5));
        addTopMargin(parent, row, 10);
    }

    private MetricCard createVoltageMetricCard(MetricSpec spec) {
        LinearLayout card = card(ELECTRIC_SURFACE_TOP, 17);
        card.setPadding(dp(11), dp(10), dp(11), dp(10));
        card.setBackground(electricCardBackground());
        card.addView(metricLabelRow(spec));

        VoltageGaugeView gauge = new VoltageGaugeView(this, spec.color);
        voltageGauges.add(gauge);
        LinearLayout.LayoutParams gaugeParams =
                new LinearLayout.LayoutParams(-1, dp(78));
        gaugeParams.topMargin = dp(2);
        card.addView(gauge, gaugeParams);

        LinearLayout scale = new LinearLayout(this);
        scale.setGravity(Gravity.CENTER_VERTICAL);
        scale.setOrientation(LinearLayout.HORIZONTAL);
        TextView minimum = text("0", 8, ELECTRIC_MUTED, false);
        TextView midpoint = text("150", 8, ELECTRIC_MUTED, false);
        TextView maximum = text("300", 8, ELECTRIC_MUTED, false);
        scale.addView(minimum, new LinearLayout.LayoutParams(0, -2, 1f));
        midpoint.setGravity(Gravity.CENTER);
        scale.addView(midpoint, new LinearLayout.LayoutParams(0, -2, 1f));
        maximum.setGravity(Gravity.END);
        scale.addView(maximum, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(scale);

        LinearLayout valueRow = new LinearLayout(this);
        valueRow.setGravity(Gravity.BOTTOM);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView value = text("—", 23, ELECTRIC_TEXT, true);
        value.setShadowLayer(dp(7), 0, 0,
                Color.argb(65, Color.red(spec.color), Color.green(spec.color),
                        Color.blue(spec.color)));
        animatedReadings.add(value);
        valueRow.addView(value);
        TextView unit = text(" " + spec.unit, 11, ELECTRIC_MUTED, false);
        LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(-2, -2);
        unitParams.bottomMargin = dp(3);
        valueRow.addView(unit, unitParams);
        addTopMargin(card, valueRow, 1);

        MetricCard metric = new MetricCard(card, value, null, gauge);
        metricCards.add(metric);
        return metric;
    }

    private MetricCard createMetricCard(MetricSpec spec) {
        LinearLayout card = card(ELECTRIC_SURFACE_TOP, 17);
        card.setPadding(dp(12), dp(11), dp(12), dp(10));
        card.setBackground(electricCardBackground());
        card.addView(metricLabelRow(spec));

        LinearLayout valueRow = new LinearLayout(this);
        valueRow.setGravity(Gravity.BOTTOM);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView value = text("—", 20, ELECTRIC_TEXT, true);
        value.setShadowLayer(dp(6), 0, 0,
                Color.argb(55, Color.red(spec.color), Color.green(spec.color),
                        Color.blue(spec.color)));
        animatedReadings.add(value);
        valueRow.addView(value);
        TextView unit = text(" " + spec.unit, 11, ELECTRIC_MUTED, false);
        LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(-2, -2);
        unitParams.bottomMargin = dp(3);
        valueRow.addView(unit, unitParams);
        addTopMargin(card, valueRow, 6);

        ProgressBar progress = spec.progress ? progressBar(spec.color) : null;
        if (progress != null) {
            addTopMargin(card, progress, 8);
        }
        MetricCard metric = new MetricCard(card, value, progress, null);
        metricCards.add(metric);
        return metric;
    }

    private LinearLayout metricLabelRow(MetricSpec spec) {
        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.addView(electricIcon(spec.icon, spec.color, 13),
                new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView label = text(spec.label, 11, ELECTRIC_MUTED, false);
        label.setSingleLine(true);
        labelRow.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        return labelRow;
    }

    private LinearLayout stateCard(String label, String value, String iconGlyph) {
        LinearLayout card = card(ELECTRIC_SURFACE_TOP, 15);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(electricCardBackground());
        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.addView(electricIcon(iconGlyph, ELECTRIC_TEAL, 11),
                new LinearLayout.LayoutParams(dp(15), dp(16)));
        TextView labelView = text(label, 9, ELECTRIC_MUTED, false);
        labelView.setSingleLine(true);
        labelRow.addView(labelView, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(labelRow);
        TextView valueView = text(value, 11, ELECTRIC_TEXT, true);
        animatedReadings.add(valueView);
        addTopMargin(card, valueView, 4);
        return card;
    }

    private LinearLayout controlCard(String title, String subtitle, String availability) {
        LinearLayout card = card(ELECTRIC_SURFACE_TOP, 17);
        card.setBackground(electricCardBackground());
        card.addView(text(title, 16, ELECTRIC_TEXT, true));
        addTopMargin(card, text(subtitle, 13, ELECTRIC_MUTED, false), 4);
        Button unavailable = button(availability, ELECTRIC_SURFACE_BOTTOM);
        unavailable.setTextColor(ELECTRIC_MUTED);
        unavailable.setEnabled(false);
        addTopMargin(card, unavailable, 9);
        return card;
    }

    private void showNavigationMenu(View anchor) {
        if (!isAuthenticated) {
            return;
        }

        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();
        menu.setGroupCheckable(NAVIGATION_GROUP, true, true);
        menu.add(NAVIGATION_GROUP, NAVIGATION_MONITOR, 0, "Мониторинг")
                .setCheckable(true)
                .setChecked(!showingControls && !showingHistory && !showingUsers);
        menu.add(NAVIGATION_GROUP, NAVIGATION_HISTORY, 1, "История")
                .setCheckable(true)
                .setChecked(showingHistory);
        menu.add(NAVIGATION_GROUP, NAVIGATION_CONTROLS, 2, "Управление")
                .setCheckable(true)
                .setChecked(showingControls);
        if (isAdminUser) {
            menu.add(NAVIGATION_GROUP, NAVIGATION_USERS, 3, "Пользователи")
                    .setCheckable(true)
                    .setChecked(showingUsers);
        }

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case NAVIGATION_MONITOR:
                    renderMonitor();
                    return true;
                case NAVIGATION_HISTORY:
                    renderHistory();
                    return true;
                case NAVIGATION_CONTROLS:
                    renderControls();
                    return true;
                case NAVIGATION_USERS:
                    renderUsers();
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    private void renderTelemetry(UpsTelemetry telemetry, Date receivedAt) {
        if (batteryValue == null || metricCards.size() != 6) {
            return;
        }

        animateBatteryValue(telemetry.batteryPercent);
        animateBatteryProgress(clampPercent(telemetry.batteryPercent));
        metricCards.get(0).set(telemetry.inputVoltage, null);
        metricCards.get(1).set(telemetry.outputVoltage, null);
        metricCards.get(2).set(telemetry.loadPercent, telemetry.loadPercent);
        metricCards.get(3).set(telemetry.inputFrequency, null);
        metricCards.get(4).set(telemetry.outputFrequency, null);
        metricCards.get(5).set(telemetry.batteryPercent, telemetry.batteryPercent);

        setAnimatedText(avrStatus, telemetry.avrMode == 1
                ? "Норма" : "Код " + telemetry.avrMode);
        if (telemetry.utilityState == UtilityPowerState.GRID_AVAILABLE_CODE) {
            setAnimatedText(utilityStatus, "От электросети");
        } else if (telemetry.utilityState == UtilityPowerState.GRID_FAILED_CODE) {
            setAnimatedText(utilityStatus, "От батареи");
        } else {
            setAnimatedText(utilityStatus, "Код " + telemetry.utilityState);
        }
        setAnimatedText(upsStatus, telemetry.upsState == 1
                ? "Нормально" : "Код " + telemetry.upsState);

        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        updateTime.setText("Обновлено в " + timeFormat.format(receivedAt));
        if (telemetry.hasOnlyZeroMeasurements()) {
            zeroDataNote.setText(
                    "Сервер отвечает, но все числовые показания равны 0. Проверьте подключение ИБП к ПК.");
            zeroDataNote.setVisibility(View.VISIBLE);
        } else {
            zeroDataNote.setVisibility(View.GONE);
        }
    }

    private void connectFromSetup() {
        connectToServer(setupHostInput.getText().toString(),
                setupPortInput.getText().toString());
    }

    private void showSettingsDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), dp(4));

        EditText hostInput = new EditText(this);
        hostInput.setSingleLine(true);
        hostInput.setHint("Домен HTTPS-сервера");
        hostInput.setInputType(InputType.TYPE_CLASS_TEXT);
        hostInput.setText(connectedHost == null ? "" : connectedHost);
        form.addView(hostInput);

        EditText portInput = new EditText(this);
        portInput.setSingleLine(true);
        portInput.setHint("HTTPS-порт (443)");
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setText(String.valueOf(connectedPort));
        form.addView(portInput);

        boolean monitoringEnabled = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getBoolean(UpsPowerMonitorService.MONITOR_ENABLED_KEY, false);
        Button monitoringToggle = button(monitoringEnabled
                ? "Остановить фоновый мониторинг"
                : "Включить фоновый мониторинг", NAVY);
        addTopMargin(form, monitoringToggle, 12);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Настройки подключения")
                .setView(form)
                .setPositiveButton("Подключить", null)
                .setNegativeButton("Отмена", null)
                .create();
        monitoringToggle.setOnClickListener(view -> {
            SharedPreferences.Editor preferences = getSharedPreferences(
                    PREFERENCES, MODE_PRIVATE).edit();
            if (monitoringEnabled) {
                preferences.putBoolean(UpsPowerMonitorService.MONITOR_ENABLED_KEY, false)
                        .putInt(UpsPowerMonitorService.LAST_POWER_STATE_KEY, -1)
                        .apply();
                stopService(new Intent(this, UpsPowerMonitorService.class));
                dialog.dismiss();
                showMessage("Фоновый мониторинг остановлен",
                        "Уведомления о питании больше не отправляются.");
            } else {
                preferences.putBoolean(UpsPowerMonitorService.MONITOR_ENABLED_KEY, true)
                        .putInt(UpsPowerMonitorService.LAST_POWER_STATE_KEY, -1)
                        .apply();
                dialog.dismiss();
                startBackgroundMonitor();
            }
        });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    if (connectToServer(hostInput.getText().toString(),
                            portInput.getText().toString())) {
                        dialog.dismiss();
                    }
                }));
        dialog.show();
    }

    private boolean connectToServer(String hostValue, String portValue) {
        String normalizedHost = UpsServerAddress.normalizeHttpsHost(hostValue);
        if (normalizedHost == null) {
            if (setupHostInput != null && setupHostInput.getVisibility() == View.VISIBLE) {
                setupHostInput.setError("Введите корректный домен HTTPS-сервера");
            }
            showMessage("Проверьте адрес",
                    "Для безопасного удалённого входа нужен домен с действующим HTTPS-сертификатом.");
            return false;
        }

        int port;
        try {
            port = Integer.parseInt(portValue.trim());
        } catch (NumberFormatException exception) {
            showMessage("Проверьте порт", "Для защищённого подключения используется порт 443.");
            return false;
        }
        if (port != DEFAULT_PORT) {
            showMessage("Проверьте порт", "Для защищённого подключения используется порт 443.");
            return false;
        }

        stopPolling();
        connectedHost = normalizedHost;
        connectedPort = port;
        latestTelemetry = null;
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                .putString(HOST_KEY, connectedHost)
                .putInt(PORT_KEY, connectedPort)
                .putInt(UpsPowerMonitorService.LAST_POWER_STATE_KEY, -1)
                .apply();
        renderMonitor();
        if (activityResumed) {
            startPolling();
        }
        return true;
    }

    private URL gatewayUrl(String endpoint) throws IOException {
        return UpsGatewayClient.url(connectedHost, connectedPort, endpoint);
    }

    private void setAuthorization(HttpURLConnection connection, String username, char[] password) {
        UpsGatewayClient.setAuthorization(connection, username, password);
    }

    private void startPolling() {
        if (!activityResumed || !isAuthenticated || connectedHost == null || pollFuture != null
                || networkExecutor.isShutdown()) {
            return;
        }

        final int generation = ++pollGeneration;
        pollFuture = networkExecutor.scheduleWithFixedDelay(
                () -> requestTelemetry(generation),
                0,
                POLL_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    private void stopPolling() {
        pollGeneration++;
        if (pollFuture != null) {
            pollFuture.cancel(true);
            pollFuture = null;
        }
    }

    private void fetchOnce() {
        if (connectedHost == null) {
            renderConnectionSetup();
            return;
        }
        final int generation = pollGeneration;
        networkExecutor.execute(() -> requestTelemetry(generation));
    }

    private void fetchHistoryOnce() {
        if (connectedHost == null) {
            renderConnectionSetup();
            return;
        }
        final int generation = ++historyGeneration;
        if (historyStatus != null) {
            historyStatus.setText("Загрузка журнала…");
            historyContent.setText("");
        }
        networkExecutor.execute(() -> requestEventHistory(generation));
    }

    private void requestEventHistory(int generation) {
        HttpURLConnection connection = null;
        try {
            URL url = gatewayUrl("events.csv");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "text/csv, text/plain");
            connection.setRequestProperty("Cache-Control", "no-cache");
            setAuthorization(connection, currentUsername, activePassword);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                mainHandler.post(() -> {
                    if (generation == historyGeneration && !isFinishing()) {
                        expireSession();
                    }
                });
                return;
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Сервер вернул HTTP " + responseCode + ".");
            }

            String body = readBoundedBody(connection.getInputStream(), 1024 * 1024);
            List<UpsEvent> events = UpsEvent.parseCsv(body);
            SimpleDateFormat dateFormat =
                    new SimpleDateFormat("dd.MM.yyyy  HH:mm:ss", Locale.getDefault());
            StringBuilder content = new StringBuilder();
            for (UpsEvent event : events) {
                content.append(dateFormat.format(event.timestamp))
                        .append('\n')
                        .append(event.description)
                        .append("\n\n");
            }

            mainHandler.post(() -> {
                if (generation != historyGeneration || !showingHistory || isFinishing()) {
                    return;
                }
                historyStatus.setText(events.isEmpty()
                        ? "В журнале пока нет событий."
                        : "Загружено событий: " + events.size());
                historyContent.setText(content.toString().trim());
            });
        } catch (IOException exception) {
            String message = exception.getMessage() == null
                    ? "Проверьте подключение и сервер истории на компьютере."
                    : exception.getMessage();
            mainHandler.post(() -> {
                if (generation != historyGeneration || !showingHistory || isFinishing()) {
                    return;
                }
                historyStatus.setText("Не удалось загрузить историю: " + message);
                historyContent.setText("");
            });
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void requestTelemetry(int generation) {
        HttpURLConnection connection = null;
        try {
            URL url = gatewayUrl("ups.txt");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "text/plain");
            connection.setRequestProperty("Cache-Control", "no-cache");
            setAuthorization(connection, currentUsername, activePassword);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                mainHandler.post(() -> {
                    if (generation == pollGeneration && !isFinishing()) {
                        expireSession();
                    }
                });
                return;
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Сервер вернул HTTP " + responseCode + ".");
            }

            String body = readBoundedBody(connection.getInputStream());
            UpsTelemetry telemetry = UpsTelemetry.parse(body);
            Date receivedAt = new Date();
            mainHandler.post(() -> {
                if (generation != pollGeneration || isFinishing()) {
                    return;
                }
                latestTelemetry = telemetry;
                connectionStatus.setText("Сервер доступен · данные обновляются каждые 2 сек.");
                connectionStatus.setTextColor(connectionStatusColor(true));
                renderTelemetry(telemetry, receivedAt);
            });
        } catch (IOException exception) {
            String message = exception.getMessage() == null
                    ? "Проверьте Wi-Fi и настройки UPSMON PRO."
                    : exception.getMessage();
            mainHandler.post(() -> {
                if (generation != pollGeneration || isFinishing()) {
                    return;
                }
                connectionStatus.setText("Нет связи · " + message);
                connectionStatus.setTextColor(connectionStatusColor(false));
            });
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readBoundedBody(InputStream inputStream) throws IOException {
        return readBoundedBody(inputStream, 1024);
    }

    private String readBoundedBody(InputStream inputStream, int maxLength) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (body.length() + line.length() > maxLength) {
                    throw new IOException("Ответ сервера слишком большой.");
                }
                body.append(line).append('\n');
            }
        }
        return body.toString();
    }

    private void showControlRequirements() {
        showMessage(
                "Нужен интерфейс команд",
                "Сейчас сервер UPSMON PRO отдаёт только GET /ups.txt. Встроенный веб-сайт не содержит команд управления. Для теста батареи, сигнала, Green Mode и других действий нужна официальная документация/API UPSMON или поддерживаемый локальный сервер-компаньон для Windows.");
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Понятно", null)
                .show();
    }

    private ProgressBar progressBar(int color) {
        ProgressBar progress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(color));
        progress.setProgressBackgroundTintList(
                ColorStateList.valueOf(dashboardTheme
                        ? Color.rgb(35, 57, 75) : Color.rgb(221, 229, 237)));
        return progress;
    }

    private int clampPercent(double value) {
        return (int) Math.max(0, Math.min(100, Math.round(value)));
    }

    private String formatNumber(double value) {
        DecimalFormat format = new DecimalFormat("0.#",
                DecimalFormatSymbols.getInstance(Locale.getDefault()));
        format.setGroupingUsed(false);
        return format.format(value);
    }

    private LinearLayout pageBody() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(4), 0, dp(16));
        return body;
    }

    private LinearLayout card(int color, int radius) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(roundRect(color, radius, color, 0));
        return card;
    }

    private Button button(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setMinHeight(dp(42));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackgroundTintList(ColorStateList.valueOf(color));
        return button;
    }

    private ImageButton toolbarIconButton(int iconResource, String description, int color) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(iconResource);
        button.setContentDescription(description);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setBackground(roundRect(color, 12, color, 0));
        button.setImageTintList(ColorStateList.valueOf(ELECTRIC_TEXT));
        return button;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private GradientDrawable roundRect(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        if (strokeWidth > 0) {
            background.setStroke(dp(strokeWidth), strokeColor);
        }
        return background;
    }

    private LinearLayout.LayoutParams weightedParams(float weight, int horizontalMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, weight);
        params.leftMargin = dp(horizontalMarginDp / 2);
        params.rightMargin = dp(horizontalMarginDp / 2);
        return params;
    }

    private void addTopMargin(LinearLayout parent, View child, int marginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(marginDp);
        parent.addView(child, params);
    }

    private void addTopMargin(LinearLayout parent, Button child, int marginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(marginDp);
        parent.addView(child, params);
    }

    private void addTopMargin(LinearLayout parent, ProgressBar child, int marginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(6));
        params.topMargin = dp(marginDp);
        parent.addView(child, params);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && connectedHost == null) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class MetricSpec {
        final String label;
        final String unit;
        final int color;
        final boolean progress;
        final String icon;

        MetricSpec(String label, String unit, int color, boolean progress, String icon) {
            this.label = label;
            this.unit = unit;
            this.color = color;
            this.progress = progress;
            this.icon = icon;
        }
    }

    private static final class MetricCard {
        final LinearLayout container;
        final TextView value;
        final ProgressBar progress;
        final VoltageGaugeView gauge;
        private float displayedValue;
        private boolean hasValue;
        private ValueAnimator numberAnimator;
        private ValueAnimator progressAnimator;

        MetricCard(LinearLayout container, TextView value, ProgressBar progress,
                   VoltageGaugeView gauge) {
            this.container = container;
            this.value = value;
            this.progress = progress;
            this.gauge = gauge;
        }

        void set(double measurement, Double percent) {
            if (gauge != null) {
                gauge.setVoltage(measurement);
            }
            if (numberAnimator != null) {
                numberAnimator.cancel();
                numberAnimator = null;
            }
            if (!hasValue || displayedValue == (float) measurement) {
                displayedValue = (float) measurement;
                value.setText(format(displayedValue));
                hasValue = true;
            } else {
                numberAnimator = ValueAnimator.ofFloat(
                        displayedValue, (float) measurement);
                numberAnimator.setDuration(650);
                numberAnimator.addUpdateListener(animator -> {
                    displayedValue = (float) animator.getAnimatedValue();
                    value.setText(format(displayedValue));
                });
                numberAnimator.start();
            }
            if (progress != null) {
                int targetProgress = percent == null ? 0
                        : (int) Math.max(0, Math.min(100, Math.round(percent)));
                if (progressAnimator != null) {
                    progressAnimator.cancel();
                    progressAnimator = null;
                }
                progressAnimator = ValueAnimator.ofInt(
                        progress.getProgress(), targetProgress);
                progressAnimator.setDuration(650);
                progressAnimator.addUpdateListener(animator ->
                        progress.setProgress((int) animator.getAnimatedValue()));
                progressAnimator.start();
            }
        }

        void cancelAnimations() {
            if (numberAnimator != null) {
                numberAnimator.cancel();
                numberAnimator = null;
            }
            if (progressAnimator != null) {
                progressAnimator.cancel();
                progressAnimator = null;
            }
        }

        private String format(double number) {
            DecimalFormat format = new DecimalFormat("0.#",
                    DecimalFormatSymbols.getInstance(Locale.getDefault()));
            format.setGroupingUsed(false);
            return format.format(number);
        }
    }
}
