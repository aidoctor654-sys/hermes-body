package com.hermes.body;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbConstants;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Hermes USB — bezpośredni dostęp do USB Devices i Accessories.
 * Odczytuje Arduino, ESP32, drukarki, serial adapters, cokolwiek na USB OTG.
 * Dwukierunkowa komunikacja: read + write.
 */
public class HermesUSB {

    private static final String TAG = "HermesUSB";
    private static final String ACTION_USB_PERMISSION = "com.hermes.body.USB_PERMISSION";
    private static HermesUSB instance;

    private final Context context;
    private final UsbManager usbManager;
    private UsbDeviceConnection activeConnection;
    private UsbInterface activeInterface;
    private UsbEndpoint endpointIn;  // device → phone
    private UsbEndpoint endpointOut; // phone → device
    private UsbDevice activeDevice;

    public HermesUSB(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        instance = this;

        // Register USB permission receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        context.registerReceiver(usbReceiver, filter);
    }

    public static HermesUSB getInstance() {
        return instance;
    }

    // ================================================================
    // LIST DEVICES
    // ================================================================

    public JSONArray listDevices() {
        JSONArray devices = new JSONArray();
        try {
            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            Iterator<Map.Entry<String, UsbDevice>> it = deviceList.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, UsbDevice> entry = it.next();
                UsbDevice dev = entry.getValue();
                JSONObject d = deviceToJSON(dev);
                d.put("hasPermission", usbManager.hasPermission(dev));
                devices.put(d);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error listing USB devices", e);
        }
        return devices;
    }

    // ================================================================
    // CONNECT / DISCONNECT
    // ================================================================

    public JSONObject connect(int deviceId) {
        JSONObject result = new JSONObject();
        try {
            UsbDevice device = findDeviceById(deviceId);
            if (device == null) {
                result.put("error", "device not found");
                return result;
            }

            if (!usbManager.hasPermission(device)) {
                // Request permission — user must accept on screen
                PendingIntent pi = PendingIntent.getBroadcast(context, 0,
                    new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
                usbManager.requestPermission(device, pi);
                result.put("status", "permission_requested");
                result.put("deviceId", deviceId);
                return result;
            }

            // Find first interface with bulk endpoints
            UsbInterface intf = findReadWriteInterface(device);
            if (intf == null) {
                result.put("error", "no read/write interface found");
                return result;
            }

            UsbDeviceConnection conn = usbManager.openDevice(device);
            if (conn == null) {
                result.put("error", "could not open device");
                return result;
            }

            if (!conn.claimInterface(intf, true)) {
                conn.close();
                result.put("error", "could not claim interface");
                return result;
            }

            // Store active connection
            disconnect(); // close previous
            activeConnection = conn;
            activeInterface = intf;
            activeDevice = device;

            endpointIn = null;
            endpointOut = null;
            for (int i = 0; i < intf.getEndpointCount(); i++) {
                UsbEndpoint ep = intf.getEndpoint(i);
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) endpointIn = ep;
                else if (ep.getDirection() == UsbConstants.USB_DIR_OUT) endpointOut = ep;
            }

            result.put("connected", true);
            result.put("device", device.getDeviceName());
            result.put("vendor", device.getVendorId());
            result.put("product", device.getProductId());
            result.put("hasRead", endpointIn != null);
            result.put("hasWrite", endpointOut != null);

            Log.i(TAG, "USB connected: " + device.getDeviceName());

        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ex) {}
        }
        return result;
    }

    public JSONObject disconnect() {
        JSONObject result = new JSONObject();
        try {
            if (activeConnection != null) {
                if (activeInterface != null) {
                    activeConnection.releaseInterface(activeInterface);
                }
                activeConnection.close();
                Log.i(TAG, "USB disconnected");
            }
            activeConnection = null;
            activeInterface = null;
            endpointIn = null;
            endpointOut = null;
            activeDevice = null;
            result.put("disconnected", true);
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ex) {}
        }
        return result;
    }

    // ================================================================
    // READ / WRITE — dwukierunkowa komunikacja
    // ================================================================

    public JSONObject read(int timeoutMs) {
        JSONObject result = new JSONObject();
        try {
            if (activeConnection == null || endpointIn == null) {
                result.put("error", "not connected or no read endpoint");
                return result;
            }

            byte[] buffer = new byte[4096];
            int bytesRead = activeConnection.bulkTransfer(endpointIn, buffer, buffer.length, timeoutMs);

            if (bytesRead > 0) {
                byte[] data = new byte[bytesRead];
                System.arraycopy(buffer, 0, data, 0, bytesRead);

                // Try to interpret as text
                String text = new String(data, "UTF-8");
                result.put("bytesRead", bytesRead);
                result.put("text", text);
                result.put("hex", bytesToHex(data));
                result.put("raw", data);
            } else {
                result.put("bytesRead", 0);
                result.put("timeout", true);
            }
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ex) {}
        }
        return result;
    }

    public JSONObject write(String text, int timeoutMs) {
        JSONObject result = new JSONObject();
        try {
            if (activeConnection == null || endpointOut == null) {
                result.put("error", "not connected or no write endpoint");
                return result;
            }

            byte[] data = text.getBytes("UTF-8");
            int bytesWritten = activeConnection.bulkTransfer(endpointOut, data, data.length, timeoutMs);
            result.put("bytesWritten", bytesWritten);
            result.put("text", text);
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ex) {}
        }
        return result;
    }

    public JSONObject writeBytes(byte[] data, int timeoutMs) {
        JSONObject result = new JSONObject();
        try {
            if (activeConnection == null || endpointOut == null) {
                result.put("error", "not connected or no write endpoint");
                return result;
            }
            int bytesWritten = activeConnection.bulkTransfer(endpointOut, data, data.length, timeoutMs);
            result.put("bytesWritten", bytesWritten);
            result.put("hex", bytesToHex(data));
        } catch (Exception e) {
            try { result.put("error", e.getMessage()); } catch (Exception ex) {}
        }
        return result;
    }

    // ================================================================
    // STATUS
    // ================================================================

    public JSONObject getStatus() {
        JSONObject result = new JSONObject();
        try {
            result.put("connected", activeConnection != null);
            if (activeDevice != null) {
                result.put("device", deviceToJSON(activeDevice));
                result.put("hasRead", endpointIn != null);
                result.put("hasWrite", endpointOut != null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting USB status", e);
        }
        return result;
    }

    // ================================================================
    // INTERNAL
    // ================================================================

    private UsbDevice findDeviceById(int deviceId) {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (UsbDevice dev : deviceList.values()) {
            if (dev.getDeviceId() == deviceId) return dev;
        }
        return null;
    }

    private UsbInterface findReadWriteInterface(UsbDevice device) {
        // Try to find interface with both IN and OUT bulk endpoints
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            boolean hasIn = false, hasOut = false;
            for (int j = 0; j < intf.getEndpointCount(); j++) {
                UsbEndpoint ep = intf.getEndpoint(j);
                if (ep.getDirection() == UsbConstants.USB_DIR_IN && ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) hasIn = true;
                if (ep.getDirection() == UsbConstants.USB_DIR_OUT && ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) hasOut = true;
            }
            if (hasIn && hasOut) return intf;
        }
        // Fallback: return first interface
        if (device.getInterfaceCount() > 0) return device.getInterface(0);
        return null;
    }

    private JSONObject deviceToJSON(UsbDevice dev) throws Exception {
        JSONObject d = new JSONObject();
        d.put("deviceId", dev.getDeviceId());
        d.put("deviceName", dev.getDeviceName());
        d.put("vendorId", dev.getVendorId());
        d.put("productId", dev.getProductId());
        d.put("interfaceCount", dev.getInterfaceCount());

        String cls = "unknown";
        switch (dev.getDeviceClass()) {
            case UsbConstants.USB_CLASS_AUDIO: cls = "audio"; break;
            case UsbConstants.USB_CLASS_COMM: cls = "serial/comm"; break;
            case UsbConstants.USB_CLASS_HID: cls = "hid"; break;
            case UsbConstants.USB_CLASS_MASS_STORAGE: cls = "storage"; break;
            case UsbConstants.USB_CLASS_HUB: cls = "hub"; break;
            case UsbConstants.USB_CLASS_VENDOR_SPEC: cls = "vendor"; break;
            case UsbConstants.USB_CLASS_PRINTER: cls = "printer"; break;
        }
        d.put("class", cls);

        // Interfaces
        JSONArray ifaces = new JSONArray();
        for (int i = 0; i < dev.getInterfaceCount(); i++) {
            UsbInterface intf = dev.getInterface(i);
            JSONObject ifaceJSON = new JSONObject();
            ifaceJSON.put("id", intf.getId());
            ifaceJSON.put("name", intf.getName());
            ifaceJSON.put("endpointCount", intf.getEndpointCount());

            JSONArray eps = new JSONArray();
            for (int j = 0; j < intf.getEndpointCount(); j++) {
                UsbEndpoint ep = intf.getEndpoint(j);
                JSONObject epJSON = new JSONObject();
                epJSON.put("address", ep.getAddress());
                epJSON.put("direction", ep.getDirection() == UsbConstants.USB_DIR_IN ? "IN" : "OUT");
                epJSON.put("type", ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK ? "bulk" :
                    ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT ? "interrupt" :
                    ep.getType() == UsbConstants.USB_ENDPOINT_XFER_ISOC ? "isochronous" : "control");
                epJSON.put("maxPacketSize", ep.getMaxPacketSize());
                eps.put(epJSON);
            }
            ifaceJSON.put("endpoints", eps);
            ifaces.put(ifaceJSON);
        }
        d.put("interfaces", ifaces);

        return d;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // USB Event receiver (named class to avoid d8 bug)
    private static class UsbEventReceiver extends BroadcastReceiver {
        private final HermesUSB host;
        UsbEventReceiver(HermesUSB host) { this.host = host; }
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Log.i(TAG, "USB permission " + (granted ? "granted" : "denied") +
                    " for " + (device != null ? device.getDeviceName() : "?"));
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.i(TAG, "USB device attached");
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.i(TAG, "USB device detached");
                host.disconnect();
            }
        }
    }

    private final BroadcastReceiver usbReceiver = new UsbEventReceiver(this);
}