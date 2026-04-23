package com.hermes.body;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Minimal UI — status and buttons to enable accessibility and start server.
 */
public class MainActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        // Title
        TextView title = new TextView(this);
        title.setText("🤖 Hermes Body");
        title.setTextSize(24);
        title.setPadding(0, 0, 0, 24);
        layout.addView(title);

        // Status
        status = new TextView(this);
        updateStatus();
        status.setTextSize(16);
        status.setPadding(0, 0, 0, 16);
        layout.addView(status);

        // Enable Accessibility button
        Button enableBtn = new Button(this);
        enableBtn.setText("⚡ Enable Accessibility Service");
        enableBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
        layout.addView(enableBtn);

        // Start HTTP Server button
        Button startBtn = new Button(this);
        startBtn.setText("🌐 Start HTTP Server (port 8421)");
        startBtn.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(this, HttpServerService.class);
                startForegroundService(intent);
                status.setText("⏳ Server starting... check notification bar");
                // Check after 2 seconds
                v.postDelayed(() -> {
                    try {
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                            new java.net.URL("http://127.0.0.1:8421/ping").openConnection();
                        conn.setConnectTimeout(2000);
                        conn.setReadTimeout(2000);
                        conn.setRequestMethod("GET");
                        int code = conn.getResponseCode();
                        java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close();
                        conn.disconnect();
                        status.setText("✅ SERVER RUNNING!\n" + sb.toString());
                    } catch (Exception e) {
                        status.setText("❌ Server not responding: " + e.getMessage() +
                            "\nCheck if HttpServerService crashed. Check logcat.");
                    }
                }, 2000);
            } catch (Exception e) {
                status.setText("❌ Error starting service: " + e.getMessage());
                android.util.Log.e("HermesBody", "startForegroundService failed", e);
            }
        });
        layout.addView(startBtn);

        // Stop HTTP Server button
        Button stopBtn = new Button(this);
        stopBtn.setText("🛑 Stop HTTP Server");
        stopBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, HttpServerService.class);
            stopService(intent);
            status.setText("Server stopped.");
        });
        layout.addView(stopBtn);

        // Test: read screen
        Button testBtn = new Button(this);
        testBtn.setText("🔍 Test: Read Screen");
        testBtn.setOnClickListener(v -> {
            HermesAccessibilityService svc = HermesAccessibilityService.getInstance();
            if (svc != null) {
                try {
                    String dump = svc.dumpScreen().toString();
                    String preview = dump.substring(0, Math.min(300, dump.length()));
                    status.setText("✅ Screen readable!\n" + preview);
                } catch (Exception e) {
                    status.setText("❌ Screen read error: " + e.getMessage());
                }
            } else {
                status.setText("❌ Accessibility service not running!");
            }
        });
        layout.addView(testBtn);

        // Test: ping server
        Button pingBtn = new Button(this);
        pingBtn.setText("📡 Test: Ping Server");
        pingBtn.setOnClickListener(v -> {
            new Thread(() -> {
                try {
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                        new java.net.URL("http://127.0.0.1:8421/ping").openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    conn.disconnect();
                    String result = sb.toString();
                    runOnUiThread(() -> status.setText("✅ Pong: " + result));
                } catch (Exception e) {
                    runOnUiThread(() -> status.setText("❌ Ping failed: " + e.getMessage()));
                }
            }).start();
        });
        layout.addView(pingBtn);

        // Info
        TextView info = new TextView(this);
        info.setText("\nHermes Body daje agentowi Hermes zdolność " +
            "widzenia ekranu i interakcji z aplikacjami.\n\n" +
            "1. Włącz Accessibility Service w ustawieniach\n" +
            "2. Kliknij Start HTTP Server\n" +
            "3. Hermes: curl localhost:8421/screen/view\n\n" +
            "Endpoints:\n" +
            "  GET  /ping\n" +
            "  GET  /screen/view\n" +
            "  GET  /action/find?text=...\n" +
            "  POST /action/tap {x,y}\n" +
            "  POST /action/swipe {x1,y1,x2,y2,duration}\n" +
            "  POST /action/type {text}\n" +
            "  POST /action/smart_click {target}\n" +
            "  POST /action/press {key}\n" +
            "  POST /action/open {package}");
        info.setTextSize(13);
        info.setPadding(0, 24, 0, 0);
        layout.addView(info);

        scrollView.addView(layout);
        setContentView(scrollView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        HermesAccessibilityService svc = HermesAccessibilityService.getInstance();
        if (svc != null) {
            status.setText("✅ Accessibility: AKTYWNY\nHermes ma oczy i ręce!");
        } else {
            status.setText("❌ Accessibility: NIEAKTYWNY\nWłącz w Settings → Accessibility");
        }
    }
}