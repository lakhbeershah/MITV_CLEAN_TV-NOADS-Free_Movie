package com.clean.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * High-precision, analog-accelerated TV mouse cursor overlay.
 */
public class VirtualCursorView extends View {

    private float cursorX = 960;
    private float cursorY = 540;
    private boolean isVisible = false;

    private Paint cursorPaint;
    private Paint shadowPaint;
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

        cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaint.setColor(0xFF00E5FF); // Bright Cyan
        cursorPaint.setStyle(Paint.Style.FILL);

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0xCC000000);
        shadowPaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setStrokeWidth(3f);

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
        cursorX = Math.max(0, Math.min(getWidth(), cursorX + dx));
        cursorY = Math.max(0, Math.min(getHeight(), cursorY + dy));
        invalidate();
    }

    public float getCursorX() {
        return cursorX;
    }

    public float getCursorY() {
        return cursorY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
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
