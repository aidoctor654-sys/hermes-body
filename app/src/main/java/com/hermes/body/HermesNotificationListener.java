package com.hermes.body;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.graphics.drawable.Icon;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Notification Listener Service — daje Hermesowi czułość na powiadomienia.
 * Widzi wszystko co przychodzi na pasek: WhatsApp, Telegram, SMS, etc.
 */
public class HermesNotificationListener extends NotificationListenerService {

    private static final String TAG = "HermesNotif";
    private static HermesNotificationListener instance;
    private static final int MAX_STORED = 50;

    private final List<JSONObject> recentNotifications = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.i(TAG, "Hermes Notification Listener created");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            JSONObject notif = parseNotification(sbn, "posted");
            recentNotifications.add(notif);
            // Keep only last MAX_STORED
            while (recentNotifications.size() > MAX_STORED) {
                recentNotifications.remove(0);
            }
            Log.d(TAG, "Notification from " + sbn.getPackageName() + ": " + 
                sbn.getNotification().tickerText);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing posted notification", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Could track removals too, but not critical
    }

    @Override
    public void onDestroy() {
        instance = null;
        Log.i(TAG, "Hermes Notification Listener destroyed");
        super.onDestroy();
    }

    public static HermesNotificationListener getInstance() {
        return instance;
    }

    /**
     * Get all current active notifications as JSONArray
     */
    public JSONArray getActiveNotificationsJSON() {
        JSONArray arr = new JSONArray();
        try {
            StatusBarNotification[] active = super.getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) {
                    arr.put(parseNotification(sbn, "active"));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting active notifications", e);
        }
        return arr;
    }

    /**
     * Get recent notifications (posted since service started)
     */
    public JSONArray getRecentNotifications(int limit) {
        JSONArray arr = new JSONArray();
        int start = Math.max(0, recentNotifications.size() - limit);
        for (int i = start; i < recentNotifications.size(); i++) {
            arr.put(recentNotifications.get(i));
        }
        return arr;
    }

    private JSONObject parseNotification(StatusBarNotification sbn, String state) throws Exception {
        JSONObject json = new JSONObject();
        json.put("package", sbn.getPackageName());
        json.put("key", sbn.getKey());
        json.put("state", state);
        json.put("time", sbn.getPostTime());
        json.put("ongoing", sbn.isOngoing());
        json.put("clearable", sbn.isClearable());

        android.app.Notification n = sbn.getNotification();
        if (n != null) {
            // Ticker text (brief summary)
            if (n.tickerText != null) {
                json.put("ticker", n.tickerText.toString());
            }

            // Extract text from extras (the real content)
            Bundle extras = n.extras;
            if (extras != null) {
                String title = extras.getString(android.app.Notification.EXTRA_TITLE);
                CharSequence textSeq = extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
                CharSequence bigTextSeq = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT);
                CharSequence subText = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT);
                CharSequence infoText = extras.getCharSequence(android.app.Notification.EXTRA_INFO_TEXT);
                CharSequence summaryText = extras.getCharSequence(android.app.Notification.EXTRA_SUMMARY_TEXT);

                if (title != null) json.put("title", title);
                if (textSeq != null) json.put("text", textSeq.toString());
                if (bigTextSeq != null) json.put("bigText", bigTextSeq.toString());
                if (subText != null) json.put("subText", subText.toString());
                if (infoText != null) json.put("infoText", infoText.toString());
                if (summaryText != null) json.put("summaryText", summaryText.toString());

                // Messages (for messaging apps like WhatsApp)
                CharSequence[] messages = extras.getCharSequenceArray(android.app.Notification.EXTRA_MESSAGES);
                if (messages != null) {
                    JSONArray msgArr = new JSONArray();
                    for (CharSequence msg : messages) {
                        msgArr.put(msg.toString());
                    }
                    json.put("messages", msgArr);
                }
            }

            // Category (msg, call, social, etc)
            if (n.category != null) {
                json.put("category", n.category);
            }
        }

        return json;
    }
}