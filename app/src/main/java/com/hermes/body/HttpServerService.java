package com.hermes.body;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Foreground service that keeps the HTTP server alive.
 */
public class HttpServerService extends Service {

    private static final String TAG = "HermesHTTP";
    private static final String CHANNEL_ID = "hermes_body";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, createNotification("Hermes Body running"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        HermesAccessibilityService svc = HermesAccessibilityService.getInstance();
        if (svc == null) {
            Log.w(TAG, "Accessibility service not running");
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "Hermes Body", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Hermes Body Service");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private Notification createNotification(String text) {
        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Hermes Body")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build();
    }
}