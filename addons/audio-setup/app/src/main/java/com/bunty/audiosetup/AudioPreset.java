package com.bunty.audiosetup;

public class AudioPreset {
    public final String id;
    public final String title;
    public final String icon;
    public final String description;

    // First-build properties (backward-compatible)
    public final boolean nightMode;
    public final int drcMode; // 1 = ON, 0 = OFF
    public final boolean surround;
    public final int[] eqBands; // dB for [100Hz, 500Hz, 1500Hz, 5000Hz, 10000Hz]

    // Extended DSP / AudioFX parameters for audible TV tuning
    public final int dialogClarity;        // 0=Off, 1=Low, 2=Medium, 3=High
    public final int bassBoost;            // 0=On, 1=Off (Amlogic Settings: 0 is ON)
    public final int treble;               // 0..100 (50 is neutral 0dB)
    public final int bass;                 // 0..100 (50 is neutral 0dB)
    public final int bassBoostStrength;    // 0..1000 for Android BassBoost
    public final int virtualizerStrength;  // 0..1000 for Android Virtualizer
    public final int loudnessGainMb;       // 0..1000 mB for LoudnessEnhancer
    public final int[] eqBandsMb;          // Millibels for [60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz]

    public AudioPreset(String id, String title, String icon, String description,
                       boolean nightMode, int drcMode, boolean surround, int[] eqBands) {
        this(id, title, icon, description,
                nightMode, drcMode, surround, eqBands,
                nightMode ? 3 : 1,          // dialogClarity: 3 if night/dialogue, else 1
                (eqBands != null && eqBands.length > 0 && eqBands[0] > 0) ? 0 : 1, // bassBoost
                calculateTreble(eqBands),
                calculateBass(eqBands),
                (eqBands != null && eqBands.length > 0 && eqBands[0] > 0) ? eqBands[0] * 200 : 0,
                surround ? 1000 : 0,
                (eqBands != null && eqBands.length > 3 && eqBands[3] > 0) ? eqBands[3] * 150 : 0,
                toMillibels(eqBands)
        );
    }

    public AudioPreset(String id, String title, String icon, String description,
                       boolean nightMode, int drcMode, boolean surround, int[] eqBands,
                       int dialogClarity, int bassBoost, int treble, int bass,
                       int bassBoostStrength, int virtualizerStrength, int loudnessGainMb,
                       int[] eqBandsMb) {
        this.id = id;
        this.title = title;
        this.icon = icon;
        this.description = description;
        this.nightMode = nightMode;
        this.drcMode = drcMode;
        this.surround = surround;
        this.eqBands = eqBands;
        this.dialogClarity = dialogClarity;
        this.bassBoost = bassBoost;
        this.treble = treble;
        this.bass = bass;
        this.bassBoostStrength = bassBoostStrength;
        this.virtualizerStrength = virtualizerStrength;
        this.loudnessGainMb = loudnessGainMb;
        this.eqBandsMb = eqBandsMb != null ? eqBandsMb : toMillibels(eqBands);
    }

    private static int calculateTreble(int[] eq) {
        if (eq == null || eq.length < 5) return 50;
        return Math.max(0, Math.min(100, 50 + eq[3] * 6));
    }

    private static int calculateBass(int[] eq) {
        if (eq == null || eq.length < 1) return 50;
        return Math.max(0, Math.min(100, 50 + eq[0] * 6));
    }

    private static int[] toMillibels(int[] eqBands) {
        if (eqBands == null) return new int[] { 0, 0, 0, 0, 0 };
        int[] mb = new int[5];
        for (int i = 0; i < Math.min(5, eqBands.length); i++) {
            mb[i] = eqBands[i] * 100;
        }
        return mb;
    }

    public static AudioPreset[] getAllPresets() {
        return new AudioPreset[] {
            // 1. Featured HDMovie2 Cinema Preset (Optimized for web browser & streaming movies)
            new AudioPreset(
                "hdmovie2",
                "HDMovie2 Cinema",
                "🎬",
                "Mastered for HDMovie2 & web movie streaming. Crystal-clear dialogue, wide theater surround, and dynamic sound leveling to tame loud action blasts.",
                true, 1, true,
                new int[] { -2, 0, 3, 5, 2 },
                3, 1, 75, 45,
                250, 900, 750,
                new int[] { -300, 50, 450, 800, 400 }
            ),
            // 2. Crisp Dialogue (Original First Build)
            new AudioPreset(
                "crisp",
                "Crisp Dialogue",
                "🎙️",
                "Ultra-clear speech & voices. Dampens harsh movie blasts. Best for YouTube, news & series.",
                true, 1, false,
                new int[] { -2, -1, 2, 4, 2 },
                3, 1, 80, 40,
                0, 0, 800,
                new int[] { -400, -150, 400, 800, 500 }
            ),
            // 3. Cinema & Movie (Original First Build)
            new AudioPreset(
                "cinema",
                "Cinema & Movie",
                "🍿",
                "Wide theater soundstage, rich dynamic range & full action punch for Netflix & Hotstar.",
                false, 0, true,
                new int[] { 1, 0, 0, 2, 2 },
                2, 0, 70, 75,
                700, 1000, 400,
                new int[] { 400, 100, 100, 500, 500 }
            ),
            // 4. Late Night Mode (Original First Build)
            new AudioPreset(
                "latenight",
                "Late Night Mode",
                "🌙",
                "Low bass to stop wall vibration. Tight compression so you hear whispers without waking family.",
                true, 1, false,
                new int[] { -4, -2, 1, 2, 0 },
                3, 1, 55, 20,
                0, 0, 500,
                new int[] { -800, -400, 250, 450, 0 }
            ),
            // 5. Music & Bhajans (Original First Build)
            new AudioPreset(
                "music",
                "Music & Bhajans",
                "🎵",
                "Natural acoustic warmth, pure stereo imaging, crisp flute/tabla and vocal sparkle.",
                false, 0, false,
                new int[] { 2, 0, 1, 2, 3 },
                1, 0, 70, 65,
                500, 300, 250,
                new int[] { 500, 150, 250, 500, 700 }
            ),
            // 6. Sports Live (Original First Build)
            new AudioPreset(
                "sports",
                "Sports Live",
                "🏏",
                "Clear commentary presence combined with stadium crowd ambiance for live matches.",
                true, 1, true,
                new int[] { 0, -1, 3, 3, 1 },
                3, 1, 70, 50,
                200, 800, 600,
                new int[] { 0, -100, 500, 650, 300 }
            ),
            // 7. Flat / Original (Original First Build)
            new AudioPreset(
                "flat",
                "Flat / Original",
                "⚖️",
                "Factory raw sound with zero DSP coloration or compression.",
                false, 0, false,
                new int[] { 0, 0, 0, 0, 0 },
                0, 1, 50, 50,
                0, 0, 0,
                new int[] { 0, 0, 0, 0, 0 }
            )
        };
    }
}
