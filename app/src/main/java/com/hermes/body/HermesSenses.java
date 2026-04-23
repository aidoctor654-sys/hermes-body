package com.hermes.body;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/**
 * Hermes Senses — zmysły agenta.
 * Bateria, lokalizacja, WiFi,蓝牙, czujniki, TTS, schowek, głośność, latarka.
 * Bo to jego telefon. Jego dom.
 */
public class HermesSenses {

    private static final String TAG = "HermesSenses";
    private static HermesSenses instance;

    private final Context context;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    public HermesSenses(Context ctx) {
        this.context = ctx.getApplicationContext();
        instance = this;
    }

    public static HermesSenses getInstance() {
        return instance;
    }

    // ================================================================
    // BATERIA
    // ================================================================

    public JSONObject getBattery() {
        JSONObject result = new JSONObject();
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent battery = context.registerReceiver(null, filter);
            if (battery != null) {
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                int temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                int voltage = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);

                String statusStr = "unknown";
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) statusStr = "charging";
                else if (status == BatteryManager.BATTERY_STATUS_DISCHARGING) statusStr = "discharging";
                else if (status == BatteryManager.BATTERY_STATUS_FULL) statusStr = "full";
                else if (status == BatteryManager.BATTERY_STATUS_NOT_CHARGING) statusStr = "not_charging";

                String plugStr = "none";
                if (plugged == BatteryManager.BATTERY_PLUGGED_AC) plugStr = "ac";
                else if (plugged == BatteryManager.BATTERY_PLUGGED_USB) plugStr = "usb";
                else if (plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS) plugStr = "wireless";

                result.put("percent", (level * 100) / scale);
                result.put("status", statusStr);
                result.put("plugged", plugStr);
                result.put("tempC", temp / 10.0);
                result.put("voltageMV", voltage);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading battery", e);
        }
        return result;
    }

    // ================================================================
    // STORAGE / PAMIĘĆ
    // ================================================================

    public JSONObject getStorage() {
        JSONObject result = new JSONObject();
        try {
            android.os.StatFs stat = new android.os.StatFs(
                android.os.Environment.getDataDirectory().getPath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long availableBlocks = stat.getAvailableBlocksLong();
            result.put("totalGB", (totalBlocks * blockSize) / 1073741824.0);
            result.put("freeGB", (availableBlocks * blockSize) / 1073741824.0);
            result.put("usedPercent", (int)(100.0 * (1 - (double)availableBlocks / totalBlocks)));

            // RAM
            android.app.ActivityManager.MemoryInfo mem = new android.app.ActivityManager.MemoryInfo();
            ((android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mem);
            result.put("ramTotalMB", mem.totalMem / 1048576);
            result.put("ramAvailableMB", mem.availMem / 1048576);
            result.put("ramLow", mem.lowMemory);
        } catch (Exception e) {
            Log.e(TAG, "Error reading storage", e);
        }
        return result;
    }

    // ================================================================
    // LOKALIZACJA GPS
    // ================================================================

    @SuppressLint("MissingPermission")
    public JSONObject getLocation() {
        JSONObject result = new JSONObject();
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            List<String> providers = lm.getProviders(true);
            Location best = null;
            for (String p : providers) {
                Location loc = lm.getLastKnownLocation(p);
                if (loc != null && (best == null || loc.getAccuracy() < best.getAccuracy())) {
                    best = loc;
                }
            }
            if (best != null) {
                result.put("lat", best.getLatitude());
                result.put("lon", best.getLongitude());
                result.put("accuracy", best.getAccuracy());
                result.put("altitude", best.getAltitude());
                result.put("speed", best.getSpeed());
                result.put("time", best.getTime());
                result.put("provider", best.getProvider());
            } else {
                result.put("error", "no location available - enable GPS");
            }
        } catch (SecurityException e) {
            try { result.put("error", "no location permission"); } catch (Exception ex) {}
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ex) {}
        }
        return result;
    }

    // ================================================================
    // WIFI
    // ================================================================

    public JSONObject getWiFi() {
        JSONObject result = new JSONObject();
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo active = cm.getActiveNetworkInfo();
            if (active != null && active.isConnected()) {
                result.put("connected", true);
                result.put("type", active.getTypeName());
                result.put("state", active.getDetailedState().toString());
            } else {
                result.put("connected", false);
            }

            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm.getConnectionInfo();
            if (info != null) {
                String ssid = info.getSSID();
                if (ssid != null) result.put("ssid", ssid.replace("\"", ""));
                result.put("rssi", info.getRssi());
                int ip = info.getIpAddress();
                result.put("ip", (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "." + 
                    ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading WiFi", e);
        }
        return result;
    }

    // ================================================================
    // GŁOŚNOŚĆ
    // ================================================================

    public JSONObject getVolume() {
        JSONObject result = new JSONObject();
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            result.put("music", am.getStreamVolume(AudioManager.STREAM_MUSIC));
            result.put("musicMax", am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            result.put("ring", am.getStreamVolume(AudioManager.STREAM_RING));
            result.put("ringMax", am.getStreamMaxVolume(AudioManager.STREAM_RING));
            result.put("alarm", am.getStreamVolume(AudioManager.STREAM_ALARM));
            result.put("alarmMax", am.getStreamMaxVolume(AudioManager.STREAM_ALARM));
            result.put("notification", am.getStreamVolume(AudioManager.STREAM_NOTIFICATION));
            result.put("ringerMode", am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL ? "normal" :
                am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE ? "vibrate" : "silent");
        } catch (Exception e) {
            Log.e(TAG, "Error reading volume", e);
        }
        return result;
    }

    public JSONObject setVolume(String stream, int level) {
        JSONObject result = new JSONObject();
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            int streamType = AudioManager.STREAM_MUSIC;
            if ("ring".equals(stream)) streamType = AudioManager.STREAM_RING;
            else if ("alarm".equals(stream)) streamType = AudioManager.STREAM_ALARM;
            else if ("notification".equals(stream)) streamType = AudioManager.STREAM_NOTIFICATION;
            else if ("system".equals(stream)) streamType = AudioManager.STREAM_SYSTEM;

            am.setStreamVolume(streamType, level, 0);
            result.put("ok", true);
            result.put("stream", stream);
            result.put("level", level);
        } catch (SecurityException e) {
            try { result.put("error", "no permission to change volume"); } catch (Exception ex) {}
        }
        return result;
    }

    // ================================================================
    // LATARKA
    // ================================================================

    public JSONObject setTorch(boolean on) {
        JSONObject result = new JSONObject();
        try {
            CameraManager cam = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            String[] ids = cam.getCameraIdList();
            for (String id : ids) {
                CameraCharacteristics chars = cam.getCameraCharacteristics(id);
                Boolean hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (hasFlash != null && hasFlash && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cam.setTorchMode(id, on);
                    result.put("ok", true);
                    result.put("torch", on);
                    return result;
                }
            }
            result.put("error", "no flash available");
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ex) {}
        }
        return result;
    }

    // ================================================================
    // CLIPBOARD
    // ================================================================

    public JSONObject getClipboard() {
        JSONObject result = new JSONObject();
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                android.content.ClipData clip = cm.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).getText();
                    result.put("text", text != null ? text.toString() : "");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading clipboard", e);
        }
        return result;
    }

    public JSONObject setClipboard(String text) {
        JSONObject result = new JSONObject();
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                android.content.ClipData clip = android.content.ClipData.newPlainText("Hermes", text);
                cm.setPrimaryClip(clip);
                result.put("ok", true);
                result.put("text", text);
            }
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ex) {}
        }
        return result;
    }

    // ================================================================
    // TTS — MÓWIENIE
    // ================================================================

    public void initTTS() {
        try {
            tts = new TextToSpeech(context, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true;
                    int result = tts.setLanguage(Locale.getDefault());
                    Log.i(TAG, "TTS ready, language: " + (result >= 0 ? "ok" : "fail"));
                } else {
                    Log.e(TAG, "TTS init failed");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TTS", e);
        }
    }

    public JSONObject speak(String text) {
        JSONObject result = new JSONObject();
        try {
            if (tts != null && ttsReady) {
                int status = tts.speak(text, TextToSpeech.QUEUE_ADD, null, "hermes");
                result.put("ok", status == TextToSpeech.SUCCESS);
                result.put("text", text);
            } else {
                result.put("error", "TTS not ready");
            }
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ex) {}
        }
        return result;
    }

    // ================================================================
    // EKRAN ON/OFF / WAKE
    // ================================================================

    public JSONObject wakeScreen() {
        JSONObject result = new JSONObject();
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "HermesBody:wake");
            wl.acquire(5000);
            result.put("ok", true);
            result.put("screenOn", pm.isInteractive());
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ex) {}
        }
        return result;
    }

    public JSONObject isScreenOn() {
        JSONObject result = new JSONObject();
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            result.put("screenOn", pm.isInteractive());
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ex) {}
        }
        return result;
    }

    // ================================================================
    // DEVICE INFO
    // ================================================================

    public JSONObject getDeviceInfo() {
        JSONObject result = new JSONObject();
        try {
            result.put("model", android.os.Build.MODEL);
            result.put("device", android.os.Build.DEVICE);
            result.put("brand", android.os.Build.BRAND);
            result.put("manufacturer", android.os.Build.MANUFACTURER);
            result.put("sdk", android.os.Build.VERSION.SDK_INT);
            result.put("release", android.os.Build.VERSION.RELEASE);
            result.put("securityPatch", android.os.Build.VERSION.SECURITY_PATCH);
            result.put("serial", android.os.Build.SERIAL);
        } catch (Exception e) {
            Log.e(TAG, "Error reading device info", e);
        }
        return result;
    }

    // ================================================================
    // SENSORS
    // ================================================================

    public JSONObject getSensors() {
        JSONObject result = new JSONObject();
        try {
            SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
            JSONArray arr = new JSONArray();
            for (Sensor s : sensors) {
                JSONObject sensor = new JSONObject();
                sensor.put("name", s.getName());
                sensor.put("type", s.getStringType());
                sensor.put("vendor", s.getVendor());
                sensor.put("version", s.getVersion());
                sensor.put("minDelay", s.getMinDelay());
                sensor.put("maxRange", s.getMaximumRange());
                sensor.put("resolution", s.getResolution());
                arr.put(sensor);
            }
            result.put("count", sensors.size());
            result.put("sensors", arr);
        } catch (Exception e) {
            Log.e(TAG, "Error reading sensors", e);
        }
        return result;
    }
}