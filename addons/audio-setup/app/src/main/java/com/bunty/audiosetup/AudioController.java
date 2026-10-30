package com.bunty.audiosetup;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.os.IBinder;
import android.os.Parcel;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

public class AudioController {
    private static final String TAG = "AudioSetup";
    private static final String PREF_NAME = "audio_setup_prefs";
    private static final String KEY_ACTIVE_PRESET = "active_preset_id";
    private static final String KEY_NIGHT_MODE = "night_mode";
    private static final String KEY_DRC_MODE = "drc_mode";
    private static final String KEY_SURROUND = "surround";
    private static final String KEY_EQ_PREFIX = "eq_band_";

    // Standard Android AudioFX Plugins (Loaded in AudioFlinger insert chain on Session 0)
    private static Equalizer standardEqualizer = null;
    private static BassBoost bassBoostEffect = null;
    private static Virtualizer virtualizerEffect = null;
    private static LoudnessEnhancer loudnessEffect = null;

    // Amlogic Hardware DSP Effect UUIDs (running in AudioFlinger postproc on Session 0)
    private static final UUID TYPE_TREBLE_BASS = UUID.fromString("7e282240-242e-11e6-bb63-0002a5d5c51b");
    private static final UUID UUID_TREBLE_BASS = UUID.fromString("76733af0-2889-11e2-81c1-0800200c9a66");

    private static final UUID TYPE_HPEQ = UUID.fromString("ce2c14af-84df-4c36-acf5-87e428ed05fc");
    private static final UUID UUID_HPEQ = UUID.fromString("049754aa-c4cf-439f-897e-37dd0c381120");

    private static final UUID TYPE_VIRTUAL_SURROUND = UUID.fromString("c656ec6f-d6be-4e7f-854b-1218077f3915");
    private static final UUID UUID_VIRTUAL_SURROUND = UUID.fromString("c8459cd3-4400-4859-b76b-e12cc2aa67ce");

    private static AudioEffect trebleBassEffect = null;
    private static AudioEffect hpeqEffect = null;
    private static AudioEffect amlSurroundEffect = null;

    private static IBinder audioEffectsServiceBinder = null;
    private static boolean isServiceBound = false;

    private static final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            audioEffectsServiceBinder = service;
            isServiceBound = true;
            Log.i(TAG, "AudioEffectsService connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            audioEffectsServiceBinder = null;
            isServiceBound = false;
            Log.i(TAG, "AudioEffectsService disconnected");
        }
    };

    public static synchronized void bindServiceIfNeeded(Context context) {
        if (!isServiceBound && context != null) {
            try {
                Intent intent = new Intent("com.droidlogic.tvinput.services.AudioEffectsService");
                intent.setPackage("com.droidlogic.tvinput");
                context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            } catch (Exception e) {
                Log.w(TAG, "Binding AudioEffectsService warning: " + e.getMessage());
            }
        }
        initAudioEffects();
    }

    private static synchronized void initAudioEffects() {
        // 1. Android Standard Equalizer on Session 0
        if (standardEqualizer == null) {
            try {
                standardEqualizer = new Equalizer(100, 0);
                standardEqualizer.setEnabled(true);
                Log.i(TAG, "Equalizer initialized on session 0, bands=" + standardEqualizer.getNumberOfBands());
            } catch (Exception e) {
                Log.w(TAG, "Equalizer init note: " + e.getMessage());
            }
        }

        // 2. Android Standard BassBoost on Session 0
        if (bassBoostEffect == null) {
            try {
                bassBoostEffect = new BassBoost(100, 0);
                bassBoostEffect.setEnabled(true);
                Log.i(TAG, "BassBoost initialized on session 0");
            } catch (Exception e) {
                Log.w(TAG, "BassBoost init note: " + e.getMessage());
            }
        }

        // 3. Android Standard Virtualizer on Session 0
        if (virtualizerEffect == null) {
            try {
                virtualizerEffect = new Virtualizer(100, 0);
                virtualizerEffect.setEnabled(true);
                Log.i(TAG, "Virtualizer initialized on session 0");
            } catch (Exception e) {
                Log.w(TAG, "Virtualizer init note: " + e.getMessage());
            }
        }

        // 4. Android LoudnessEnhancer on Session 0 (Dialogue punch)
        if (loudnessEffect == null) {
            try {
                loudnessEffect = new LoudnessEnhancer(0);
                loudnessEffect.setEnabled(true);
                Log.i(TAG, "LoudnessEnhancer initialized on session 0");
            } catch (Exception e) {
                Log.w(TAG, "LoudnessEnhancer init note: " + e.getMessage());
            }
        }

        // 5. Amlogic Hardware TrebleBass DSP
        if (trebleBassEffect == null) {
            try {
                trebleBassEffect = createAudioEffect(TYPE_TREBLE_BASS, UUID_TREBLE_BASS, 100, 0);
                if (trebleBassEffect != null) {
                    trebleBassEffect.setEnabled(true);
                    Log.i(TAG, "Amlogic TrebleBass hardware effect initialized");
                }
            } catch (Exception e) {
                Log.w(TAG, "TrebleBass init note: " + e.getMessage());
            }
        }

        // 6. Amlogic Hardware Virtualsurround DSP
        if (amlSurroundEffect == null) {
            try {
                amlSurroundEffect = createAudioEffect(TYPE_VIRTUAL_SURROUND, UUID_VIRTUAL_SURROUND, 100, 0);
                if (amlSurroundEffect != null) {
                    amlSurroundEffect.setEnabled(true);
                    Log.i(TAG, "Amlogic Virtualsurround hardware effect initialized");
                }
            } catch (Exception e) {
                Log.w(TAG, "Amlogic Virtualsurround init note: " + e.getMessage());
            }
        }

        // 7. Amlogic Hardware HPEQ DSP
        if (hpeqEffect == null) {
            try {
                hpeqEffect = createAudioEffect(TYPE_HPEQ, UUID_HPEQ, 100, 0);
                if (hpeqEffect != null) {
                    hpeqEffect.setEnabled(true);
                    Log.i(TAG, "Amlogic HPEQ hardware effect initialized");
                }
            } catch (Exception e) {
                Log.w(TAG, "HPEQ init note: " + e.getMessage());
            }
        }
    }

    private static AudioEffect createAudioEffect(UUID type, UUID uuid, int priority, int audioSession) {
        try {
            Constructor<?>[] ctors = AudioEffect.class.getDeclaredConstructors();
            for (Constructor<?> c : ctors) {
                c.setAccessible(true);
                Class<?>[] pTypes = c.getParameterTypes();
                if (pTypes.length == 4 && pTypes[0] == UUID.class && pTypes[1] == UUID.class && pTypes[2] == int.class && pTypes[3] == int.class) {
                    return (AudioEffect) c.newInstance(type, uuid, priority, audioSession);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "createAudioEffect error (" + uuid + "): " + t.getMessage());
        }
        return null;
    }

    public static void applySavedSettings(Context context) {
        if (context == null) return;
        String activePresetId = getActivePresetId(context);
        AudioPreset[] presets = AudioPreset.getAllPresets();
        for (AudioPreset p : presets) {
            if (p.id.equals(activePresetId)) {
                applyPreset(context, p);
                return;
            }
        }
        if ("custom".equals(activePresetId)) {
            applyCustom(context, getSavedNightMode(context), getSavedDrcMode(context),
                    getSavedSurround(context), getSavedEqBands(context));
            return;
        }
        applyPreset(context, presets[0]);
    }

    public static void applyPreset(Context context, AudioPreset preset) {
        if (preset == null || context == null) return;
        bindServiceIfNeeded(context);
        applySettings(context, preset.id, preset.soundMode, preset.dialogClarity, preset.bassBoost,
                preset.surround, preset.nightMode, preset.drcMode, preset.treble, preset.bass,
                preset.bassBoostStrength, preset.virtualizerStrength, preset.loudnessGainMb, preset.eqBandsMb);
    }

    public static void applyCustom(Context context, boolean nightMode, int drcMode, boolean surround, int[] eqBandsDb) {
        if (context == null) return;
        bindServiceIfNeeded(context);
        int bassVal = 50;
        int trebleVal = 50;
        int[] eqBandsMb = new int[5];
        if (eqBandsDb != null && eqBandsDb.length >= 5) {
            bassVal = Math.max(0, Math.min(100, 50 + eqBandsDb[0] * 5));
            trebleVal = Math.max(0, Math.min(100, 50 + eqBandsDb[3] * 5));
            for (int i = 0; i < 5; i++) {
                eqBandsMb[i] = eqBandsDb[i] * 100; // dB to millibels
            }
        }
        int bbStrength = Math.max(0, (bassVal - 50) * 20); // 0..1000
        int virtStrength = surround ? 1000 : 0;
        int loudGain = (trebleVal > 50) ? (trebleVal - 50) * 10 : 0; // 0..500 mB

        applySettings(context, "custom", 3, 2, 0, surround, nightMode, drcMode, trebleVal, bassVal,
                bbStrength, virtStrength, loudGain, eqBandsMb);
    }

    private static void applySettings(Context context, String presetId, int soundMode, int dialogClarity,
                                      int bassBoostSetting, boolean surround, boolean nightMode, int drcMode,
                                      int treble, int bass, int bassBoostStrength, int virtualizerStrength,
                                      int loudnessGainMb, int[] eqBandsMb) {
        initAudioEffects();
        ContentResolver cr = context.getContentResolver();

        // 1. Real-time Android AudioFX: Equalizer on Session 0
        if (standardEqualizer != null && eqBandsMb != null && eqBandsMb.length >= 5) {
            try {
                standardEqualizer.setEnabled(true);
                short numBands = standardEqualizer.getNumberOfBands();
                short[] range = standardEqualizer.getBandLevelRange();
                short minLevel = range[0];
                short maxLevel = range[1];

                for (short b = 0; b < numBands; b++) {
                    int centerFreq = standardEqualizer.getCenterFreq(b) / 1000;
                    int targetMb = 0;
                    if (centerFreq <= 150) {
                        targetMb = eqBandsMb[0];
                    } else if (centerFreq <= 500) {
                        targetMb = eqBandsMb[1];
                    } else if (centerFreq <= 2000) {
                        targetMb = eqBandsMb[2];
                    } else if (centerFreq <= 6000) {
                        targetMb = eqBandsMb[3];
                    } else {
                        targetMb = eqBandsMb[4];
                    }
                    short level = (short) Math.max(minLevel, Math.min(maxLevel, targetMb));
                    standardEqualizer.setBandLevel(b, level);
                }
                Log.i(TAG, "Equalizer levels applied to session 0");
            } catch (Exception e) {
                Log.w(TAG, "Equalizer band apply warning: " + e.getMessage());
            }
        }

        // 2. Real-time Android AudioFX: BassBoost on Session 0
        if (bassBoostEffect != null) {
            try {
                if (bassBoostStrength > 0) {
                    bassBoostEffect.setEnabled(true);
                    bassBoostEffect.setStrength((short) Math.min(1000, bassBoostStrength));
                    Log.i(TAG, "BassBoost enabled at strength " + bassBoostStrength);
                } else {
                    bassBoostEffect.setStrength((short) 0);
                    bassBoostEffect.setEnabled(false);
                    Log.i(TAG, "BassBoost disabled");
                }
            } catch (Exception e) {
                Log.w(TAG, "BassBoost apply warning: " + e.getMessage());
            }
        }

        // 3. Real-time Android AudioFX: Virtualizer on Session 0
        if (virtualizerEffect != null) {
            try {
                if (virtualizerStrength > 0 || surround) {
                    virtualizerEffect.setEnabled(true);
                    virtualizerEffect.setStrength((short) Math.max(virtualizerStrength, surround ? 1000 : 0));
                    Log.i(TAG, "Virtualizer enabled at strength " + virtualizerStrength);
                } else {
                    virtualizerEffect.setStrength((short) 0);
                    virtualizerEffect.setEnabled(false);
                    Log.i(TAG, "Virtualizer disabled");
                }
            } catch (Exception e) {
                Log.w(TAG, "Virtualizer apply warning: " + e.getMessage());
            }
        }

        // 4. Real-time Android AudioFX: LoudnessEnhancer on Session 0 (Dialogue Punch)
        if (loudnessEffect != null) {
            try {
                if (loudnessGainMb > 0) {
                    loudnessEffect.setEnabled(true);
                    loudnessEffect.setTargetGain(loudnessGainMb);
                    Log.i(TAG, "LoudnessEnhancer enabled at gain " + loudnessGainMb + " mB");
                } else {
                    loudnessEffect.setTargetGain(0);
                    loudnessEffect.setEnabled(false);
                    Log.i(TAG, "LoudnessEnhancer disabled");
                }
            } catch (Exception e) {
                Log.w(TAG, "LoudnessEnhancer apply warning: " + e.getMessage());
            }
        }

        // 5. Amlogic Hardware TrebleBass DSP Effect
        if (trebleBassEffect != null) {
            try {
                trebleBassEffect.setEnabled(true);
                // Param 0: treble (0..100), Param 1: bass (0..100)
                setEffectParamInt(trebleBassEffect, 0, treble);
                setEffectParamInt(trebleBassEffect, 1, bass);
                Log.i(TAG, "TrebleBass hardware DSP set: treble=" + treble + " bass=" + bass);
            } catch (Exception e) {
                Log.w(TAG, "TrebleBass apply warning: " + e.getMessage());
            }
        }

        // 6. Amlogic Hardware Virtualsurround DSP Effect
        if (amlSurroundEffect != null) {
            try {
                amlSurroundEffect.setEnabled(surround);
                setEffectParamInt(amlSurroundEffect, 0, surround ? 1 : 0);
                Log.i(TAG, "Virtualsurround hardware DSP set: " + surround);
            } catch (Exception e) {
                Log.w(TAG, "Virtualsurround DSP apply warning: " + e.getMessage());
            }
        }

        // 7. Amlogic Hardware HPEQ 5-Band byte array
        if (hpeqEffect != null && eqBandsMb != null && eqBandsMb.length >= 5) {
            try {
                hpeqEffect.setEnabled(true);
                byte[] bands = new byte[5];
                for (int i = 0; i < 5; i++) {
                    bands[i] = (byte) Math.max(-10, Math.min(10, eqBandsMb[i] / 100));
                }
                setEffectParamBytes(hpeqEffect, 2, bands);
                Log.i(TAG, "HPEQ hardware DSP bands set");
            } catch (Exception e) {
                Log.w(TAG, "HPEQ DSP apply warning: " + e.getMessage());
            }
        }

        // 8. Write to Settings.Global (TV System Persistence)
        try {
            Settings.Global.putInt(cr, "sound_effect_sound_mode", soundMode);
            Settings.Global.putInt(cr, "sound_effect_dialog_clarity", dialogClarity);
            Settings.Global.putInt(cr, "sound_effect_bass_boost", bassBoostSetting);
            Settings.Global.putInt(cr, "sound_effect_surround", surround ? 1 : 0);
            Settings.Global.putInt(cr, "sound_effect_treble", treble);
            Settings.Global.putInt(cr, "sound_effect_bass", bass);
            Settings.Global.putInt(cr, "drc_mode", drcMode);

            if (eqBandsMb != null && eqBandsMb.length >= 5) {
                for (int i = 0; i < 5; i++) {
                    int sliderVal = Math.max(0, Math.min(100, 50 + (eqBandsMb[i] / 100) * 5));
                    Settings.Global.putInt(cr, "sound_effect_band" + (i + 1), sliderVal);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Settings.Global write warning: " + e.getMessage());
        }

        // 9. AudioEffectsService AIDL calls
        if (audioEffectsServiceBinder != null) {
            callServiceTransact(15, soundMode);
            callServiceTransact(21, surround ? 1 : 0);
            callServiceTransact(22, dialogClarity);
            callServiceTransact(23, bassBoostSetting);
            callServiceTransact(18, treble);
            callServiceTransact(19, bass);
        }

        // 10. Flush Hardware Mixer Buffer
        applyAudioEffectByPlayEmptyTrack();

        // 11. Broadcast Session Notification
        try {
            Intent sessionIntent = new Intent("android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION");
            sessionIntent.putExtra("android.media.extra.AUDIO_SESSION", 0);
            sessionIntent.putExtra("android.media.extra.PACKAGE_NAME", context.getPackageName());
            context.sendBroadcast(sessionIntent);
        } catch (Exception e) {
            Log.w(TAG, "Broadcast warning: " + e.getMessage());
        }

        // 12. Save Preferences
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_ACTIVE_PRESET, presetId);
        editor.putBoolean(KEY_NIGHT_MODE, nightMode);
        editor.putInt(KEY_DRC_MODE, drcMode);
        editor.putBoolean(KEY_SURROUND, surround);
        if (eqBandsMb != null && eqBandsMb.length >= 5) {
            for (int i = 0; i < 5; i++) {
                editor.putInt(KEY_EQ_PREFIX + i, eqBandsMb[i] / 100);
            }
        }
        editor.apply();
        Log.i(TAG, "Preset '" + presetId + "' applied successfully");
    }

    public static void applyAudioEffectByPlayEmptyTrack() {
        new Thread(() -> {
            try {
                int minBufferSize = AudioTrack.getMinBufferSize(8000,
                        AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
                if (minBufferSize > 0) {
                    byte[] emptyBuffer = new byte[minBufferSize];
                    AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, 8000,
                            AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                            minBufferSize, AudioTrack.MODE_STREAM);
                    track.play();
                    track.write(emptyBuffer, 0, emptyBuffer.length);
                    Thread.sleep(60);
                    track.stop();
                    track.release();
                    Log.i(TAG, "applyAudioEffectByPlayEmptyTrack flushed mixer");
                }
            } catch (Exception e) {
                Log.w(TAG, "Empty track flush warning: " + e.getMessage());
            }
        }).start();
    }

    private static void setEffectParamInt(AudioEffect effect, int param, int value) {
        if (effect == null) return;
        try {
            Method m = AudioEffect.class.getMethod("setParameter", int.class, int.class);
            m.setAccessible(true);
            m.invoke(effect, param, value);
        } catch (Exception e) {
            Log.w(TAG, "setEffectParamInt warning: " + e.getMessage());
        }
    }

    private static void setEffectParamBytes(AudioEffect effect, int param, byte[] value) {
        if (effect == null || value == null) return;
        try {
            Method m = AudioEffect.class.getMethod("setParameter", int.class, byte[].class);
            m.setAccessible(true);
            m.invoke(effect, param, value);
        } catch (Exception e) {
            Log.w(TAG, "setEffectParamBytes warning: " + e.getMessage());
        }
    }

    private static void callServiceTransact(int code, int param1) {
        if (audioEffectsServiceBinder == null) return;
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken("com.droidlogic.tvinput.services.IAudioEffectsService");
            data.writeInt(param1);
            audioEffectsServiceBinder.transact(code, data, reply, 0);
            reply.recycle();
            data.recycle();
        } catch (Exception e) {
            Log.w(TAG, "callServiceTransact (" + code + ") warning: " + e.getMessage());
        }
    }

    public static void playTestSample() {
        new Thread(() -> {
            try {
                int sampleRate = 44100;
                int durationSeconds = 3;
                int numSamples = durationSeconds * sampleRate;
                short[] buffer = new short[numSamples * 2];

                for (int i = 0; i < numSamples; i++) {
                    double t = (double) i / sampleRate;
                    double freq = (t < 1.0) ? 120.0 : ((t < 2.0) ? 1000.0 : 4000.0);
                    double envelope = Math.sin(Math.PI * (t % 1.0));
                    short sample = (short) (Math.sin(2.0 * Math.PI * freq * t) * envelope * 24000);
                    buffer[i * 2] = sample;
                    buffer[i * 2 + 1] = sample;
                }

                int minBufSize = AudioTrack.getMinBufferSize(sampleRate,
                        AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
                AudioTrack track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build())
                        .setBufferSizeInBytes(Math.max(minBufSize, buffer.length * 2))
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();

                track.write(buffer, 0, buffer.length);
                track.play();
                Thread.sleep(durationSeconds * 1000 + 200);
                track.stop();
                track.release();
            } catch (Exception e) {
                Log.e(TAG, "Error playing sound test: " + e.getMessage());
            }
        }).start();
    }

    public static String getActivePresetId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_ACTIVE_PRESET, "music");
    }

    public static boolean getSavedNightMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_NIGHT_MODE, false);
    }

    public static int getSavedDrcMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_DRC_MODE, 0);
    }

    public static boolean getSavedSurround(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SURROUND, false);
    }

    public static int[] getSavedEqBands(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int[] bands = new int[5];
        for (int i = 0; i < 5; i++) {
            bands[i] = prefs.getInt(KEY_EQ_PREFIX + i, 0);
        }
        return bands;
    }
}
