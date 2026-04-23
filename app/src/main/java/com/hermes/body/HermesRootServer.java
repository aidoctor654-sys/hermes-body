package com.hermes.body;

import android.util.Log;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Root-specific HTTP endpoints. Only active when su is available.
 * Mounted as additional routes on the same port 8421.
 */
public class HermesRootServer {

    private static final String TAG = "HermesROOT";
    private final HermesRootPowers root;

    public HermesRootServer() {
        this.root = new HermesRootPowers();
    }

    public HermesRootPowers getRoot() {
        return root;
    }

    /**
     * Check if root is available
     */
    public JSONObject checkRoot() {
        return root.su("echo ROOT_OK");
    }

    /**
     * Handle root-specific routes. Returns null if not a root route.
     */
    public NanoHTTPD.Response handleRoute(String uri, NanoHTTPD.Method method,
                                            HermesBodyServer.RequestData requestData) {
        try {
            // Only POST root commands
            if (NanoHTTPD.Method.POST.equals(method)) {
                JSONObject body = requestData.getBody();

                switch (uri) {
                    // Instant touch (no accessibility delay)
                    case "/root/tap":
                        return jsonify(root.rootTap(body.optInt("x", -1), body.optInt("y", -1)));
                    case "/root/swipe":
                        return jsonify(root.rootSwipe(
                            body.optInt("x1", 0), body.optInt("y1", 0),
                            body.optInt("x2", 0), body.optInt("y2", 0),
                            body.optInt("duration", 300)));
                    case "/root/type":
                        return jsonify(root.rootType(body.optString("text", "")));
                    case "/root/press_key":
                        return jsonify(root.rootPressKey(body.optInt("keyCode", 4)));

                    // Screenshot
                    case "/root/screenshot":
                        return jsonify(root.screenshot(body.optString("path", null)));
                    case "/root/screenrecord":
                        return jsonify(root.screenrecord(body.optString("path", null),
                            body.optInt("duration", 10)));

                    // Dumpsys
                    case "/root/dumpsys":
                        return jsonify(root.dumpsys(body.optString("service", "")));
                    case "/root/dumpsys/activity":
                        return jsonify(root.dumpsysActivity());
                    case "/root/dumpsys/battery":
                        return jsonify(root.dumpsysBattery());
                    case "/root/dumpsys/notifications":
                        return jsonify(root.dumpsysNotification());
                    case "/root/dumpsys/wifi":
                        return jsonify(root.dumpsysWifi());
                    case "/root/dumpsys/meminfo":
                        return jsonify(root.dumpsysMeminfo());

                    // App management
                    case "/root/pm/install":
                        return jsonify(root.pmInstall(body.optString("path", "")));
                    case "/root/pm/uninstall":
                        return jsonify(root.pmUninstall(body.optString("package", "")));
                    case "/root/pm/clear":
                        return jsonify(root.pmClear(body.optString("package", "")));
                    case "/root/pm/grant":
                        return jsonify(root.pmGrant(body.optString("package", ""),
                            body.optString("permission", "")));

                    // System settings
                    case "/root/settings/put":
                        return jsonify(root.settingsPut(body.optString("namespace", "system"),
                            body.optString("key", ""), body.optString("value", "")));
                    case "/root/settings/get":
                        return jsonify(root.settingsGet(body.optString("namespace", "system"),
                            body.optString("key", "")));

                    // Network
                    case "/root/wifi/enable":
                        return jsonify(root.wifiEnable());
                    case "/root/wifi/disable":
                        return jsonify(root.wifiDisable());
                    case "/root/data/enable":
                        return jsonify(root.dataEnable());
                    case "/root/data/disable":
                        return jsonify(root.dataDisable());
                    case "/root/iptables":
                        return jsonify(root.iptables(body.optString("rule", "")));
                    case "/root/tcpdump":
                        return jsonify(root.tcpdump(body.optString("args", ""),
                            body.optInt("duration", 10)));

                    // App data
                    case "/root/appdata/read":
                        return jsonify(root.readAppData(body.optString("package", ""),
                            body.optString("file", "")));
                    case "/root/appdata/list":
                        return jsonify(root.listAppData(body.optString("package", "")));
                    case "/root/appdata/db":
                        return jsonify(root.readAppDB(body.optString("package", ""),
                            body.optString("db", ""), body.optString("query", "")));

                    // Processes
                    case "/root/ps":
                        return jsonify(root.psList());
                    case "/root/kill":
                        return jsonify(root.killProcess(body.optInt("pid", -1)));
                    case "/root/force-stop":
                        return jsonify(root.killPackage(body.optString("package", "")));

                    // Logcat
                    case "/root/logcat":
                        return jsonify(root.logcat(body.optInt("lines", 100)));
                    case "/root/logcat/tag":
                        return jsonify(root.logcatTag(body.optString("tag", "HermesBody"),
                            body.optInt("lines", 100)));

                    // System props
                    case "/root/getprop":
                        return jsonify(root.getProp(body.optString("name", "")));
                    case "/root/setprop":
                        return jsonify(root.setProp(body.optString("name", ""),
                            body.optString("value", "")));

                    // Power
                    case "/root/reboot":
                        return jsonify(root.reboot());
                    case "/root/reboot/recovery":
                        return jsonify(root.rebootRecovery());
                    case "/root/shutdown":
                        return jsonify(root.shutdown());

                    // Files
                    case "/root/file/read":
                        return jsonify(root.readFile(body.optString("path", "")));
                    case "/root/file/write":
                        return jsonify(root.writeFile(body.optString("path", ""),
                            body.optString("content", "")));
                    case "/root/file/list":
                        return jsonify(root.listDir(body.optString("path", "/")));
                    case "/root/file/cp":
                        return jsonify(root.copyFile(body.optString("src", ""),
                            body.optString("dst", "")));

                    // Generic su
                    case "/root/su":
                        return jsonify(root.su(body.optString("command", "echo ROOT_OK")));

                    // Root check
                    case "/root/check":
                        return jsonify(checkRoot());
                }
            }

            // GET root routes
            if (NanoHTTPD.Method.GET.equals(method)) {
                switch (uri) {
                    case "/root/check":
                        return jsonify(checkRoot());
                    case "/root/ps":
                        return jsonify(root.psList());
                    case "/root/logcat":
                        return jsonify(root.logcat(50));
                }
            }

            return null; // Not a root route

        } catch (Exception e) {
            Log.e(TAG, "Root route error", e);
            return null;
        }
    }

    private NanoHTTPD.Response jsonify(JSONObject json) {
        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, "application/json", json.toString());
        r.addHeader("Access-Control-Allow-Origin", "*");
        return r;
    }

    /**
     * Simple container for request data (body + params)
     */
    public static class RequestData {
        private final JSONObject body;
        private final Map<String, String> params;

        public RequestData(JSONObject body, Map<String, String> params) {
            this.body = body;
            this.params = params;
        }

        public JSONObject getBody() { return body; }
        public Map<String, String> getParams() { return params; }
    }
}
