package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Ultra-lightweight Network Time & Epoch Calibration Engine.
 * Fetches accurate atomic time via minimal 100-byte HTTP HEAD request with 0 payload.
 * Fallback to monotonic elapsed realtime if offline, preventing TV hardware clock 2019 reset.
 */
public class NetworkTimeSyncEngine {

    private static final String TAG = "NetworkTimeSync";
    private static volatile boolean isSyncing = false;
    private static long lastSyncTimeMs = 0L;

    public static void syncTimeAsync(Context context) {
        if (context == null) return;

        // Skip if synced within the last 10 minutes
        long now = SystemClock.elapsedRealtime();
        if (now - lastSyncTimeMs < 10 * 60 * 1000L && lastSyncTimeMs > 0) {
            return;
        }
        if (isSyncing) return;

        new Thread(() -> {
            isSyncing = true;
            try {
                long serverTime = fetchNetworkTime();
                if (serverTime > 1735689600000L) { // Valid time > Jan 1, 2025
                    long systemNow = System.currentTimeMillis();
                    long offset = serverTime - systemNow;

                    SharedPreferences sp = context.getSharedPreferences(ClockHelper.PREFS_NAME, Context.MODE_PRIVATE);
                    sp.edit()
                            .putLong("time_offset_ms", offset)
                            .putLong("last_known_good_time_ms", serverTime)
                            .putLong("last_saved_elapsed_realtime", SystemClock.elapsedRealtime())
                            .apply();

                    lastSyncTimeMs = SystemClock.elapsedRealtime();
                    Log.i(TAG, "Network time synchronized! Offset: " + offset + "ms, Server Time: " + serverTime);

                    // Re-render UI on main thread
                    if (context instanceof MainActivity) {
                        ((MainActivity) context).runOnUiThread(((MainActivity) context)::applyHomeClockSettings);
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "Network time fetch failed: " + t.getMessage());
            } finally {
                isSyncing = false;
            }
        }).start();
    }

    private static long fetchNetworkTime() {
        String[] endpoints = {
                "https://www.google.com",
                "https://cloudflare.com",
                "https://www.gstatic.com/generate_204"
        };

        for (String endpoint : endpoints) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(endpoint);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(3500);
                conn.setReadTimeout(3500);
                conn.setInstanceFollowRedirects(false);
                conn.connect();

                long date = conn.getDate();
                if (date > 1735689600000L) { // Valid time > Jan 1, 2025
                    return date;
                }
            } catch (Throwable ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return 0L;
    }
}
