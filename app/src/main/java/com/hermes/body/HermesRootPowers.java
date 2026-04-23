package com.hermes.body;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Hermes Body ROOT — Ultra Max Edition.
 * Dla urządzeń z rootem. Pełna władza.
 *
 * input tap — natychmiastowe tapnięcia (bez delay accessibility)
 * screencap — prawdziwe screenshoty piksel po pikselu
 * pm install — instalacja bez dialogu
 * dumpsys — głębokie info o systemie
 * settings put — zmiana ustawień
 * iptables / tcpdump — kontrola sieci
 * /data/data — czytanie apki dowolnej
 * svc wifi/data — zarządzanie konektywnością
 */
public class HermesRootPowers {

    private static final String TAG = "HermesROOT";
    private static HermesRootPowers instance;

    public HermesRootPowers() {
        instance = this;
    }

    public static HermesRootPowers getInstance() {
        return instance;
    }

    // ================================================================
    // ROOT SHELL
    // ================================================================

    /**
     * Execute command as root via su
     */
    public JSONObject su(String command) {
        JSONObject result = new JSONObject();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            StringBuilder out = new StringBuilder();
            String line;
            while ((line = stdout.readLine()) != null) out.append(line).append("\n");

            StringBuilder err = new StringBuilder();
            while ((line = stderr.readLine()) != null) err.append(line).append("\n");

            p.waitFor();
            stdout.close();
            stderr.close();

            result.put("exitCode", p.exitValue());
            result.put("stdout", out.toString().trim());
            result.put("stderr", err.toString().trim());
            result.put("success", p.exitValue() == 0);
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ex) {}
        }
        return result;
    }

    // ================================================================
    // INSTANT TOUCH — bez accessibility delay
    // ================================================================

    public JSONObject rootTap(int x, int y) {
        return su("input tap " + x + " " + y);
    }

    public JSONObject rootSwipe(int x1, int y1, int x2, int y2, int durationMs) {
        return su("input swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + durationMs);
    }

    public JSONObject rootType(String text) {
        // Escape special chars for shell
        String escaped = text.replace(" ", "%s").replace("'", "'\\''");
        return su("input text '" + escaped + "'");
    }

    public JSONObject rootPressKey(int keyCode) {
        return su("input keyevent " + keyCode);
    }

    // ================================================================
    // SCREENSHOT — prawdziwe piksele
    // ================================================================

    public JSONObject screenshot(String path) {
        if (path == null || path.isEmpty()) {
            path = "/sdcard/hermes-screenshot-" + System.currentTimeMillis() + ".png";
        }
        JSONObject result = su("screencap -p " + path);
        try { result.put("path", path); } catch (Exception e) {}
        return result;
    }

    public JSONObject screenrecord(String path, int durationSec) {
        if (path == null || path.isEmpty()) {
            path = "/sdcard/hermes-recording-" + System.currentTimeMillis() + ".mp4";
        }
        JSONObject result = su("screenrecord --time-limit " + durationSec + " " + path);
        try { result.put("path", path); result.put("duration", durationSec); } catch (Exception e) {}
        return result;
    }

    // ================================================================
    // DUMPSYS — głębokie info systemowe
    // ================================================================

    public JSONObject dumpsys(String service) {
        if (service == null || service.isEmpty()) service = "";
        return su("dumpsys " + service);
    }

    public JSONObject dumpsysActivity() {
        return su("dumpsys activity top");
    }

    public JSONObject dumpsysBattery() {
        return su("dumpsys battery");
    }

    public JSONObject dumpsysNotification() {
        return su("dumpsys notification");
    }

    public JSONObject dumpsysWifi() {
        return su("dumpsys wifi");
    }

    public JSONObject dumpsysMeminfo() {
        return su("dumpsys meminfo");
    }

    public JSONObject dumpsysCPU() {
        return su("dumpsys cpuinfo");
    }

    // ================================================================
    // APP MANAGEMENT
    // ================================================================

    public JSONObject pmInstall(String apkPath) {
        JSONObject result = su("pm install -r -g " + apkPath);
        try { result.put("action", "install"); result.put("apk", apkPath); } catch (Exception e) {}
        return result;
    }

    public JSONObject pmUninstall(String packageName) {
        JSONObject result = su("pm uninstall " + packageName);
        try { result.put("action", "uninstall"); result.put("package", packageName); } catch (Exception e) {}
        return result;
    }

    public JSONObject pmClear(String packageName) {
        return su("pm clear " + packageName);
    }

    public JSONObject pmGrant(String packageName, String permission) {
        return su("pm grant " + packageName + " " + permission);
    }

    public JSONObject pmRevoke(String packageName, String permission) {
        return su("pm revoke " + packageName + " " + permission);
    }

    // ================================================================
    // SYSTEM SETTINGS
    // ================================================================

    public JSONObject settingsPut(String namespace, String key, String value) {
        return su("settings put " + namespace + " " + key + " " + value);
    }

    public JSONObject settingsGet(String namespace, String key) {
        return su("settings get " + namespace + " " + key);
    }

    // ================================================================
    // NETWORK — iptables, tcpdump, svc
    // ================================================================

    public JSONObject iptables(String rule) {
        return su("iptables " + rule);
    }

    public JSONObject tcpdump(String args, int durationSec) {
        return su("timeout " + durationSec + " tcpdump " + args);
    }

    public JSONObject wifiEnable() {
        return su("svc wifi enable");
    }

    public JSONObject wifiDisable() {
        return su("svc wifi disable");
    }

    public JSONObject dataEnable() {
        return su("svc data enable");
    }

    public JSONObject dataDisable() {
        return su("svc data disable");
    }

    // ================================================================
    // /data/data — czytanie danych apek
    // ================================================================

    public JSONObject readAppData(String packageName, String filePath) {
        return su("cat /data/data/" + packageName + "/" + filePath);
    }

    public JSONObject listAppData(String packageName) {
        return su("ls -la /data/data/" + packageName + "/");
    }

    public JSONObject readAppDB(String packageName, String dbName, String query) {
        return su("sqlite3 /data/data/" + packageName + "/databases/" + dbName + " \"" + query + "\"");
    }

    // ================================================================
    // PROCESS MANAGEMENT
    // ================================================================

    public JSONObject psList() {
        return su("ps -A");
    }

    public JSONObject killProcess(int pid) {
        return su("kill " + pid);
    }

    public JSONObject killPackage(String packageName) {
        return su("am force-stop " + packageName);
    }

    // ================================================================
    // LOGCAT
    // ================================================================

    public JSONObject logcat(int lines) {
        return su("logcat -d -t " + lines);
    }

    public JSONObject logcatTag(String tag, int lines) {
        return su("logcat -d -s " + tag + " -t " + lines);
    }

    // ================================================================
    // SYSTEM PROPERTIES
    // ================================================================

    public JSONObject getProp(String name) {
        return su("getprop " + name);
    }

    public JSONObject setProp(String name, String value) {
        return su("setprop " + name + " " + value);
    }

    // ================================================================
    // REBOOT / POWER
    // ================================================================

    public JSONObject reboot() {
        return su("reboot");
    }

    public JSONObject rebootRecovery() {
        return su("reboot recovery");
    }

    public JSONObject shutdown() {
        return su("reboot -p");
    }

    // ================================================================
    // FILE OPERATIONS
    // ================================================================

    public JSONObject readFile(String path) {
        return su("cat " + path);
    }

    public JSONObject writeFile(String path, String content) {
        // Write via tee (handles escaping better)
        String escaped = content.replace("'", "'\\''");
        return su("echo -n '" + escaped + "' | tee " + path);
    }

    public JSONObject listDir(String path) {
        return su("ls -la " + path);
    }

    public JSONObject chmod(String mode, String path) {
        return su("chmod " + mode + " " + path);
    }

    public JSONObject copyFile(String src, String dst) {
        return su("cp " + src + " " + dst);
    }
}