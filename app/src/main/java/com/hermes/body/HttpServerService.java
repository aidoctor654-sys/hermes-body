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
    private static final int PORT = 8421;

    private HermesBodyServer server;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, createNotification("Hermes Body: starting server..."));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "HttpServerService onStartCommand");

        if (server == null) {
            try {
                server = new HermesBodyServer();
                server.start();
                Log.i(TAG, "Hermes HTTP server started on port " + PORT);
                updateNotification("Hermes Body: server on :" + PORT);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start HTTP server", e);
                updateNotification("Hermes Body: ERROR - " + e.getMessage());
            }
        } else {
            Log.i(TAG, "Server already running");
        }

        return START_STICKY;
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(1, createNotification(text));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
            server = null;
            Log.i(TAG, "Hermes HTTP server stopped");
        }
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