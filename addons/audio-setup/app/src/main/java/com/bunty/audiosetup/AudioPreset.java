package com.bunty.audiosetup;

public class AudioPreset {
    public final String id;
    public final String title;
    public final String icon;
    public final String description;
    public final boolean nightMode;
    public final int drcMode; // 1 = ON, 0 = OFF
    public final boolean surround;
    public final int[] eqBands; // dB for [100Hz, 500Hz, 1500Hz, 5000Hz, 10000Hz]

    public AudioPreset(String id, String title, String icon, String description,
                       boolean nightMode, int drcMode, boolean surround, int[] eqBands) {
        this.id = id;
        this.title = title;
        this.icon = icon;
        this.description = description;
        this.nightMode = nightMode;
        this.drcMode = drcMode;
        this.surround = surround;
        this.eqBands = eqBands;
    }

    public static AudioPreset[] getAllPresets() {
        return new AudioPreset[] {
            new AudioPreset(
                "crisp",
                "Crisp Dialogue",
                "🎙️",
                "Ultra-clear speech & voices. Dampens harsh movie blasts. Best for YouTube, news & series.",
                true, 1, false,
                new int[] { -2, -1, 2, 4, 2 }
            ),
            new AudioPreset(
                "cinema",
                "Cinema & Movie",
                "🍿",
                "Wide theater soundstage, rich dynamic range & full action punch for Netflix & Hotstar.",
                false, 0, true,
                new int[] { 1, 0, 0, 2, 2 }
            ),
            new AudioPreset(
                "latenight",
                "Late Night Mode",
                "🌙",
                "Low bass to stop wall vibration. Tight compression so you hear whispers without waking family.",
                true, 1, false,
                new int[] { -4, -2, 1, 2, 0 }
            ),
            new AudioPreset(
                "music",
                "Music & Bhajans",
                "🎵",
                "Natural acoustic warmth, pure stereo imaging, crisp flute/tabla and vocal sparkle.",
                false, 0, false,
                new int[] { 2, 0, 1, 2, 3 }
            ),
            new AudioPreset(
                "sports",
                "Sports Live",
                "🏏",
                "Clear commentary presence combined with stadium crowd ambiance for live matches.",
                true, 1, true,
                new int[] { 0, -1, 3, 3, 1 }
            ),
            new AudioPreset(
                "flat",
                "Flat / Original",
                "⚖️",
                "Factory raw sound with zero DSP coloration or compression.",
                false, 0, false,
                new int[] { 0, 0, 0, 0, 0 }
            )
        };
    }
}
