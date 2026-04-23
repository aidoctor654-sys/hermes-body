package com.hermes.body;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * HTTP server — mostek między Termux a telefonem.
 * Wszystkie zmysły agenta dostępne przez localhost:8421.
 */
public class HermesBodyServer extends NanoHTTPD {

    private static final String TAG = "HermesHTTP";

    private final HermesPhoneHome phoneHome;
    private final HermesSenses senses;
    private final HermesUSB usb;
    private final HermesPhoneState phoneState;
    private HermesRootServer rootServer; // null if no root

    public HermesBodyServer(HermesPhoneHome phoneHome, HermesSenses senses,
                           HermesUSB usb, HermesPhoneState phoneState) {
        super(8421);
        this.phoneHome = phoneHome;
        this.senses = senses;
        this.usb = usb;
        this.phoneState = phoneState;

        // Check if root is available and enable root endpoints
        try {
            HermesRootPowers root = new HermesRootPowers();
            JSONObject check = root.su("echo ROOT_OK");
            if (check.optBoolean("success", false) || check.optString("stdout").contains("ROOT_OK")) {
                this.rootServer = new HermesRootServer();
                Log.i(TAG, "ROOT detected — root endpoints ENABLED");
            } else {
                Log.i(TAG, "No root — root endpoints disabled");
            }
        } catch (Exception e) {
            Log.i(TAG, "No root: " + e.getMessage());
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        try {
            // CORS preflight
            if (Method.OPTIONS.equals(method)) {
                return cors(newFixedLengthResponse(Response.Status.OK, "text/plain", ""));
            }

            // Try root routes first (if root available)
            if (rootServer != null && uri.startsWith("/root/")) {
                RequestData rd = new RequestData(
                    Method.POST.equals(method) ? parseBody(session) : new JSONObject(),
                    session.getParms());
                Response rootResp = rootServer.handleRoute(uri, method, rd);
                if (rootResp != null) return rootResp;
                return err("Unknown root route: " + uri);
            }

            // Health check
            if ("/ping".equals(uri)) {
                return ok(new JSONObject()
                    .put("status", "ok")
                    .put("service", "hermes-body")
                    .put("version", "1.1")
                    .put("accessibility", HermesAccessibilityService.getInstance() != null)
                    .put("notifications", HermesNotificationListener.getInstance() != null));
            }

            // ==============================
            // SCREEN & ACCESSIBILITY
            // ==============================

            if ("/screen/view".equals(uri) && Method.GET.equals(method)) {
                HermesAccessibilityService svc = HermesAccessibilityService.getInstance();
                if (svc == null) return err("Accessibility service not running");
                return ok(svc.dumpScreen());
            }

            if ("/action/find".equals(uri) && Method.GET.equals(method)) {
                HermesAccessibilityService svc = HermesAccessibilityService.getInstance();
                if (svc == null) return err("Accessibility service not running");
                String text = session.getParms().get("text");
                if (text == null) return err("Missing 'text' parameter");
                return ok(new JSONObject().put("found", svc.findNodesByText(text).length())
                    .put("nodes", svc.findNodesByText(text)));
            }

            // ==============================
            // POST ACTIONS
            // ==============================

            if (Method.POST.equals(method)) {
                JSONObject body = parseBody(session);
                return handleAction(uri, body);
            }

            // ==============================
            // GET ROUTES (non-action)
            // ==============================

            // SMS
            if ("/sms/read".equals(uri)) {
                int limit = parseInt(session.getParms().get("limit"), 20);
                return ok(new JSONObject().put("messages", phoneHome.readSMS(limit)));
            }

            // Contacts
            if ("/contacts".equals(uri)) {
                String search = session.getParms().get("search");
                int limit = parseInt(session.getParms().get("limit"), 50);
                return ok(new JSONObject().put("contacts", phoneHome.readContacts(search, limit)));
            }

            // Phone state
            if ("/phone/state".equals(uri)) {
                return ok(phoneState.getStatus());
            }

            if ("/phone/log".equals(uri)) {
                int limit = parseInt(session.getParms().get("limit"), 20);
                return ok(new JSONObject().put("calls", phoneState.getCallLog(limit)));
            }

            // Calendar
            if ("/calendar/events".equals(uri)) {
                int limit = parseInt(session.getParms().get("limit"), 10);
                return ok(new JSONObject().put("events", phoneHome.readCalendar(limit)));
            }

            // Notifications
            if ("/notifications/active".equals(uri)) {
                HermesNotificationListener nl = HermesNotificationListener.getInstance();
                if (nl == null) return err("Notification listener not enabled. Enable in Settings > Notification access.");
                return ok(new JSONObject().put("notifications", nl.getActiveNotifications()));
            }

            if ("/notifications/recent".equals(uri)) {
                HermesNotificationListener nl = HermesNotificationListener.getInstance();
                if (nl == null) return err("Notification listener not enabled");
                int limit = parseInt(session.getParms().get("limit"), 20);
                return ok(new JSONObject().put("notifications", nl.getRecentNotifications(limit)));
            }

            // Device info
            if ("/device/info".equals(uri)) {
                return ok(senses.getDeviceInfo());
            }

            if ("/device/battery".equals(uri)) {
                return ok(senses.getBattery());
            }

            if ("/device/storage".equals(uri)) {
                return ok(senses.getStorage());
            }

            if ("/device/location".equals(uri)) {
                return ok(senses.getLocation());
            }

            if ("/device/wifi".equals(uri)) {
                return ok(senses.getWiFi());
            }

            if ("/device/volume".equals(uri)) {
                return ok(senses.getVolume());
            }

            if ("/device/screen".equals(uri)) {
                return ok(senses.isScreenOn());
            }

            // Media
            if ("/media/photos".equals(uri)) {
                int limit = parseInt(session.getParms().get("limit"), 20);
                return ok(new JSONObject().put("photos", phoneHome.listPhotos(limit)));
            }

            if ("/media/videos".equals(uri)) {
                int limit = parseInt(session.getParms().get("limit"), 20);
                return ok(new JSONObject().put("videos", phoneHome.listVideos(limit)));
            }

            // Apps
            if ("/action/apps".equals(uri)) {
                return ok(new JSONObject().put("apps", phoneHome.listApps()));
            }

            // Clipboard
            if ("/action/clipboard/get".equals(uri)) {
                return ok(senses.getClipboard());
            }

            // USB
            if ("/usb/list".equals(uri)) {
                return ok(new JSONObject().put("devices", usb.listDevices()));
            }

            if ("/usb/status".equals(uri)) {
                return ok(usb.getStatus());
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain",
                "Not found. Try /ping for health check.");

        } catch (Exception e) {
            Log.e(TAG, "Error: " + uri, e);
            return err("Error: " + e.getMessage());
        }
    }

    // Expose parseBody and params for root server
    public static class RequestData {
        public final JSONObject body;
        public final Map<String, String> params;
        public RequestData(JSONObject body, Map<String, String> params) {
            this.body = body; this.params = params;
        }
    }

    private Response handleAction(String uri, JSONObject body) {
        try {
            switch (uri) {
                // Accessibility actions
                case "/action/tap": {
                    HermesAccessibilityService svc = HermesAccessibilityService.getInstance();
                    if (svc == null) return err("Accessibility service not running");
                    int x = body.optInt("x", -1);
                    int y = body.optInt("y", -1);
                    if (x < 0 || y < 0) return err("Missing x or y");
                    return ok(new JSONObject().put("ok", svc.tap(x, y)).put("action", "tap"));
                }

                case "/action/swipe": {
                    HermesAccessibilityService svc = HermesAccessibilityService.getInstance();
                    if (svc == null) return err("Accessibility service not running");
                    int x1 = body.optInt("x1", 0), y1 = body.optInt("y1", 0);
                    int x2 = body.optInt("x2", 0), y2 = body.optInt("y2", 0);
                    int duration = body.optInt("duration", 300);
                    return ok(new JSONObject().put("ok", svc.swipe(x1, y1, x2, y2, duration)).put("action", "swipe"));
                }

                case "/action/type": {
                    HermesAccessibilityService svc = HermesAccessibilityService.getInstance();
                    if (svc == null) return err("Accessibility service not running");
                    String text = body.optString("text", "");
                    if (text.isEmpty()) return err("Missing text");
                    return ok(new JSONObject().put("ok", svc.typeText(text)).put("action", "type"));
                }

                case "/action/smart_click": {
                    HermesAccessibilityService svc = HermesAccessibilityService.getInstance();
                    if (svc == null) return err("Accessibility service not running");
                    String target = body.optString("target", "");
                    if (target.isEmpty()) return err("Missing target");
                    return ok(new JSONObject().put("ok", svc.smartClick(target)).put("action", "smart_click"));
                }

                case "/action/press": {
                    HermesAccessibilityService svc = HermesAccessibilityService.getInstance();
                    if (svc == null) return err("Accessibility service not running");
                    String key = body.optString("key", "");
                    boolean ok = false;
                    switch (key) {
                        case "back": ok = svc.pressBack(); break;
                        case "home": ok = svc.pressHome(); break;
                        case "recents": ok = svc.pressRecents(); break;
                        case "notifications": ok = svc.pressNotifications(); break;
                        case "quick_settings": ok = svc.pressQuickSettings(); break;
                        default: return err("Unknown key: " + key);
                    }
                    return ok(new JSONObject().put("ok", ok).put("action", "press").put("key", key));
                }

                case "/action/open": {
                    HermesAccessibilityService svc = HermesAccessibilityService.getInstance();
                    if (svc == null) return err("Accessibility service not running");
                    String pkg = body.optString("package", "");
                    if (pkg.isEmpty()) return err("Missing package");
                    android.content.Intent launch = svc.getPackageManager().getLaunchIntentForPackage(pkg);
                    if (launch != null) {
                        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        svc.startActivity(launch);
                        return ok(new JSONObject().put("ok", true).put("package", pkg));
                    }
                    return err("Package not found: " + pkg);
                }

                // SMS
                case "/sms/send": {
                    String number = body.optString("number", "");
                    String message = body.optString("message", "");
                    if (number.isEmpty() || message.isEmpty()) return err("Missing number or message");
                    return ok(phoneHome.sendSMS(number, message));
                }

                // Phone
                case "/phone/call": {
                    String number = body.optString("number", "");
                    if (number.isEmpty()) return err("Missing number");
                    return ok(phoneHome.makeCall(number));
                }

                case "/phone/dial": {
                    String number = body.optString("number", "");
                    if (number.isEmpty()) return err("Missing number");
                    return ok(phoneHome.dialNumber(number));
                }

                // Alarm
                case "/alarm/set": {
                    int hour = body.optInt("hour", -1);
                    int minute = body.optInt("minute", -1);
                    if (hour < 0 || minute < 0) return err("Missing hour or minute");
                    return ok(phoneHome.setAlarm(hour, minute, body.optString("message", null)));
                }

                case "/timer/set": {
                    int seconds = body.optInt("seconds", -1);
                    if (seconds < 0) return err("Missing seconds");
                    return ok(phoneHome.setTimer(seconds, body.optString("message", null)));
                }

                // Volume
                case "/device/volume/set": {
                    String stream = body.optString("stream", "music");
                    int level = body.optInt("level", -1);
                    if (level < 0) return err("Missing level");
                    return ok(senses.setVolume(stream, level));
                }

                // Torch
                case "/action/torch": {
                    boolean on = body.optBoolean("on", true);
                    return ok(senses.setTorch(on));
                }

                // TTS
                case "/action/speak": {
                    String text = body.optString("text", "");
                    if (text.isEmpty()) return err("Missing text");
                    return ok(senses.speak(text));
                }

                // Wake screen
                case "/device/wake": {
                    return ok(senses.wakeScreen());
                }

                // Clipboard
                case "/action/clipboard/set": {
                    String text = body.optString("text", "");
                    if (text.isEmpty()) return err("Missing text");
                    return ok(senses.setClipboard(text));
                }

                // USB
                case "/usb/connect": {
                    int deviceId = body.optInt("deviceId", -1);
                    if (deviceId < 0) return err("Missing deviceId");
                    return ok(usb.connect(deviceId));
                }

                case "/usb/disconnect": {
                    return ok(usb.disconnect());
                }

                case "/usb/read": {
                    int timeout = parseInt(body.optString("timeout", "1000"), 1000);
                    return ok(usb.read(timeout));
                }

                case "/usb/write": {
                    String text = body.optString("text", "");
                    if (text.isEmpty()) return err("Missing text");
                    int timeout = parseInt(body.optString("timeout", "1000"), 1000);
                    return ok(usb.write(text, timeout));
                }

                default:
                    return err("Unknown action: " + uri);
            }
        } catch (Exception e) {
            return err("Error: " + e.getMessage());
        }
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private JSONObject parseBody(IHTTPSession session) {
        try {
            Map<String, String> bodyMap = new HashMap<>();
            session.parseBody(bodyMap);
            String body = bodyMap.get("postData");
            if (body != null && !body.isEmpty()) {
                return new JSONObject(body);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing body", e);
        }
        return new JSONObject();
    }

    private int parseInt(String s, int def) {
        try { return s != null ? Integer.parseInt(s) : def; }
        catch (NumberFormatException e) { return def; }
    }

    private Response ok(JSONObject json) {
        Response r = newFixedLengthResponse(Response.Status.OK, "application/json", json.toString());
        r.addHeader("Access-Control-Allow-Origin", "*");
        return r;
    }

    private Response err(String message) {
        try {
            Response r = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
                new JSONObject().put("error", message).toString());
            r.addHeader("Access-Control-Allow-Origin", "*");
            return r;
        } catch (Exception e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", message);
        }
    }

    private Response cors(Response r) {
        r.addHeader("Access-Control-Allow-Origin", "*");
        r.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        r.addHeader("Access-Control-Allow-Headers", "Content-Type");
        return r;
    }
}