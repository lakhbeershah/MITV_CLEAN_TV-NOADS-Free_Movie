package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
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
public class PhotoSlider extends FrameLayout {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<File> photoFiles = new ArrayList<>();
    private final List<Bitmap> loadedBitmaps = new ArrayList<>();

    private FrameLayout container;
    private LinearLayout strip;

    private float offset = 0f;
    private float singleSetWidth = 0f;
    private boolean isRunning = false;
    private boolean isFocused = false;

    private final Runnable scrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            if (singleSetWidth > 0 && !isFocused) {
                offset += 0.8f;
                if (offset >= singleSetWidth) offset -= singleSetWidth;
                strip.setTranslationX(-offset);
            }
            postOnAnimation(this);
        }
    };

    public PhotoSlider(Context context, AttributeSet attrs) {
        super(context, attrs);

        setFocusable(true);
        setFocusableInTouchMode(true);
        setClipChildren(false);

        container = new FrameLayout(context);
        container.setBackgroundColor(0xFF101318);

        strip = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(1000000, MeasureSpec.UNSPECIFIED), heightMeasureSpec);
            }
        };
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        container.addView(strip, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        addView(container, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setOnFocusChangeListener((v, hasFocus) -> {
            isFocused = hasFocus;
            if (hasFocus) {
                animate().scaleX(1.01f).scaleY(1.01f).setDuration(180).start();
            } else {
                animate().scaleX(1f).scaleY(1f).setDuration(180).start();
            }
        });

        setOnClickListener(v -> {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage("com.example.tvfilebox");
            if (intent != null) context.startActivity(intent);
        });

        reloadPhotos();
    }

    public void reloadPhotos() {
        new Thread(() -> {
            List<File> found = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadDir == null || !downloadDir.exists()) {
                downloadDir = new File(Environment.getExternalStorageDirectory(), "Download");
            }
            if (downloadDir.exists() && downloadDir.isDirectory()) {
                scanDir(downloadDir, found, seen, 0);
            }

            if (found.isEmpty()) {
                File picDir = new File(Environment.getExternalStorageDirectory(), "Pictures");
                if (picDir.exists() && picDir.isDirectory()) {
                    scanDir(picDir, found, seen, 0);
                }
            }

            Collections.sort(found, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

            if (found.size() > 20) {
                found = found.subList(0, 20);
            }

            float density = getResources().getDisplayMetrics().density;
            int targetH = Math.round(288 * density);

            List<Bitmap> bitmaps = new ArrayList<>();
            for (File f : found) {
                Bitmap b = decodeScaled(f.getAbsolutePath(), targetH);
                if (b != null) bitmaps.add(b);
            }

            List<File> finalFound = found;
            handler.post(() -> {
                photoFiles.clear();
                photoFiles.addAll(finalFound);
                loadedBitmaps.clear();
                loadedBitmaps.addAll(bitmaps);
                buildStrip();
            });
        }).start();
    }

    private void scanDir(File dir, List<File> result, Set<String> seen, int depth) {
        if (depth > 2) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) scanDir(f, result, seen, depth + 1);
            } else {
                String n = f.getName().toLowerCase();
                if ((n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))
                        && seen.add(f.getAbsolutePath())) {
                    result.add(f);
                }
            }
        }
    }

    private void buildStrip() {
        strip.removeAllViews();
        if (loadedBitmaps.isEmpty()) {
            singleSetWidth = 0;
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        int targetH = Math.round(288 * density);

        float setW = 0f;
        for (Bitmap bmp : loadedBitmaps) {
            int h = bmp.getHeight() > 0 ? bmp.getHeight() : targetH;
            int w = (bmp.getWidth() * targetH) / h;
            setW += w;
        }
        singleSetWidth = setW;

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int copies = Math.max(2, (int) Math.ceil((screenW * 2f) / (setW > 0 ? setW : screenW)) + 1);

        for (int c = 0; c < copies; c++) {
            for (Bitmap bmp : loadedBitmaps) {
                int h = bmp.getHeight() > 0 ? bmp.getHeight() : targetH;
                int w = Math.max(Math.round(40 * density), (bmp.getWidth() * targetH) / h);

                ImageView iv = new ImageView(getContext());
                iv.setImageBitmap(bmp);
                iv.setScaleType(ImageView.ScaleType.FIT_XY);
                strip.addView(iv, new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
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
                ExifInterface exif = new ExifInterface(path);
                int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                if (o == 6) rot = 90;
                else if (o == 8) rot = 270;
                else if (o == 3) rot = 180;
            } catch (Throwable ignored) {}

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);

            int srcH = (rot == 90 || rot == 270) ? opts.outWidth : opts.outHeight;
            int inSampleSize = 1;
            while ((srcH / inSampleSize) >= (targetH * 2)) {
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
                if (r != bmp) {
                    bmp.recycle();
                    bmp = r;
                }
            }
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    public void startLoop() {
        if (!isRunning) {
            isRunning = true;
            removeCallbacks(scrollRunnable);
            postOnAnimation(scrollRunnable);
        }
    }

    public void stopLoop() {
        isRunning = false;
        removeCallbacks(scrollRunnable);
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
