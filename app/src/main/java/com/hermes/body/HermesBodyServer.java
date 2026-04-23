package com.hermes.body;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * HTTP server for Hermes Body.
 * Runs on localhost:8421 and bridges Termux to Accessibility Service.
 */
public class HermesBodyServer extends NanoHTTPD {

    private static final String TAG = "HermesHTTP";

    public HermesBodyServer() {
        super(8421);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        try {
            // CORS
            if (Method.OPTIONS.equals(method)) {
                Response r = newFixedLengthResponse(Response.Status.OK, "text/plain", "");
                r.addHeader("Access-Control-Allow-Origin", "*");
                r.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                r.addHeader("Access-Control-Allow-Headers", "Content-Type");
                return r;
            }

            // Health check
            if ("/ping".equals(uri)) {
                return ok(new JSONObject()
                    .put("status", "ok")
                    .put("service", "hermes-body")
                    .put("accessibility", HermesAccessibilityService.getInstance() != null));
            }

            // Screen reading
            if ("/screen/view".equals(uri) && Method.GET.equals(method)) {
                HermesAccessibilityService svc = HermesAccessibilityService.getInstance();
                if (svc == null) {
                    return err("Accessibility service not running. Enable in Settings.");
                }
                return ok(svc.dumpScreen());
            }

            // Find nodes
            if ("/action/find".equals(uri) && Method.GET.equals(method)) {
                HermesAccessibilityService svc = HermesAccessibilityService.getInstance();
                if (svc == null) return err("Accessibility service not running");
                Map<String, String> params = session.getParms();
                String text = params.get("text");
                if (text == null) return err("Missing 'text' parameter");
                JSONArray results = svc.findNodesByText(text);
                return ok(new JSONObject().put("found", results.length()).put("nodes", results));
            }

            // Actions (POST)
            if (Method.POST.equals(method)) {
                JSONObject body = parseBody(session);
                return handleAction(uri, body);
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");

        } catch (Exception e) {
            Log.e(TAG, "Error handling request: " + uri, e);
            try {
                return err("Error: " + e.getMessage());
            } catch (Exception e2) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error");
            }
        }
    }

    private Response handleAction(String uri, JSONObject body) {
        HermesAccessibilityService svc = HermesAccessibilityService.getInstance();

        try {
            switch (uri) {
                case "/action/tap": {
                    if (svc == null) return err("Accessibility service not running");
                    int x = body.optInt("x", -1);
                    int y = body.optInt("y", -1);
                    if (x < 0 || y < 0) return err("Missing x or y");
                    boolean ok = svc.tap(x, y);
                    return ok(new JSONObject().put("ok", ok).put("action", "tap").put("x", x).put("y", y));
                }

                case "/action/swipe": {
                    if (svc == null) return err("Accessibility service not running");
                    int x1 = body.optInt("x1", 0);
                    int y1 = body.optInt("y1", 0);
                    int x2 = body.optInt("x2", 0);
                    int y2 = body.optInt("y2", 0);
                    int duration = body.optInt("duration", 300);
                    boolean ok = svc.swipe(x1, y1, x2, y2, duration);
                    return ok(new JSONObject().put("ok", ok).put("action", "swipe"));
                }

                case "/action/type": {
                    if (svc == null) return err("Accessibility service not running");
                    String text = body.optString("text", "");
                    if (text.isEmpty()) return err("Missing text");
                    boolean ok = svc.typeText(text);
                    return ok(new JSONObject().put("ok", ok).put("action", "type"));
                }

                case "/action/smart_click": {
                    if (svc == null) return err("Accessibility service not running");
                    String target = body.optString("target", "");
                    if (target.isEmpty()) return err("Missing target");
                    boolean ok = svc.smartClick(target);
                    return ok(new JSONObject().put("ok", ok).put("action", "smart_click").put("target", target));
                }

                case "/action/press": {
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
                    String packageName = body.optString("package", "");
                    if (packageName.isEmpty()) return err("Missing package");
                    android.content.Intent launchIntent = svc.getPackageManager().getLaunchIntentForPackage(packageName);
                    if (launchIntent != null) {
                        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        svc.startActivity(launchIntent);
                        return ok(new JSONObject().put("ok", true).put("action", "open").put("package", packageName));
                    } else {
                        return err("Package not found: " + packageName);
                    }
                }

                default:
                    return err("Unknown action: " + uri);
            }
        } catch (Exception e) {
            return err("Error: " + e.getMessage());
        }
    }

    private JSONObject parseBody(IHTTPSession session) throws Exception {
        Map<String, String> bodyMap = new HashMap<>();
        session.parseBody(bodyMap);
        String body = bodyMap.get("postData");
        if (body != null && !body.isEmpty()) {
            return new JSONObject(body);
        }
        return new JSONObject();
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
}