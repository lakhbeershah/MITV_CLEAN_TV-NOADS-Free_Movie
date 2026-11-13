package com.clean.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * High-precision, analog-accelerated TV mouse cursor & D-Pad focus highlight overlay.
 */
public class VirtualCursorView extends View {

    private float cursorX = 960;
    private float cursorY = 540;
    private boolean isVisible = false;

    private float focusNx = -1f;
    private float focusNy = -1f;
    private float focusNw = 0f;
    private float focusNh = 0f;
    private boolean hasFocusRect = false;
    private boolean focusBoxVisible = false;
    private final RectF focusDrawRect = new RectF();

    private Paint cursorPaint;
    private Paint shadowPaint;
    private Paint focusStrokePaint;
    private Paint focusGlowPaint;
    private Path cursorPath;

    public VirtualCursorView(Context context) {
        super(context);
        init();
    }

    public VirtualCursorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaint.setColor(0xFF00E5FF); // Bright Cyan
        cursorPaint.setStyle(Paint.Style.FILL);

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0xCC000000);
        shadowPaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setStrokeWidth(3f);

        focusGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        focusGlowPaint.setColor(0x6600E5FF);
        focusGlowPaint.setStyle(Paint.Style.STROKE);
        focusGlowPaint.setStrokeWidth(10f);

        focusStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        focusStrokePaint.setColor(0xFF00E5FF);
        focusStrokePaint.setStyle(Paint.Style.STROKE);
        focusStrokePaint.setStrokeWidth(3.5f);

        cursorPath = new Path();
        cursorPath.moveTo(0, 0);
        cursorPath.lineTo(0, 26);
        cursorPath.lineTo(7, 20);
        cursorPath.lineTo(13, 32);
        cursorPath.lineTo(17, 30);
        cursorPath.lineTo(11, 18);
        cursorPath.lineTo(19, 18);
        cursorPath.close();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (cursorX == 960 && cursorY == 540) {
            cursorX = w / 2f;
            cursorY = h / 2f;
        }
    }

    public void setPointerVisible(boolean visible) {
        this.isVisible = visible;
        invalidate();
    }

    public boolean isPointerVisible() {
        return isVisible;
    }

    public void togglePointerVisible() {
        setPointerVisible(!isVisible);
    }

    public void resetToCenter() {
        cursorX = getWidth() > 0 ? getWidth() / 2f : 960;
        cursorY = getHeight() > 0 ? getHeight() / 2f : 540;
        invalidate();
    }

    public void moveBy(float dx, float dy) {
        float oldX = cursorX;
        float oldY = cursorY;
        float maxW = getWidth() > 2 ? getWidth() - 2 : 1278;
        float maxH = getHeight() > 2 ? getHeight() - 2 : 718;
        cursorX = Math.max(0, Math.min(maxW, cursorX + dx));
        cursorY = Math.max(0, Math.min(maxH, cursorY + dy));

        // High-performance dirty-rect invalidation: Repaints only the cursor's bounding box
        // instead of the entire 1080p/720p screen, eliminating video playback cursor lag.
        int pad = 46;
        int l = (int) Math.min(oldX, cursorX) - 4;
        int t = (int) Math.min(oldY, cursorY) - 4;
        int r = (int) Math.max(oldX, cursorX) + pad;
        int b = (int) Math.max(oldY, cursorY) + pad;
        invalidate(Math.max(0, l), Math.max(0, t), Math.min((int) maxW + 6, r), Math.min((int) maxH + 6, b));
    }

    public float getCursorX() {
        return cursorX;
    }

    public float getCursorY() {
        return cursorY;
    }

    public void setFocusRect(float nx, float ny, float nw, float nh) {
        if (nx < 0f || ny < 0f || nw <= 0f || nh <= 0f) {
            hasFocusRect = false;
        } else {
            focusNx = nx;
            focusNy = ny;
            focusNw = nw;
            focusNh = nh;
            hasFocusRect = true;
        }
        invalidate();
    }

    public void clearFocusRect() {
        if (hasFocusRect) {
            hasFocusRect = false;
            invalidate();
        }
    }

    public void setFocusBoxVisible(boolean visible) {
        if (focusBoxVisible != visible) {
            focusBoxVisible = visible;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (focusBoxVisible && hasFocusRect && getWidth() > 0 && getHeight() > 0) {
            float w = getWidth();
            float h = getHeight();
            float left = Math.max(2f, focusNx * w - 3f);
            float top = Math.max(2f, focusNy * h - 3f);
            float right = Math.min(w - 2f, (focusNx + focusNw) * w + 3f);
            float bottom = Math.min(h - 2f, (focusNy + focusNh) * h + 3f);
            if (right > left + 4f && bottom > top + 4f) {
                focusDrawRect.set(left, top, right, bottom);
                float radius = Math.min(18f, Math.min(focusDrawRect.width(), focusDrawRect.height()) * 0.28f);
                canvas.drawRoundRect(focusDrawRect, radius, radius, focusGlowPaint);
                canvas.drawRoundRect(focusDrawRect, radius, radius, focusStrokePaint);
            }
        }
        if (!isVisible) return;

        canvas.save();
        canvas.translate(cursorX, cursorY);
        // Draw shadow/border
        canvas.drawPath(cursorPath, shadowPaint);
        // Draw main cursor arrow
        canvas.drawPath(cursorPath, cursorPaint);
        canvas.restore();
    }
}
