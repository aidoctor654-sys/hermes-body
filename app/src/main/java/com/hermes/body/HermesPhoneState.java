package com.hermes.body;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.telephony.PhoneStateListener;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Phone state monitor — daje Hermesowi czułość na połączenia.
 */
public class HermesPhoneState {

    private static final String TAG = "HermesPhone";
    private static HermesPhoneState instance;

    private final Context context;
    private String currentState = "idle";
    private String lastCallerNumber = null;
    private final List<JSONObject> callLog = new ArrayList<>();
    private static final int MAX_LOG = 50;

    private TelephonyManager telephonyManager;
    private HermesPhoneStateListener phoneStateListener;

    public HermesPhoneState(Context ctx) {
        this.context = ctx.getApplicationContext();
        instance = this;
    }

    // Named inner class to avoid d8 bug with anonymous classes
    private class HermesPhoneStateListener extends PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            try {
                JSONObject entry = new JSONObject();
                entry.put("time", System.currentTimeMillis());

                switch (state) {
                    case TelephonyManager.CALL_STATE_IDLE:
                        entry.put("state", "idle");
                        if ("ringing".equals(currentState)) {
                            entry.put("event", "missed");
                            entry.put("number", lastCallerNumber);
                        } else if ("offhook".equals(currentState)) {
                            entry.put("event", "ended");
                        }
                        currentState = "idle";
                        break;
                    case TelephonyManager.CALL_STATE_RINGING:
                        currentState = "ringing";
                        lastCallerNumber = incomingNumber;
                        entry.put("state", "ringing");
                        entry.put("event", "incoming");
                        entry.put("number", incomingNumber);
                        break;
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        entry.put("state", "offhook");
                        entry.put("event", "answered");
                        if (lastCallerNumber != null) {
                            entry.put("number", lastCallerNumber);
                        }
                        currentState = "offhook";
                        break;
                }

                if (entry.has("event")) {
                    callLog.add(entry);
                    while (callLog.size() > MAX_LOG) callLog.remove(0);
                    Log.i(TAG, "Call event: " + entry.toString());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing call state", e);
            }
        }
    }

    public void start() {
        try {
            telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null) {
                phoneStateListener = new HermesPhoneStateListener();
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
                Log.i(TAG, "Phone state listener started");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "No READ_PHONE_STATE permission", e);
        } catch (Exception e) {
            Log.e(TAG, "Error starting phone listener", e);
        }
    }

    public void stop() {
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    public static HermesPhoneState getInstance() {
        return instance;
    }

    public String getCurrentState() {
        return currentState;
    }

    public String getLastCallerNumber() {
        return lastCallerNumber;
    }

    public JSONArray getCallLog(int limit) {
        JSONArray arr = new JSONArray();
        int start = Math.max(0, callLog.size() - limit);
        for (int i = start; i < callLog.size(); i++) {
            arr.put(callLog.get(i));
        }
        return arr;
    }

    public JSONObject getStatus() {
        try {
            return new JSONObject()
                .put("state", currentState)
                .put("lastCaller", lastCallerNumber)
                .put("eventsLogged", callLog.size());
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}