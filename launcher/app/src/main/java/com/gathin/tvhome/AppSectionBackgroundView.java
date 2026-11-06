package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Animated moving gradient background for the bottom app section.
 * Blends deep shades of black with all colors present in the selected app's icon,
 * smoothly flowing horizontally and cross-fading when focus changes.
 */
public class AppSectionBackgroundView extends View implements ValueAnimator.AnimatorUpdateListener {

    private final Paint paint = new Paint();
    private final Matrix matrix = new Matrix();

    private static final int BLK = 0xFF000000;
    private static final int DRK = 0xFF08080C;

    private int[] cur = new int[]{BLK, 0xFF333333, DRK, 0xFF222222, BLK, 0xFF444444, BLK};
    private int[] tgt = cur;
    private int[] src = cur;
    private long transStart;

    private float phase;
    private ValueAnimator flow;

    public AppSectionBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setAppColors(int[] c) {
        if (c == null || c.length == 0) return;
        int c1 = c[0];
        int c2 = c.length > 1 ? c[1] : c1;
        int c3 = c.length > 2 ? c[2] : c1;

        int[] next = new int[]{BLK, c1, DRK, c2, BLK, c3, BLK};
        if (getWidth() <= 0) {
            cur = tgt = src = next;
            return;
        }
        src = cur.clone();
        tgt = next;
        transStart = SystemClock.uptimeMillis();
    }

    private void updateShader() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        LinearGradient hGrad = new LinearGradient(0, 0, w, 0, cur, null, Shader.TileMode.REPEAT);
        LinearGradient vGrad = new LinearGradient(0, 0, 0, h,
            new int[]{0x00FFFFFF, 0x80FFFFFF, 0xFFFFFFFF},
            new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP);

        ComposeShader s = new ComposeShader(hGrad, vGrad, PorterDuff.Mode.MULTIPLY);
        matrix.setTranslate(phase * w, 0);
        s.setLocalMatrix(matrix);
        paint.setShader(s);
    }

    public void startFlow() {
        if (flow != null && flow.isRunning()) return;
        flow = ValueAnimator.ofFloat(0f, 1f);
        flow.setDuration(6000);
        flow.setRepeatCount(ValueAnimator.INFINITE);
        flow.setInterpolator(new LinearInterpolator());
        flow.addUpdateListener(this);
        flow.start();
    }

    public void stopFlow() {
        if (flow != null) {
            flow.cancel();
            flow = null;
        }
    }

    @Override
    public void onAnimationUpdate(ValueAnimator a) {
        phase = (float) a.getAnimatedValue();
        int w = getWidth();
        if (w <= 0) return;

        if (transStart > 0) {
            float f = (SystemClock.uptimeMillis() - transStart) / 360f;
            if (f >= 1f) {
                cur = tgt;
                transStart = 0;
            } else {
                for (int i = 0; i < cur.length; i++) {
                    cur[i] = blend(f, src[i], tgt[i]);
                }
            }
            updateShader();
        } else if (paint.getShader() != null) {
            matrix.setTranslate(phase * w, 0);
            paint.getShader().setLocalMatrix(matrix);
        }
        invalidate();
    }

    private static int blend(float f, int c1, int c2) {
        float inv = 1f - f;
        int a = (int) (((c1 >>> 24) & 0xff) * inv + ((c2 >>> 24) & 0xff) * f);
        int r = (int) (((c1 >> 16) & 0xff) * inv + ((c2 >> 16) & 0xff) * f);
        int g = (int) (((c1 >> 8) & 0xff) * inv + ((c2 >> 8) & 0xff) * f);
        int b = (int) ((c1 & 0xff) * inv + (c2 & 0xff) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startFlow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopFlow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int v) {
        super.onVisibilityChanged(changedView, v);
        if (v == VISIBLE) startFlow(); else stopFlow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShader();
        startFlow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }
}
