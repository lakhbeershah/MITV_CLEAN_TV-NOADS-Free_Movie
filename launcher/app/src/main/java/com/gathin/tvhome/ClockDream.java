package com.gathin.tvhome;

import android.service.dreams.DreamService;
import android.view.View;

/**
 * Screensaver: full-screen ambient photo reel from tv_screensaver with clock and seconds.
 */
public class ClockDream extends DreamService {

    private PhotoSlider photoSlider;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);
        ClockHelper.applyScreensaverClock(this, findViewById(R.id.clock), findViewById(R.id.date), findViewById(R.id.analogClock), findViewById(R.id.dreamContent));
        photoSlider = findViewById(R.id.dreamPhotoSlider);
        if (photoSlider != null) {
            photoSlider.setClickable(false);
            photoSlider.setFocusable(false);
            photoSlider.setOnClickListener(null);
        }
        View scrim = findViewById(R.id.dreamDimmingScrim);
        if (scrim != null) {
            int dimPercent = getSharedPreferences("home_custom_prefs", MODE_PRIVATE)
                    .getInt("screensaver_dim_percent", 35);
            scrim.setAlpha(dimPercent / 100f);
        }
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (photoSlider != null) {
            photoSlider.reloadPhotos();
            photoSlider.startLoop();
        }
    }

    @Override
    public void onDreamingStopped() {
        if (photoSlider != null) photoSlider.stopLoop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (photoSlider != null) photoSlider.stopLoop();
        super.onDetachedFromWindow();
    }
}
