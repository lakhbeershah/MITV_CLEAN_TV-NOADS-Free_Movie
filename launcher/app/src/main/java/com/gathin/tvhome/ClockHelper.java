package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextClock;

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
    public static final String DATE_MDY_SLASH = "MM/dd/yyyy";
    public static final String DATE_YMD_DASH = "yyyy-MM-dd";
    public static final String DATE_HIDDEN = "none";

    public static void applyHomeClock(Context context, TextClock clock, TextClock date, View container) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String timeFmt = sp.getString(KEY_HOME_TIME_FMT, FMT_12_SEC);
        String dateFmt = sp.getString(KEY_HOME_DATE_FMT, DATE_VERBAL_STD);
        String pos = sp.getString(KEY_HOME_POS, "top_right");

        if (clock != null) {
            clock.setFormat12Hour(timeFmt);
            clock.setFormat24Hour(timeFmt.contains("a") ? timeFmt.replace(" a", "") : timeFmt);
        }

        if (date != null) {
            if (DATE_HIDDEN.equals(dateFmt)) {
                date.setVisibility(View.GONE);
            } else {
                date.setVisibility(View.VISIBLE);
                date.setFormat12Hour(dateFmt);
                date.setFormat24Hour(dateFmt);
            }
        }

        if (container != null && container.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) container.getLayoutParams();
            lp.removeRule(RelativeLayout.ALIGN_PARENT_END);
            lp.removeRule(RelativeLayout.ALIGN_PARENT_START);
            lp.removeRule(RelativeLayout.CENTER_HORIZONTAL);
            if ("center".equals(pos)) {
                lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
                if (container instanceof LinearLayout) {
                    ((LinearLayout) container).setGravity(Gravity.CENTER_HORIZONTAL);
                }
            } else if ("top_left".equals(pos)) {
                lp.addRule(RelativeLayout.ALIGN_PARENT_START);
                if (container instanceof LinearLayout) {
                    ((LinearLayout) container).setGravity(Gravity.START);
                }
            } else {
                lp.addRule(RelativeLayout.ALIGN_PARENT_END);
                if (container instanceof LinearLayout) {
                    ((LinearLayout) container).setGravity(Gravity.END);
                }
            }
            container.setLayoutParams(lp);
        }
    }

    public static void applyScreensaverClock(Context context, TextClock clock, TextClock date, View analogClock, View container) {
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
            clock.setFormat12Hour(timeFmt);
            clock.setFormat24Hour(timeFmt.contains("a") ? timeFmt.replace(" a", "") : timeFmt);
        }

        if (date != null) {
            if (DATE_HIDDEN.equals(dateFmt)) {
                date.setVisibility(View.GONE);
            } else {
                date.setVisibility(View.VISIBLE);
                date.setFormat12Hour(dateFmt);
                date.setFormat24Hour(dateFmt);
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
        }
    }
}
