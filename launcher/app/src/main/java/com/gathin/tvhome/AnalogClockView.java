package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.util.Calendar;

/**
 * Clean ambient luxury analog clock for screensaver & home hero.
 */
public class AnalogClockView extends View {

    private final Paint dialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hourPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint minutePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint secondPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerCapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Handler tickerHandler = new Handler(Looper.getMainLooper());
    private final Runnable tickerRunnable = new Runnable() {
        @Override
        public void run() {
            invalidate();
            tickerHandler.postDelayed(this, 1000L);
        }
    };

    public AnalogClockView(Context context) {
        super(context);
        init();
    }

    public AnalogClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AnalogClockView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        dialPaint.setStyle(Paint.Style.STROKE);
        dialPaint.setStrokeWidth(3f);
        dialPaint.setColor(0x66FFFFFF);

        tickPaint.setColor(0xCCFFFFFF);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        hourPaint.setColor(0xFFFFFFFF);
        hourPaint.setStrokeCap(Paint.Cap.ROUND);
        hourPaint.setStrokeWidth(5f);

        minutePaint.setColor(0xEEFFFFFF);
        minutePaint.setStrokeCap(Paint.Cap.ROUND);
        minutePaint.setStrokeWidth(3.5f);

        secondPaint.setColor(0xFF00E5FF); // Vibrant Cyan second hand
        secondPaint.setStrokeCap(Paint.Cap.ROUND);
        secondPaint.setStrokeWidth(2f);

        centerCapPaint.setColor(0xFF00E5FF);
        centerCapPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        tickerHandler.post(tickerRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        tickerHandler.removeCallbacks(tickerRunnable);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float radius = Math.min(cx, cy) - 6f;

        // Dial ring
        canvas.drawCircle(cx, cy, radius, dialPaint);

        // 12 Hour Ticks
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30);
            float inner = radius - (i % 3 == 0 ? 12f : 6f);
            tickPaint.setStrokeWidth(i % 3 == 0 ? 3.5f : 1.8f);
            float x1 = (float) (cx + Math.sin(angle) * inner);
            float y1 = (float) (cy - Math.cos(angle) * inner);
            float x2 = (float) (cx + Math.sin(angle) * radius);
            float y2 = (float) (cy - Math.cos(angle) * radius);
            canvas.drawLine(x1, y1, x2, y2, tickPaint);
        }

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ClockHelper.getAccurateTimeMillis(getContext()));
        int hours = cal.get(Calendar.HOUR);
        int minutes = cal.get(Calendar.MINUTE);
        int seconds = cal.get(Calendar.SECOND);

        // Hour Hand
        double hourAngle = Math.toRadians((hours * 30) + (minutes * 0.5));
        float hx = (float) (cx + Math.sin(hourAngle) * (radius * 0.52f));
        float hy = (float) (cy - Math.cos(hourAngle) * (radius * 0.52f));
        canvas.drawLine(cx, cy, hx, hy, hourPaint);

        // Minute Hand
        double minAngle = Math.toRadians((minutes * 6) + (seconds * 0.1));
        float mx = (float) (cx + Math.sin(minAngle) * (radius * 0.75f));
        float my = (float) (cy - Math.cos(minAngle) * (radius * 0.75f));
        canvas.drawLine(cx, cy, mx, my, minutePaint);

        // Second Hand
        double secAngle = Math.toRadians(seconds * 6);
        float sx = (float) (cx + Math.sin(secAngle) * (radius * 0.85f));
        float sy = (float) (cy - Math.cos(secAngle) * (radius * 0.85f));
        float tailX = (float) (cx - Math.sin(secAngle) * (radius * 0.18f));
        float tailY = (float) (cy + Math.cos(secAngle) * (radius * 0.18f));
        canvas.drawLine(tailX, tailY, sx, sy, secondPaint);

        // Center hub
        canvas.drawCircle(cx, cy, 4.5f, centerCapPaint);
    }
}
