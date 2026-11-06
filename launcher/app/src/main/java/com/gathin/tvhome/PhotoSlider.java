package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Shader;
import android.media.ExifInterface;
import android.os.Environment;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A continuous, smooth-scrolling horizontal strip of photos from the Download directory.
 * Photos preserve their natural upright portrait and landscape aspect ratios at full window height,
 * with square corners and seamless edge-to-edge layout.
 */
public class PhotoSlider extends FrameLayout implements View.OnFocusChangeListener, View.OnClickListener, Runnable {

    private final List<Bitmap> loadedBitmaps = new ArrayList<>();

    private LinearLayout strip;
    private float density;

    private float offset = 0f;
    private float singleSetWidth = 0f;
    private boolean isRunning = false;
    private boolean isFocused = false;

    private final Paint paint = new Paint();
    private LinearGradient vertGrad, seamGrad;
    private int lastW = 0, lastH = 0;

    @Override
    public void run() {
        if (!isRunning) return;
        if (singleSetWidth > 0) {
            offset += 0.8f;
            if (offset >= singleSetWidth) offset -= singleSetWidth;
            strip.setTranslationX(-offset);
            invalidate();
        }
        postOnAnimation(this);
    }

    public PhotoSlider(Context context, AttributeSet attrs) {
        super(context, attrs);

        setFocusable(false);
        setFocusableInTouchMode(false);
        setClipChildren(false);
        setBackgroundColor(0xFF0E1116);

        density = getResources().getDisplayMetrics().density;
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRect(0, 0, view.getWidth(), view.getHeight());
            }
        });
        setClipToOutline(true);

        strip = new LinearLayout(context);
        addView(strip, new LayoutParams(-2, -1));

        reloadPhotos();
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
    }

    @Override
    public void onClick(View v) {
    }

    public void reloadPhotos() {
        new Thread(() -> {
            List<File> found = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            File ext = Environment.getExternalStorageDirectory();
            File[] searchDirs = {
                new File(ext, "tv_screensaver"),
                new File(ext, "Download")
            };
            for (File dir : searchDirs) {
                if (dir.isDirectory()) {
                    scanDir(dir, found, seen);
                    if (!found.isEmpty()) break;
                }
            }

            if (found.size() > 10) {
                Collections.shuffle(found);
                found = found.subList(0, 10);
            }

            int targetH = getHeight() > 0 ? getHeight() : Math.round(288 * density);
            if (targetH > 480) targetH = 480;
            List<Bitmap> bitmaps = new ArrayList<>();
            for (File f : found) {
                Bitmap b = decodeScaled(f.getPath(), targetH);
                if (b != null) bitmaps.add(b);
            }

            post(() -> {
                for (Bitmap b : loadedBitmaps) if (b != null) b.recycle();
                loadedBitmaps.clear();
                loadedBitmaps.addAll(bitmaps);
                buildStrip();
            });
        }).start();
    }

    private void scanDir(File dir, List<File> result, Set<String> seen) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.isDirectory()) {
                String n = f.getName();
                if ((n.endsWith(".jpg") || n.endsWith(".png")) && seen.add(f.getPath())) {
                    result.add(f);
                }
            }
        }
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
        if (child == strip) parentWidthMeasureSpec = MeasureSpec.makeMeasureSpec(1000000, MeasureSpec.UNSPECIFIED);
        super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed);
    }

    private void buildStrip() {
        strip.removeAllViews();
        if (loadedBitmaps.isEmpty()) {
            singleSetWidth = 0;
            return;
        }

        int targetH = getHeight() > 0 ? getHeight() : Math.round(288 * density);
        float setW = 0f;
        for (Bitmap bmp : loadedBitmaps) {
            int h = bmp.getHeight() > 0 ? bmp.getHeight() : targetH;
            setW += (bmp.getWidth() * targetH) / h;
        }
        singleSetWidth = setW;

        int copies = Math.max(2, (int) Math.ceil((getResources().getDisplayMetrics().widthPixels * 2f) / (setW > 0 ? setW : 1)) + 1);
        if (copies * setW > 10000f && setW > 0) copies = Math.max(2, (int) (10000f / setW));

        int minW = Math.round(40 * density);
        for (int c = 0; c < copies; c++) {
            for (Bitmap bmp : loadedBitmaps) {
                int h = bmp.getHeight() > 0 ? bmp.getHeight() : targetH;
                int w = Math.max(minW, (bmp.getWidth() * targetH) / h);

                ImageView iv = new ImageView(getContext());
                iv.setImageBitmap(bmp);
                iv.setScaleType(ImageView.ScaleType.FIT_XY);
                strip.addView(iv, new LinearLayout.LayoutParams(w, -1));
            }
        }

        offset = 0f;
        strip.setTranslationX(0f);
        startLoop();
    }

    private Bitmap decodeScaled(String path, int targetH) {
        try {
            int rot = 0;
            try {
                int o = new ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                if (o == 6) rot = 90;
                else if (o == 8) rot = 270;
                else if (o == 3) rot = 180;
            } catch (Throwable ignored) {}

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);

            boolean swap = (rot == 90 || rot == 270);
            int srcH = swap ? opts.outWidth : opts.outHeight;
            int srcW = swap ? opts.outHeight : opts.outWidth;
            int inSampleSize = 1;
            int limitH = (targetH * 5) / 4;
            while ((srcH / inSampleSize) > limitH || (srcW / inSampleSize) > 720) {
                inSampleSize *= 2;
            }
            opts.inSampleSize = inSampleSize;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            opts.inJustDecodeBounds = false;
            Bitmap bmp = BitmapFactory.decodeFile(path, opts);
            if (bmp == null) return null;

            if (rot != 0) {
                Matrix m = new Matrix();
                m.postRotate(rot);
                Bitmap r = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                bmp.recycle();
                return r;
            }
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    public void startLoop() {
        if (!isRunning) {
            isRunning = true;
            removeCallbacks(this);
            postOnAnimation(this);
        }
    }

    public void stopLoop() {
        isRunning = false;
        removeCallbacks(this);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        int vh = Math.round(24 * density);

        if (w != lastW || h != lastH || seamGrad == null) {
            lastW = w;
            lastH = h;
            Shader.TileMode m = Shader.TileMode.CLAMP;
            vertGrad = new LinearGradient(0, 0, 0, vh, 0xFF0E1116, 0x000E1116, m);
            seamGrad = new LinearGradient(0, 0, vh, 0, new int[]{0x000E1116, 0x380E1116, 0x000E1116}, null, m);
        }

        if (strip != null) {
            float halfSw = vh * 0.5f;
            float tx = strip.getTranslationX();
            paint.setShader(seamGrad);
            for (int i = 0, count = strip.getChildCount(); i < count; i++) {
                View child = strip.getChildAt(i);
                if (child == null) continue;
                float cx = tx + child.getLeft();
                if (cx >= -vh && cx <= w + vh) {
                    canvas.save();
                    canvas.translate(cx - halfSw, 0);
                    canvas.drawRect(0, 0, vh, h, paint);
                    canvas.restore();
                }
            }
        }

        paint.setShader(vertGrad);
        canvas.drawRect(0, 0, w, vh, paint);
        canvas.save();
        canvas.translate(0, h);
        canvas.scale(1, -1);
        canvas.drawRect(0, 0, w, vh, paint);
        canvas.restore();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startLoop();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopLoop();
    }
}
