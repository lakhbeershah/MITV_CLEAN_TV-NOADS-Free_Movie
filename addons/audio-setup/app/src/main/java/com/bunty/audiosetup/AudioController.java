package com.bunty.audiosetup;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.audiofx.Equalizer;
import android.provider.Settings;
import android.util.Log;

public class AudioController {
    private static final String TAG = "AudioSetup";
    private static final String PREF_NAME = "audio_setup_prefs";
    private static final String KEY_ACTIVE_PRESET = "active_preset_id";
    private static final String KEY_NIGHT_MODE = "night_mode";
    private static final String KEY_DRC_MODE = "drc_mode";
    private static final String KEY_SURROUND = "surround";
    private static final String KEY_EQ_PREFIX = "eq_band_";

    private static Equalizer globalEqualizer = null;
    private static AudioTrack testAudioTrack = null;

    public static void applyPreset(Context context, AudioPreset preset) {
        if (preset == null) return;
        applySettings(context, preset.id, preset.nightMode, preset.drcMode, preset.surround, preset.eqBands);
    }

    public static void applyCustom(Context context, boolean nightMode, int drcMode, boolean surround, int[] eqBands) {
        applySettings(context, "custom", nightMode, drcMode, surround, eqBands);
    }

    private static void applySettings(Context context, String presetId, boolean nightMode, int drcMode, boolean surround, int[] eqBands) {
        ContentResolver cr = context.getContentResolver();

        try {
            // Apply Xiaomi / Amlogic system settings
            Settings.System.putInt(cr, "sound_effect_night_mode", nightMode ? 1 : 0);
            Settings.Global.putInt(cr, "drc_mode", drcMode);
            Settings.System.putInt(cr, "sound_effect_surround", surround ? 1 : 0);
        } catch (Exception e) {
            Log.w(TAG, "Settings write warning: " + e.getMessage());
        }

        // Apply Android Equalizer (Session 0: Global Output Mix)
        applyHardwareEqualizer(eqBands);

        // Save preferences
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_ACTIVE_PRESET, presetId);
        editor.putBoolean(KEY_NIGHT_MODE, nightMode);
        editor.putInt(KEY_DRC_MODE, drcMode);
        editor.putBoolean(KEY_SURROUND, surround);
        if (eqBands != null && eqBands.length >= 5) {
            for (int i = 0; i < 5; i++) {
                editor.putInt(KEY_EQ_PREFIX + i, eqBands[i]);
            }
        }
        editor.apply();
    }

    private static synchronized void applyHardwareEqualizer(int[] eqBands) {
        if (eqBands == null || eqBands.length < 5) return;
        try {
            if (globalEqualizer == null) {
                globalEqualizer = new Equalizer(0, 0);
            }
            if (globalEqualizer.getNumberOfBands() > 0) {
                globalEqualizer.setEnabled(true);
                short numBands = globalEqualizer.getNumberOfBands();
                short minLevel = globalEqualizer.getBandLevelRange()[0];
                short maxLevel = globalEqualizer.getBandLevelRange()[1];

                for (short b = 0; b < numBands; b++) {
                    int centerFreq = globalEqualizer.getCenterFreq(b) / 1000; // in Hz
                    int targetDb = 0;
                    if (centerFreq <= 250) {
                        targetDb = eqBands[0]; // 100Hz
                    } else if (centerFreq <= 800) {
                        targetDb = eqBands[1]; // 500Hz
                    } else if (centerFreq <= 2500) {
                        targetDb = eqBands[2]; // 1.5kHz
                    } else if (centerFreq <= 7000) {
                        targetDb = eqBands[3]; // 5kHz
                    } else {
                        targetDb = eqBands[4]; // 10kHz
                    }

                    short levelMb = (short) Math.max(minLevel, Math.min(maxLevel, targetDb * 100));
                    globalEqualizer.setBandLevel(b, levelMb);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Global Equalizer setup note: " + e.getMessage());
        }
    }

    public static void applySavedSettings(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String presetId = prefs.getString(KEY_ACTIVE_PRESET, "crisp");
        boolean nightMode = prefs.getBoolean(KEY_NIGHT_MODE, true);
        int drcMode = prefs.getInt(KEY_DRC_MODE, 1);
        boolean surround = prefs.getBoolean(KEY_SURROUND, false);
        int[] eqBands = new int[5];
        for (int i = 0; i < 5; i++) {
            eqBands[i] = prefs.getInt(KEY_EQ_PREFIX + i, i == 3 ? 4 : (i == 2 ? 2 : (i == 0 ? -2 : 0)));
        }
        applySettings(context, presetId, nightMode, drcMode, surround, eqBands);
    }

    public static String getActivePresetId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_ACTIVE_PRESET, "crisp");
    }

    public static boolean getSavedNightMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_NIGHT_MODE, true);
    }

    public static int getSavedDrcMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_DRC_MODE, 1);
    }

    public static boolean getSavedSurround(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SURROUND, false);
    }

    public static int[] getSavedEqBands(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int[] bands = new int[5];
        bands[0] = prefs.getInt(KEY_EQ_PREFIX + "0", -2);
        bands[1] = prefs.getInt(KEY_EQ_PREFIX + "1", -1);
        bands[2] = prefs.getInt(KEY_EQ_PREFIX + "2", 2);
        bands[3] = prefs.getInt(KEY_EQ_PREFIX + "3", 4);
        bands[4] = prefs.getInt(KEY_EQ_PREFIX + "4", 2);
        return bands;
    }

    /**
     * Plays a rich multi-frequency synthesized acoustic sound (bass, vocal chord, treble shimmer)
     * so the user can immediately preview the audio equalizer and DRC response.
     */
    public static void playTestSample() {
        new Thread(() -> {
            try {
                if (testAudioTrack != null) {
                    try {
                        testAudioTrack.stop();
                        testAudioTrack.release();
                    } catch (Exception ignored) {}
                }

                int sampleRate = 44100;
                int durationSeconds = 3;
                int numSamples = durationSeconds * sampleRate;
                short[] sample = new short[numSamples];

                // Frequencies: 130 Hz (C3 bass), 523 Hz (C5 voice), 1046 Hz (C6), 5000 Hz (Harmonic shimmer)
                for (int i = 0; i < numSamples; i++) {
                    double t = (double) i / sampleRate;
                    // Envelope: smooth fade-in and gentle decay
                    double envelope = Math.min(1.0, t * 8.0) * Math.exp(-t * 0.9);

                    double wave = 0.40 * Math.sin(2 * Math.PI * 130.81 * t)   // Bass
                                + 0.35 * Math.sin(2 * Math.PI * 523.25 * t)   // Mid vocal
                                + 0.25 * Math.sin(2 * Math.PI * 1046.50 * t)  // Upper vocal
                                + 0.15 * Math.sin(2 * Math.PI * 4186.00 * t); // Treble shimmer

                    sample[i] = (short) (wave * envelope * Short.MAX_VALUE * 0.7);
                }

                testAudioTrack = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(numSamples * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();

                testAudioTrack.write(sample, 0, numSamples);
                testAudioTrack.play();
            } catch (Exception e) {
                Log.w(TAG, "Audio test preview error: " + e.getMessage());
            }
        }).start();
    }
}
