package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

/**
 * Manages TV sleep timer countdown and standby trigger.
 */
public class SleepTimerManager {

    private static final String TAG = "SleepTimer";
    private static Handler handler;
    private static Runnable sleepRunnable;
    private static long targetTimeMs = 0;
    private static int selectedMinutes = 0;

    public interface TimerCallback {
        void onTick(int remainingMinutes);
        void onExpired();
    }

    public static synchronized void setTimer(Activity activity, int minutes, Runnable onExpireCallback) {
        cancelTimer();
        selectedMinutes = minutes;
        if (minutes <= 0) {
            return;
        }

        targetTimeMs = System.currentTimeMillis() + (minutes * 60_000L);
        handler = new Handler(Looper.getMainLooper());

        sleepRunnable = new Runnable() {
            @Override
            public void run() {
                targetTimeMs = 0;
                selectedMinutes = 0;
                Log.i(TAG, "Sleep timer expired! Triggering TV sleep...");
                if (onExpireCallback != null) {
                    onExpireCallback.run();
                }
                triggerTvSleep(activity);
            }
        };

        handler.postDelayed(sleepRunnable, minutes * 60_000L);
        Toast.makeText(activity, "TV will sleep in " + minutes + " minutes", Toast.LENGTH_SHORT).show();
    }

    public static synchronized int getSelectedMinutes() {
        return selectedMinutes;
    }

    public static synchronized int getRemainingMinutes() {
        if (targetTimeMs <= 0) return 0;
        long diff = targetTimeMs - System.currentTimeMillis();
        return diff > 0 ? (int) Math.ceil(diff / 60_000.0) : 0;
    }

    public static synchronized void cancelTimer() {
        if (handler != null && sleepRunnable != null) {
            handler.removeCallbacks(sleepRunnable);
        }
        handler = null;
        sleepRunnable = null;
        targetTimeMs = 0;
        selectedMinutes = 0;
    }

    private static void triggerTvSleep(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        try {
            // Send Power keyevent to enter standby
            new Thread(() -> {
                try {
                    Runtime.getRuntime().exec("input keyevent 26");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to exec power keyevent", e);
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "Error triggering sleep", e);
        }
    }
}
