package com.hermes.body;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Minimal UI — just status and buttons to enable accessibility and start server.
 */
public class MainActivity extends Activity {

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
        TextView status = new TextView(this);
        status.setId(android.R.id.text1);
        updateStatus(status);
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
            Intent intent = new Intent(this, HttpServerService.class);
            startForegroundService(intent);
            updateStatus(status);
        });
        layout.addView(startBtn);

        // Stop HTTP Server button
        Button stopBtn = new Button(this);
        stopBtn.setText("🛑 Stop HTTP Server");
        stopBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, HttpServerService.class);
            stopService(intent);
            updateStatus(status);
        });
        layout.addView(stopBtn);

        // Test button
        Button testBtn = new Button(this);
        testBtn.setText("🔍 Test: Read Screen");
        testBtn.setOnClickListener(v -> {
            HermesAccessibilityService svc = HermesAccessibilityService.getInstance();
            if (svc != null) {
                try {
                    android.util.Log.i("HermesBody", "Screen dump: " + svc.dumpScreen().toString().substring(0, Math.min(200, svc.dumpScreen().toString().length())));
                    status.setText("✅ Screen readable! Accessibility is working.");
                } catch (Exception e) {
                    status.setText("❌ Error: " + e.getMessage());
                }
            } else {
                status.setText("❌ Accessibility service not running. Enable it first!");
            }
        });
        layout.addView(testBtn);

        // Info
        TextView info = new TextView(this);
        info.setText("\nHermes Body daje agentowi Hermes zdolność " +
            "widzenia ekranu i interakcji z aplikacjami.\n\n" +
            "1. Włącz Accessibility Service w ustawieniach\n" +
            "2. Kliknij Start HTTP Server\n" +
            "3. Hermes może teraz używać: curl localhost:8421/screen/view\n\n" +
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
        TextView status = findViewById(android.R.id.text1);
        if (status != null) updateStatus(status);
    }

    private void updateStatus(TextView status) {
        HermesAccessibilityService svc = HermesAccessibilityService.getInstance();
        if (svc != null) {
            status.setText("✅ Accessibility Service: AKTYWNY\nHermes ma oczy i ręce!");
        } else {
            status.setText("❌ Accessibility Service: NIEAKTYWNY\nWłącz w Settings → Accessibility");
        }
    }
}