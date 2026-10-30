package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/**
 * Full-screen ambient photo screensaver with clock and seconds,
 * shown after idle timeout or launched immediately via quick button.
 * Any key immediately wakes the TV and returns to Home.
 */
public class ClockActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private PhotoSlider photoSlider;
    private View dreamContent;

    private final Runnable drift = new Runnable() {
        @Override
        public void run() {
            if (dreamContent != null) {
                float density = getResources().getDisplayMetrics().density;
                float dx = (random.nextFloat() - 0.5f) * 20 * density;
                float dy = (random.nextFloat() - 0.5f) * 16 * density;
                dreamContent.animate().translationX(dx).translationY(dy).setDuration(3500).start();
            }
            handler.postDelayed(this, 60_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dream_clock);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE);
        photoSlider = findViewById(R.id.dreamPhotoSlider);
        if (photoSlider != null) {
            photoSlider.setClickable(false);
            photoSlider.setFocusable(false);
            photoSlider.setOnClickListener(null);
        }
        dreamContent = findViewById(R.id.dreamContent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (photoSlider != null) {
            photoSlider.reloadPhotos();
            photoSlider.startLoop();
        }
        handler.removeCallbacks(drift);
        handler.postDelayed(drift, 30_000L);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacksAndMessages(null);
        if (photoSlider != null) photoSlider.stopLoop();
        super.onPause();
        finish();   // never left behind in the back stack
    }

    private boolean exiting = false;

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Consume both DOWN and UP; finish on UP so the key never leaks to Home
        if (event.getAction() == KeyEvent.ACTION_UP && !exiting) {
            exiting = true;
            finish();
        }
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        finish();
        return true;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
