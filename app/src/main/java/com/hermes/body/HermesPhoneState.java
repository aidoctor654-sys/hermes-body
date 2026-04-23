package com.hermes.body;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Phone state — sprawdza stan na żądanie (polling).
 * PhoneStateListener powoduje błąd d8 przy kompilacji,
 * więc używamy getLastKnownState z TelephonyManager.
 */
public class HermesPhoneState {

    private static final String TAG = "HermesPhone";
    private static HermesPhoneState instance;

    private final Context context;

    public HermesPhoneState(Context ctx) {
        this.context = ctx.getApplicationContext();
        instance = this;
    }

    public void start() {
        Log.i(TAG, "Phone state monitor ready (polling mode)");
    }

    public void stop() {}

    public static HermesPhoneState getInstance() {
        return instance;
    }

    public JSONObject getStatus() {
        JSONObject result = new JSONObject();
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                int callState = tm.getCallState();
                String stateStr = "idle";
                if (callState == TelephonyManager.CALL_STATE_RINGING) stateStr = "ringing";
                else if (callState == TelephonyManager.CALL_STATE_OFFHOOK) stateStr = "offhook";

                result.put("state", stateStr);
                result.put("networkOperator", tm.getNetworkOperatorName());
                result.put("networkType", tm.getNetworkType());
                result.put("phoneType", tm.getPhoneType());
                result.put("simState", tm.getSimState());
                result.put("simOperator", tm.getSimOperatorName());

                // Try to get number (requires READ_PHONE_STATE + sometimes READ_SMS)
                try {
                    String lineNum = tm.getLine1Number();
                    result.put("lineNumber", lineNum != null ? lineNum : "unknown");
                } catch (SecurityException e) {
                    result.put("lineNumber", "no_permission");
                }
            }
        } catch (SecurityException e) {
            try { result.put("error", "no READ_PHONE_STATE permission"); } catch (Exception ex) {}
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ex) {}
        }
        return result;
    }

    public JSONArray getCallLog(int limit) {
        JSONArray arr = new JSONArray();
        try {
            android.database.Cursor cursor = context.getContentResolver().query(
                android.provider.CallLog.Calls.CONTENT_URI,
                new String[] {
                    android.provider.CallLog.Calls.NUMBER,
                    android.provider.CallLog.Calls.CACHED_NAME,
                    android.provider.CallLog.Calls.DATE,
                    android.provider.CallLog.Calls.DURATION,
                    android.provider.CallLog.Calls.TYPE
                },
                null, null,
                android.provider.CallLog.Calls.DATE + " DESC LIMIT " + limit
            );
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject call = new JSONObject();
                    call.put("number", cursor.getString(0));
                    call.put("name", cursor.getString(1));
                    call.put("date", cursor.getLong(2));
                    call.put("duration", cursor.getString(3));
                    int type = cursor.getInt(4);
                    String typeStr = "other";
                    if (type == android.provider.CallLog.Calls.INCOMING_TYPE) typeStr = "incoming";
                    else if (type == android.provider.CallLog.Calls.OUTGOING_TYPE) typeStr = "outgoing";
                    else if (type == android.provider.CallLog.Calls.MISSED_TYPE) typeStr = "missed";
                    call.put("type", typeStr);
                    arr.put(call);
                }
                cursor.close();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "No CALL_LOG permission", e);
        } catch (Exception e) {
            Log.e(TAG, "Error reading call log", e);
        }
        return arr;
    }
}