package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Centralized Clock & Date Layout Engine for Launcher Studio and Screensaver.
 */
public class ClockHelper {

    public static final String PREFS_NAME = "home_custom_prefs";

    // Keys
    public static final String KEY_HOME_TIME_FMT = "clock_home_time_format";
    public static final String KEY_HOME_DATE_FMT = "clock_home_date_format";
    public static final String KEY_HOME_POS = "clock_home_position";

    public static final String KEY_SS_STYLE = "clock_ss_style"; // "digital", "analog"
    public static final String KEY_SS_TIME_FMT = "clock_ss_time_format";
    public static final String KEY_SS_DATE_FMT = "clock_ss_date_format";
    public static final String KEY_SS_POS = "clock_ss_position";

    // Formats
    public static final String FMT_12_SEC = "h:mm:ss a";
    public static final String FMT_12_CLEAN = "h:mm a";
    public static final String FMT_24_SEC = "HH:mm:ss";
    public static final String FMT_24_CLEAN = "HH:mm";

    public static final String DATE_VERBAL_STD = "EEE, d MMM yyyy";
    public static final String DATE_VERBAL_FULL = "EEEE, d MMMM yyyy";
    public static final String DATE_DMY_SLASH = "dd/MM/yyyy";
    public static final String DATE_DMY_DASH = "dd-MM-yyyy";
    public static final String DATE_HIDDEN = "none";

    /**
     * Returns the accurate calibrated timestamp, resolving 2019 epoch resets
     * via network time offset and offline monotonic fallback.
     */
    public static long getAccurateTimeMillis(Context context) {
        if (context == null) return System.currentTimeMillis();
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long systemNow = System.currentTimeMillis();
        long offset = sp.getLong("time_offset_ms", 0L);
        long lastKnownGood = sp.getLong("last_known_good_time_ms", 0L);

        long calculated = systemNow + offset;

        // If system time or calculated time is before Jan 1, 2025 (e.g. 2019 epoch reset bug)
        if (calculated < 1735689600000L) {
            if (lastKnownGood >= 1735689600000L) {
                long lastSavedElapsed = sp.getLong("last_saved_elapsed_realtime", 0L);
                long currentElapsed = SystemClock.elapsedRealtime();
                if (currentElapsed >= lastSavedElapsed && lastSavedElapsed > 0) {
                    calculated = lastKnownGood + (currentElapsed - lastSavedElapsed);
                } else {
                    calculated = lastKnownGood + currentElapsed;
                }
            }
        } else {
            // Persist valid 2025+ timestamp periodically
            long lastSaved = sp.getLong("last_saved_elapsed_realtime", 0L);
            long currentElapsed = SystemClock.elapsedRealtime();
            if (currentElapsed - lastSaved > 60_000L) {
                sp.edit()
                        .putLong("last_known_good_time_ms", calculated)
                        .putLong("last_saved_elapsed_realtime", currentElapsed)
                        .apply();
            }
        }
        return calculated;
    }

    public static String getFormattedSampleTime(String pattern) {
        return getFormattedSampleTime(null, pattern);
    }

    public static String getFormattedSampleTime(Context context, String pattern) {
        if (pattern == null || pattern.isEmpty()) pattern = FMT_12_SEC;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
            long now = context != null ? getAccurateTimeMillis(context) : System.currentTimeMillis();
            return sdf.format(new Date(now));
        } catch (Throwable t) {
            return "4:42:15 PM";
        }
    }

    public static String getFormattedSampleDate(String pattern) {
        return getFormattedSampleDate(null, pattern);
    }

    public static String getFormattedSampleDate(Context context, String pattern) {
        if (DATE_HIDDEN.equals(pattern)) return "(Date Hidden)";
        if (pattern == null || pattern.isEmpty()) pattern = DATE_VERBAL_STD;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
            long now = context != null ? getAccurateTimeMillis(context) : System.currentTimeMillis();
            return sdf.format(new Date(now));
        } catch (Throwable t) {
            return "SAT, 14 NOV 2026";
        }
    }

    public static void applyHomeClock(Context context, TextView clock, TextView date, View container) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String timeFmt = sp.getString(KEY_HOME_TIME_FMT, FMT_12_SEC);
        String dateFmt = sp.getString(KEY_HOME_DATE_FMT, DATE_VERBAL_STD);
        String pos = sp.getString(KEY_HOME_POS, "top_right");

        if (clock != null) {
            clock.setVisibility(View.VISIBLE);
            if (clock instanceof AccurateTextClock) {
                ((AccurateTextClock) clock).setFormat12Hour(timeFmt);
                ((AccurateTextClock) clock).setFormat24Hour(timeFmt);
            } else if (clock instanceof android.widget.TextClock) {
                android.widget.TextClock tc = (android.widget.TextClock) clock;
                tc.setFormat12Hour(timeFmt);
                tc.setFormat24Hour(timeFmt);
            }
        }

        if (date != null) {
            if (DATE_HIDDEN.equals(dateFmt)) {
                date.setVisibility(View.GONE);
            } else {
                date.setVisibility(View.VISIBLE);
                if (date instanceof AccurateTextClock) {
                    ((AccurateTextClock) date).setFormat12Hour(dateFmt);
                    ((AccurateTextClock) date).setFormat24Hour(dateFmt);
                } else if (date instanceof android.widget.TextClock) {
                    android.widget.TextClock tc = (android.widget.TextClock) date;
                    tc.setFormat12Hour(dateFmt);
                    tc.setFormat24Hour(dateFmt);
                }
            }
        }

        if (container != null) {
            container.setVisibility(View.VISIBLE);
            container.bringToFront();

            if (container.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) container.getLayoutParams();
                lp.removeRule(RelativeLayout.ALIGN_PARENT_END);
                lp.removeRule(RelativeLayout.ALIGN_PARENT_START);
                lp.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                lp.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
                lp.removeRule(RelativeLayout.CENTER_HORIZONTAL);
                lp.removeRule(RelativeLayout.CENTER_IN_PARENT);
                lp.removeRule(RelativeLayout.END_OF);
                lp.removeRule(RelativeLayout.RIGHT_OF);
                lp.removeRule(RelativeLayout.START_OF);
                lp.removeRule(RelativeLayout.LEFT_OF);

                float density = context.getResources().getDisplayMetrics().density;
                lp.addRule(RelativeLayout.CENTER_VERTICAL);

                if ("center".equals(pos)) {
                    lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
                    lp.setMarginStart(0);
                    lp.setMarginEnd(0);
                    if (container instanceof LinearLayout) {
                        ((LinearLayout) container).setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
                    }
                } else if ("top_left".equals(pos)) {
                    lp.addRule(RelativeLayout.END_OF, R.id.topButtons);
                    lp.setMarginStart((int) (20 * density));
                    lp.setMarginEnd(0);
                    if (container instanceof LinearLayout) {
                        ((LinearLayout) container).setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    }
                } else { // "top_right" (default)
                    lp.addRule(RelativeLayout.ALIGN_PARENT_END);
                    lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                    lp.setMarginStart(0);
                    lp.setMarginEnd(0);
                    if (container instanceof LinearLayout) {
                        ((LinearLayout) container).setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                    }
                }
                container.setLayoutParams(lp);
                container.requestLayout();
            }
        }
    }

    public static void applyScreensaverClock(Context context, TextView clock, TextView date, View analogClock, View container) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String style = sp.getString(KEY_SS_STYLE, "digital");
        String timeFmt = sp.getString(KEY_SS_TIME_FMT, FMT_12_SEC);
        String dateFmt = sp.getString(KEY_SS_DATE_FMT, DATE_VERBAL_STD);
        String pos = sp.getString(KEY_SS_POS, "bottom_right");

        boolean isAnalog = "analog".equals(style);
        if (analogClock != null) {
            analogClock.setVisibility(isAnalog ? View.VISIBLE : View.GONE);
        }
        if (clock != null) {
            clock.setVisibility(isAnalog ? View.GONE : View.VISIBLE);
            if (clock instanceof AccurateTextClock) {
                ((AccurateTextClock) clock).setFormat12Hour(timeFmt);
                ((AccurateTextClock) clock).setFormat24Hour(timeFmt);
            } else if (clock instanceof android.widget.TextClock) {
                android.widget.TextClock tc = (android.widget.TextClock) clock;
                tc.setFormat12Hour(timeFmt);
                tc.setFormat24Hour(timeFmt);
            }
        }

        if (date != null) {
            if (DATE_HIDDEN.equals(dateFmt)) {
                date.setVisibility(View.GONE);
            } else {
                date.setVisibility(View.VISIBLE);
                if (date instanceof AccurateTextClock) {
                    ((AccurateTextClock) date).setFormat12Hour(dateFmt);
                    ((AccurateTextClock) date).setFormat24Hour(dateFmt);
                } else if (date instanceof android.widget.TextClock) {
                    android.widget.TextClock tc = (android.widget.TextClock) date;
                    tc.setFormat12Hour(dateFmt);
                    tc.setFormat24Hour(dateFmt);
                }
            }
        }

        if (container != null && container.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) container.getLayoutParams();
            if ("center".equals(pos)) {
                lp.gravity = Gravity.CENTER;
                if (container instanceof LinearLayout) {
                    ((LinearLayout) container).setGravity(Gravity.CENTER_HORIZONTAL);
                }
            } else if ("top_right".equals(pos)) {
                lp.gravity = Gravity.TOP | Gravity.END;
                if (container instanceof LinearLayout) {
                    ((LinearLayout) container).setGravity(Gravity.END);
                }
            } else if ("bottom_left".equals(pos)) {
                lp.gravity = Gravity.BOTTOM | Gravity.START;
                if (container instanceof LinearLayout) {
                    ((LinearLayout) container).setGravity(Gravity.START);
                }
            } else {
                lp.gravity = Gravity.BOTTOM | Gravity.END;
                if (container instanceof LinearLayout) {
                    ((LinearLayout) container).setGravity(Gravity.END);
                }
            }
            container.setLayoutParams(lp);
            container.requestLayout();
        }
    }
}
