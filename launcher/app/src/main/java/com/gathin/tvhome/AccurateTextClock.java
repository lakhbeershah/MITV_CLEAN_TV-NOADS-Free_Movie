package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Custom clock & date text view that uses calibrated network time
 * and monotonic offline fallback, preventing 2019 epoch resets.
 */
public class AccurateTextClock extends TextView {

    private String pattern = "h:mm:ss a";
    private SimpleDateFormat sdf;
    private final Handler tickerHandler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateText();
            tickerHandler.postDelayed(this, 1000L);
        }
    };

    public AccurateTextClock(Context context) {
        super(context);
        init();
    }

    public AccurateTextClock(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AccurateTextClock(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        updateFormat();
    }

    public void setFormat12Hour(CharSequence format) {
        if (format != null && format.length() > 0) {
            this.pattern = format.toString();
            updateFormat();
            updateText();
        }
    }

    public void setFormat24Hour(CharSequence format) {
        if (format != null && format.length() > 0) {
            this.pattern = format.toString();
            updateFormat();
            updateText();
        }
    }

    public String getFormat12Hour() {
        return pattern;
    }

    public String getFormat24Hour() {
        return pattern;
    }

    private void updateFormat() {
        try {
            sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        } catch (Throwable t) {
            sdf = new SimpleDateFormat("h:mm:ss a", Locale.getDefault());
        }
    }

    public void updateText() {
        if (getVisibility() != VISIBLE) return;
        long accurateNow = ClockHelper.getAccurateTimeMillis(getContext());
        if (sdf != null) {
            setText(sdf.format(new Date(accurateNow)));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateText();
        tickerHandler.post(ticker);
    }

    @Override
    protected void onDetachedFromWindow() {
        tickerHandler.removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }
}
