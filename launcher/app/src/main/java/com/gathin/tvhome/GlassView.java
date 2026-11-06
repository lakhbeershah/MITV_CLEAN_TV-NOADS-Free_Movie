package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * A translucent glass slab: soft sheen fill, bright top specular, crisp running duo-tone gradient rim
 * and a radiant ambient glow. One instance floats over the grid and moves with spring animations between tiles.
 */
public class GlassView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private float radius;
    /** Glow colour as 0xRRGGBB: white normally, or the app's primary vibrant accent. */
    private int glowRgb = 0xFFFFFF;
    /** Glow strength: radiant alpha. */
    private int glowAlpha = 0x55;

    private int color1 = 0xFFFFFFFF;
    private int color2 = 0xFFFFFFFF;
    private float rotationAngle = 0f;
    private ValueAnimator borderAnimator;
    private final Matrix gradientMatrix = new Matrix();
    private SweepGradient sweepGradient;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(2.6f * density);
        rim.setColor(0xF0FFFFFF);
        glow.setStyle(Paint.Style.STROKE);
    }

    /** Sets the 2 dominant app colours and starts the animated rotating gradient border flow. */
    void setColors(int c1, int c2) {
        if (c1 == 0) c1 = 0xFFFFFFFF;
        if (c2 == 0) c2 = 0xFFFFFFFF;
        if (this.color1 == c1 && this.color2 == c2) return;
        this.color1 = c1;
        this.color2 = c2;
        this.glowRgb = c1 & 0xFFFFFF;
        this.glowAlpha = 0x90;
        updateShader();
        startBorderAnimation();
        invalidate();
    }

    /** Legacy single accent setter: pairs the accent with bright white for dynamic gradient. */
    void setAccent(int argb) {
        if (argb == 0) {
            setColors(0xFFFFFFFF, 0xFFFFFFFF);
        } else {
            setColors(argb, 0xFFFFFFFF);
        }
    }

    private void updateShader() {
        if (rect.width() <= 0 || rect.height() <= 0) return;
        float cx = rect.centerX();
        float cy = rect.centerY();
        int[] gradientColors = new int[]{ color1, color2, color1, color2, color1 };
        float[] positions = new float[]{ 0f, 0.25f, 0.5f, 0.75f, 1f };
        sweepGradient = new SweepGradient(cx, cy, gradientColors, positions);
        gradientMatrix.setRotate(rotationAngle, cx, cy);
        sweepGradient.setLocalMatrix(gradientMatrix);
        rim.setShader(sweepGradient);
    }

    void startBorderAnimation() {
        if (borderAnimator != null && borderAnimator.isRunning()) return;
        borderAnimator = ValueAnimator.ofFloat(0f, 360f);
        borderAnimator.setDuration(2400); // 2.4s for smooth, lively flow
        borderAnimator.setRepeatCount(ValueAnimator.INFINITE);
        borderAnimator.setInterpolator(new LinearInterpolator());
        borderAnimator.addUpdateListener(animation -> {
            rotationAngle = (float) animation.getAnimatedValue();
            if (sweepGradient != null && rect.width() > 0) {
                gradientMatrix.setRotate(rotationAngle, rect.centerX(), rect.centerY());
                sweepGradient.setLocalMatrix(gradientMatrix);
                rim.setShader(sweepGradient);
            }
            invalidate();
        });
        borderAnimator.start();
    }

    void stopBorderAnimation() {
        if (borderAnimator != null) {
            borderAnimator.cancel();
            borderAnimator = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startBorderAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopBorderAnimation();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            startBorderAnimation();
        } else {
            stopBorderAnimation();
        }
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float inset = 10f * density;
        rect.set(inset, inset, w - inset, h - inset);
        fill.setShader(new LinearGradient(0, 0, 0, h, 0x3DFFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP));
        specular.setShader(new LinearGradient(0, 0, 0, h * 0.45f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        updateShader();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = 10f * density;   // room for the radiant outer glow
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        // Outer glow: thin rings across the inset, each a little fainter, so it fades out smoothly.
        int rings = 16;
        float step = inset / rings;
        glow.setStrokeWidth(step + 0.6f);
        for (int i = 0; i < rings; i++) {
            float o = step * (i + 0.5f);
            float t = 1f - (float) i / rings;
            glow.setColor(Math.round(glowAlpha * t) << 24 | glowRgb);
            canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o, radius + o, radius + o, glow);
        }

        canvas.drawRoundRect(rect, radius, radius, fill);

        // Sheen across the top 45%: clipRect stays on the GPU and keeps top corners rounded
        canvas.save();
        canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f);
        canvas.drawRoundRect(rect, radius, radius, specular);
        canvas.restore();

        canvas.drawRoundRect(rect, radius, radius, rim);
    }
}
