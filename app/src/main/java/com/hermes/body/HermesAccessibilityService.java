package com.hermes.body;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Hermes Body - Accessibility Service
 *
 * Daje Hermesowi oczy i ręce na Androidzie.
 * Widzi ekran, klika, pisze, swajpuje — wszystko bez roota.
 * Komunikuje się z Termux przez HTTP na localhost:8421.
 */
public class HermesAccessibilityService extends AccessibilityService {

    private static final String TAG = "HermesBody";
    private static HermesAccessibilityService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.i(TAG, "Hermes Accessibility Service created");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // We read screen on demand, not on every event
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Hermes Accessibility Service interrupted");
    }

    @Override
    public void onDestroy() {
        instance = null;
        Log.i(TAG, "Hermes Accessibility Service destroyed");
        super.onDestroy();
    }

    public static HermesAccessibilityService getInstance() {
        return instance;
    }

    // ================================================================
    // SCREEN READING
    // ================================================================

    public JSONObject dumpScreen() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return new JSONObject();
        }
        try {
            return nodeToJSON(root, 0);
        } catch (Exception e) {
            Log.e(TAG, "Error dumping screen", e);
            return new JSONObject();
        }
    }

    public JSONArray findNodesByText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        JSONArray results = new JSONArray();
        if (root == null) return results;
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
            for (AccessibilityNodeInfo node : nodes) {
                results.put(nodeToBriefJSON(node));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding nodes by text", e);
        }
        return results;
    }

    public boolean smartClick(String targetText) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(targetText);
            for (AccessibilityNodeInfo node : nodes) {
                AccessibilityNodeInfo clickable = findClickableAncestor(node);
                if (clickable != null) {
                    return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                if (node.isClickable()) {
                    return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error smart clicking", e);
        }
        return false;
    }

    public boolean typeText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            AccessibilityNodeInfo focused = findFocusedEditable(root);
            if (focused != null) {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                boolean result = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                if (!result) {
                    focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                    focused.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                return result;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error typing text", e);
        }
        return false;
    }

    // ================================================================
    // GESTURES
    // ================================================================

    public boolean tap(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, 50);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        boolean[] result = {false};
        CountDownLatch latch = new CountDownLatch(1);
        dispatchGesture(gesture, new GestureCallback(latch, result), null);
        try { latch.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) { return false; }
        return result[0];
    }

    public boolean swipe(int x1, int y1, int x2, int y2, int durationMs) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, durationMs);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        boolean[] result = {false};
        CountDownLatch latch = new CountDownLatch(1);
        dispatchGesture(gesture, new GestureCallback(latch, result), null);
        try { latch.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { return false; }
        return result[0];
    }

    // Named inner class to avoid d8 bug with anonymous classes on aarch64
    private static class GestureCallback extends GestureResultCallback {
        private final CountDownLatch latch;
        private final boolean[] result;
        GestureCallback(CountDownLatch latch, boolean[] result) {
            this.latch = latch;
            this.result = result;
        }
        @Override public void onCompleted(GestureDescription desc) { result[0] = true; latch.countDown(); }
        @Override public void onCancelled(GestureDescription desc) { result[0] = false; latch.countDown(); }
    }

    // ================================================================
    // KEY ACTIONS
    // ================================================================

    public boolean pressBack() {
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public boolean pressHome() {
        return performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public boolean pressRecents() {
        return performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    public boolean pressNotifications() {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

    public boolean pressQuickSettings() {
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
    }

    // ================================================================
    // INTERNAL HELPERS
    // ================================================================

    private JSONObject nodeToJSON(AccessibilityNodeInfo node, int depth) throws Exception {
        JSONObject json = new JSONObject();
        if (node == null) return json;

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        json.put("text", node.getText() != null ? node.getText().toString() : "");
        json.put("contentDesc", node.getContentDescription() != null ? node.getContentDescription().toString() : "");
        json.put("className", node.getClassName() != null ? node.getClassName().toString() : "");
        json.put("viewId", node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "");
        json.put("clickable", node.isClickable());
        json.put("editable", node.isEditable());
        json.put("scrollable", node.isScrollable());
        json.put("checked", node.isChecked());
        json.put("enabled", node.isEnabled());
        json.put("focusable", node.isFocusable());
        json.put("focused", node.isFocused());
        json.put("selected", node.isSelected());
        json.put("depth", depth);
        json.put("bounds", new JSONObject()
            .put("left", bounds.left)
            .put("top", bounds.top)
            .put("right", bounds.right)
            .put("bottom", bounds.bottom));

        JSONArray children = new JSONArray();
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                children.put(nodeToJSON(child, depth + 1));
            }
        }
        json.put("children", children);

        return json;
    }

    private JSONObject nodeToBriefJSON(AccessibilityNodeInfo node) throws Exception {
        JSONObject json = new JSONObject();
        if (node == null) return json;

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        json.put("text", node.getText() != null ? node.getText().toString() : "");
        json.put("contentDesc", node.getContentDescription() != null ? node.getContentDescription().toString() : "");
        json.put("viewId", node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "");
        json.put("clickable", node.isClickable());
        json.put("bounds", bounds.left + "," + bounds.top + "," + bounds.right + "," + bounds.bottom);

        return json;
    }

    private AccessibilityNodeInfo findClickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable()) return current;
            current = current.getParent();
        }
        return null;
    }

    private AccessibilityNodeInfo findFocusedEditable(AccessibilityNodeInfo root) {
        if (root == null) return null;
        if (root.isEditable() && root.isFocused()) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findFocusedEditable(child);
                if (found != null) return found;
            }
        }
        return null;
    }
}