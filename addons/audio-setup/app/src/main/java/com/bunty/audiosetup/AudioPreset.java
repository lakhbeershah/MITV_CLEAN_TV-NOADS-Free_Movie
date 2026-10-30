package com.bunty.audiosetup;

public class AudioPreset {
    public final String id;
    public final String title;
    public final String icon;
    public final String description;
    
    // Core parameters for Android AudioFX & hardware DSP
    public final int soundMode;            // 0=Standard, 1=News/Dialogue, 2=Movie, 3=Custom
    public final int dialogClarity;        // 0=Off, 1=Low, 2=Medium, 3=High
    public final int bassBoost;            // 0=On, 1=Off (Amlogic Settings convention: 0 is ON)
    public final boolean surround;         // true = On, false = Off
    public final boolean nightMode;        // true = On, false = Off
    public final int drcMode;              // 0=Off, 1=Line, 2=RF
    public final int treble;               // 0..100 (50 is neutral 0dB)
    public final int bass;                 // 0..100 (50 is neutral 0dB)
    public final int bassBoostStrength;    // 0..1000 for Android BassBoost
    public final int virtualizerStrength;  // 0..1000 for Android Virtualizer
    public final int loudnessGainMb;       // 0..1000 mB for LoudnessEnhancer
    public final int[] eqBandsMb;          // Millibels [-1500..+1500] for [60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz]

    public AudioPreset(String id, String title, String icon, String description,
                       int soundMode, int dialogClarity, int bassBoost, boolean surround,
                       boolean nightMode, int drcMode, int treble, int bass,
                       int bassBoostStrength, int virtualizerStrength, int loudnessGainMb,
                       int[] eqBandsMb) {
        this.id = id;
        this.title = title;
        this.icon = icon;
        this.description = description;
        this.soundMode = soundMode;
        this.dialogClarity = dialogClarity;
        this.bassBoost = bassBoost;
        this.surround = surround;
        this.nightMode = nightMode;
        this.drcMode = drcMode;
        this.treble = treble;
        this.bass = bass;
        this.bassBoostStrength = bassBoostStrength;
        this.virtualizerStrength = virtualizerStrength;
        this.loudnessGainMb = loudnessGainMb;
        this.eqBandsMb = eqBandsMb;
    }

    public static AudioPreset[] getAllPresets() {
        return new AudioPreset[] {
            new AudioPreset(
                "crisp",
                "Crisp Dialogue",
                "🎙️",
                "Ultra-clear speech & voices. Dampens harsh movie blasts. Best for YouTube, news & series.",
                1, 3, 1, false,
                true, 1, 80, 35,
                0, 0, 700,
                new int[] { -700, -300, 400, 800, 600 }
            ),
            new AudioPreset(
                "cinema",
                "Cinema & Movie",
                "🍿",
                "Wide theater soundstage, rich dynamic range & full action punch for Netflix & Hotstar.",
                2, 2, 0, true,
                false, 0, 70, 75,
                800, 1000, 400,
                new int[] { 700, 300, 100, 600, 800 }
            ),
            new AudioPreset(
                "bass_heavy",
                "Bass Heavy Punch",
                "🔊",
                "Deep punchy low-end thump & enriched warmth for EDM, Punjabi music & party beats.",
                2, 1, 0, false,
                false, 0, 55, 95,
                1000, 350, 200,
                new int[] { 1200, 700, 0, 200, 200 }
            ),
            new AudioPreset(
                "music",
                "Music & Bhajans",
                "🎵",
                "Natural acoustic warmth, pure stereo imaging, crisp flute/tabla and vocal sparkle.",
                0, 1, 0, false,
                false, 0, 65, 60,
                400, 300, 250,
                new int[] { 400, 200, 200, 500, 600 }
            ),
            new AudioPreset(
                "latenight",
                "Late Night Mode",
                "🌙",
                "Low bass to stop wall vibration. Tight compression so you hear whispers without loud explosions.",
                1, 3, 1, false,
                true, 1, 50, 20,
                0, 0, 500,
                new int[] { -1200, -600, 200, 400, 0 }
            ),
            new AudioPreset(
                "flat",
                "Flat / Reference",
                "⚖️",
                "Factory raw sound with zero DSP coloration or compression.",
                0, 0, 1, false,
                false, 0, 50, 50,
                0, 0, 0,
                new int[] { 0, 0, 0, 0, 0 }
            )
        };
    }
}
