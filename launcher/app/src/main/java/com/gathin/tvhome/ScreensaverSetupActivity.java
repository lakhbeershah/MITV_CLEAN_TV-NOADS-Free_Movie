package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class ScreensaverSetupActivity extends Activity {

    public static final String PREFS_NAME = "screensaver_prefs";
    public static final String KEY_TIMEOUT_MIN = "idle_timeout_min";

    private TextView setupStatus;
    private final int[] btnIds = {R.id.btnTimeout1, R.id.btnTimeout2, R.id.btnTimeout5, R.id.btnTimeout10, R.id.btnTimeoutOff};
    private final int[] timeouts = {1, 2, 5, 10, 0};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screensaver_setup);
        setupStatus = findViewById(R.id.setupStatus);

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int currentTimeout = sp.getInt(KEY_TIMEOUT_MIN, 1);

        for (int i = 0; i < btnIds.length; i++) {
            int t = timeouts[i];
            View b = findViewById(btnIds[i]);
            b.setSelected(currentTimeout == t);
            b.setOnClickListener(v -> {
                sp.edit().putInt(KEY_TIMEOUT_MIN, t).apply();
                for (int j = 0; j < btnIds.length; j++) {
                    findViewById(btnIds[j]).setSelected(timeouts[j] == t);
                }
                Toast.makeText(this, t > 0 ? t + " min timeout" : "Screensaver off", Toast.LENGTH_SHORT).show();
            });
        }

        findViewById(R.id.btnTestScreensaver).setOnClickListener(v ->
                startActivity(new Intent(this, ClockActivity.class)));

        findViewById(R.id.btnSystemScreensaver).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS));
            } catch (Throwable t) {
                Toast.makeText(this, "Not supported", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnRescan).setOnClickListener(v -> refreshStatus());
        refreshStatus();
    }

    private void refreshStatus() {
        File dir = new File(Environment.getExternalStorageDirectory(), "tv_screensaver");
        int count = 0;
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().toLowerCase().endsWith(".jpg")) count++;
                }
            }
        }
        setupStatus.setText("Source: " + (count > 0 ? "/sdcard/tv_screensaver (" + count + " photos)" : "Downloads")
                + " • 720x480 RGB_565 (RAM < 15MB)");
    }
}
