package com.bunty.audiosetup;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private TextView txtActiveStatus;
    private GridLayout gridPresets;
    private LinearLayout layoutCustom;
    private LinearLayout layoutSliders;
    private Button btnToggleNight;
    private Button btnToggleDrc;
    private Button btnToggleSurround;

    private boolean customNightMode;
    private int customDrcMode;
    private boolean customSurround;
    private int[] customEqBands = new int[5];

    private final String[] bandNames = new String[] {
            "100 Hz (Bass / Resonance)",
            "500 Hz (Low-Mids / Boxiness)",
            "1.5 kHz (Core Dialogue)",
            "5.0 kHz (Presence / Crispness)",
            "10.0 kHz (Treble / Air Sparkle)"
    };

    private final List<TextView> sliderValueTexts = new ArrayList<>();
    private final List<View> presetCardViews = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtActiveStatus = findViewById(R.id.txt_active_status);
        gridPresets = findViewById(R.id.grid_presets);
        layoutCustom = findViewById(R.id.layout_custom);
        layoutSliders = findViewById(R.id.layout_sliders);
        btnToggleNight = findViewById(R.id.btn_toggle_night);
        btnToggleDrc = findViewById(R.id.btn_toggle_drc);
        btnToggleSurround = findViewById(R.id.btn_toggle_surround);

        Button btnPlaySample = findViewById(R.id.btn_play_sample);
        Button btnToggleCustom = findViewById(R.id.btn_toggle_custom);
        Button btnApplyCustom = findViewById(R.id.btn_apply_custom);

        loadCustomState();
        setupPresetsGrid();
        setupCustomSliders();

        btnPlaySample.setOnClickListener(v -> {
            AudioController.playTestSample();
            Toast.makeText(this, "Playing sound test...", Toast.LENGTH_SHORT).show();
        });

        btnToggleCustom.setOnClickListener(v -> {
            if (layoutCustom.getVisibility() == View.VISIBLE) {
                layoutCustom.setVisibility(View.GONE);
            } else {
                layoutCustom.setVisibility(View.VISIBLE);
                layoutCustom.requestFocus();
            }
        });

        btnToggleNight.setOnClickListener(v -> {
            customNightMode = !customNightMode;
            updateToggleButtonsText();
        });

        btnToggleDrc.setOnClickListener(v -> {
            customDrcMode = (customDrcMode == 1) ? 0 : 1;
            updateToggleButtonsText();
        });

        btnToggleSurround.setOnClickListener(v -> {
            customSurround = !customSurround;
            updateToggleButtonsText();
        });

        btnApplyCustom.setOnClickListener(v -> {
            AudioController.applyCustom(this, customNightMode, customDrcMode, customSurround, customEqBands);
            updateActiveStatusDisplay("CUSTOM USER PROFILE");
            highlightSelectedCard(null);
            Toast.makeText(this, "Custom audio profile applied!", Toast.LENGTH_SHORT).show();
        });

        updateToggleButtonsText();
        updateActiveStatusDisplay(getPresetTitleById(AudioController.getActivePresetId(this)));
    }

    private void loadCustomState() {
        customNightMode = AudioController.getSavedNightMode(this);
        customDrcMode = AudioController.getSavedDrcMode(this);
        customSurround = AudioController.getSavedSurround(this);
        customEqBands = AudioController.getSavedEqBands(this);
    }

    private void setupPresetsGrid() {
        gridPresets.removeAllViews();
        presetCardViews.clear();

        AudioPreset[] presets = AudioPreset.getAllPresets();
        String currentId = AudioController.getActivePresetId(this);

        for (AudioPreset preset : presets) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setFocusable(true);
            card.setClickable(true);
            card.setBackgroundResource(R.drawable.card_selector);
            card.setPadding(20, 20, 20, 20);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(10, 10, 10, 10);
            card.setLayoutParams(params);

            // Icon + Title
            TextView title = new TextView(this);
            title.setText(preset.icon + "  " + preset.title);
            title.setTextColor(Color.WHITE);
            title.setTextSize(17f);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            card.addView(title);

            // Description
            TextView desc = new TextView(this);
            desc.setText(preset.description);
            desc.setTextColor(Color.parseColor("#99FFFFFF"));
            desc.setTextSize(12f);
            desc.setPadding(0, 8, 0, 8);
            card.addView(desc);

            // Status Indicator
            TextView indicator = new TextView(this);
            indicator.setTag("indicator");
            indicator.setTextSize(11f);
            indicator.setTypeface(null, android.graphics.Typeface.BOLD);
            if (preset.id.equals(currentId)) {
                indicator.setText("● ACTIVE");
                indicator.setTextColor(Color.parseColor("#00E676"));
                card.setSelected(true);
            } else {
                indicator.setText("○ SELECT");
                indicator.setTextColor(Color.parseColor("#66FFFFFF"));
                card.setSelected(false);
            }
            card.addView(indicator);

            card.setOnClickListener(v -> {
                AudioController.applyPreset(this, preset);
                updateActiveStatusDisplay(preset.title);
                highlightSelectedCard(preset.id);
                Toast.makeText(this, preset.title + " applied!", Toast.LENGTH_SHORT).show();
            });

            card.setTag(preset.id);
            presetCardViews.add(card);
            gridPresets.addView(card);
        }
    }

    private void highlightSelectedCard(String activeId) {
        for (View v : presetCardViews) {
            String tag = (String) v.getTag();
            TextView indicator = v.findViewWithTag("indicator");
            if (tag != null && tag.equals(activeId)) {
                v.setSelected(true);
                if (indicator != null) {
                    indicator.setText("● ACTIVE");
                    indicator.setTextColor(Color.parseColor("#00E676"));
                }
            } else {
                v.setSelected(false);
                if (indicator != null) {
                    indicator.setText("○ SELECT");
                    indicator.setTextColor(Color.parseColor("#66FFFFFF"));
                }
            }
        }
    }

    private void setupCustomSliders() {
        layoutSliders.removeAllViews();
        sliderValueTexts.clear();

        for (int i = 0; i < 5; i++) {
            final int bandIndex = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(12, 12, 12, 12);
            row.setFocusable(true);
            row.setClickable(true);
            row.setBackgroundResource(R.drawable.card_selector);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(0, 6, 0, 6);
            row.setLayoutParams(rowParams);

            TextView lbl = new TextView(this);
            lbl.setText(bandNames[bandIndex]);
            lbl.setTextColor(Color.WHITE);
            lbl.setTextSize(14f);
            lbl.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(lbl);

            TextView hint = new TextView(this);
            hint.setText("◀ D-Pad ▶");
            hint.setTextColor(Color.parseColor("#66FFFFFF"));
            hint.setTextSize(12f);
            hint.setPadding(10, 0, 10, 0);
            row.addView(hint);

            TextView val = new TextView(this);
            int db = customEqBands[bandIndex];
            val.setText((db > 0 ? "+" : "") + db + " dB");
            val.setTextColor(Color.parseColor("#00E676"));
            val.setTextSize(14f);
            val.setTypeface(null, android.graphics.Typeface.BOLD);
            val.setGravity(Gravity.END);
            val.setLayoutParams(new LinearLayout.LayoutParams(100, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(val);

            sliderValueTexts.add(val);

            // Handle D-Pad Left/Right for adjusting decibels
            row.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (customEqBands[bandIndex] > -10) {
                            customEqBands[bandIndex]--;
                            int curr = customEqBands[bandIndex];
                            val.setText((curr > 0 ? "+" : "") + curr + " dB");
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (customEqBands[bandIndex] < 10) {
                            customEqBands[bandIndex]++;
                            int curr = customEqBands[bandIndex];
                            val.setText((curr > 0 ? "+" : "") + curr + " dB");
                            return true;
                        }
                    }
                }
                return false;
            });

            layoutSliders.addView(row);
        }
    }

    private void updateToggleButtonsText() {
        btnToggleNight.setText("Dialogue Boost: " + (customNightMode ? "ON" : "OFF"));
        btnToggleDrc.setText("DRC Limiter: " + (customDrcMode == 1 ? "ON" : "OFF"));
        btnToggleSurround.setText("Surround: " + (customSurround ? "ON" : "OFF"));
    }

    private void updateActiveStatusDisplay(String title) {
        if (txtActiveStatus != null && title != null) {
            txtActiveStatus.setText("ACTIVE: " + title.toUpperCase());
        }
    }

    private String getPresetTitleById(String id) {
        for (AudioPreset p : AudioPreset.getAllPresets()) {
            if (p.id.equals(id)) return p.title;
        }
        return "Crisp Dialogue";
    }
}
