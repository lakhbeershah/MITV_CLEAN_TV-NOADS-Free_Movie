package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An auto-looping photo carousel that scans the Download directory
 * and smoothly slides through photos with seamless edge-to-edge motion.
 */
public class PhotoSlider extends FrameLayout {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<File> photoFiles = new ArrayList<>();

    private FrameLayout container;
    private ImageView imageA, imageB;
    private TextView caption;
    private View border;
    private ValueAnimator slideAnimator;

    private boolean isAActive = true;
    private int currentIndex = -1;
    private boolean isRunning = false;
    private boolean isAnimating = false;

    private final Runnable slideNextRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            slideNext();
            handler.postDelayed(this, 5000L);
        }
    };

    public PhotoSlider(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        int radius = Math.round(18 * density);

        setFocusable(true);
        setFocusableInTouchMode(true);
        setClipChildren(false);

        container = new FrameLayout(context);
        container.setClipToOutline(true);
        container.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        container.setBackgroundColor(0xFF151820);

        imageA = new ImageView(context);
        imageA.setScaleType(ImageView.ScaleType.CENTER_CROP);
        container.addView(imageA, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        imageB = new ImageView(context);
        imageB.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageB.setVisibility(View.GONE);
        container.addView(imageB, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View gradient = new View(context);
        gradient.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xCC000000, 0x66000000, 0x00000000}));
        LayoutParams gradLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(55 * density));
        gradLp.gravity = Gravity.BOTTOM;
        container.addView(gradient, gradLp);

        caption = new TextView(context);
        caption.setTextColor(0xFFFFFFFF);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        caption.setSingleLine(true);
        caption.setPadding(Math.round(16 * density), 0, Math.round(16 * density), Math.round(10 * density));
        LayoutParams capLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        capLp.gravity = Gravity.BOTTOM;
        container.addView(caption, capLp);

        addView(container, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        border = new View(context);
        GradientDrawable borderDrawable = new GradientDrawable();
        borderDrawable.setCornerRadius(radius);
        borderDrawable.setStroke(Math.round(1.5f * density), 0x26FFFFFF);
        border.setBackground(borderDrawable);
        addView(border, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable d = (GradientDrawable) border.getBackground();
            if (hasFocus) {
                d.setStroke(Math.round(2f * density), 0xFFFFFFFF);
                animate().scaleX(1.015f).scaleY(1.015f).setDuration(180).start();
            } else {
                d.setStroke(Math.round(1.5f * density), 0x26FFFFFF);
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

            handler.post(() -> {
                photoFiles.clear();
                photoFiles.addAll(found);
                if (!photoFiles.isEmpty()) {
                    if (currentIndex < 0 || currentIndex >= photoFiles.size()) currentIndex = 0;
                    showPhotoAt(currentIndex);
                }
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

    private void showPhotoAt(int index) {
        if (photoFiles.isEmpty() || index < 0 || index >= photoFiles.size()) return;
        File file = photoFiles.get(index);
        new Thread(() -> {
            Bitmap bmp = decodeSampledBitmap(file.getAbsolutePath(), 960, 400);
            if (bmp != null) {
                handler.post(() -> {
                    ImageView active = isAActive ? imageA : imageB;
                    ImageView inactive = isAActive ? imageB : imageA;
                    active.setImageBitmap(bmp);
                    active.setTranslationX(0f);
                    active.setVisibility(View.VISIBLE);
                    inactive.setVisibility(View.GONE);
                    caption.setText(formatCaption(file.getName(), index));
                });
            }
        }).start();
    }

    private void slideNext() {
        if (isAnimating || photoFiles.size() <= 1) return;
        int nextIndex = (currentIndex + 1) % photoFiles.size();
        File nextFile = photoFiles.get(nextIndex);

        isAnimating = true;
        new Thread(() -> {
            Bitmap bmp = decodeSampledBitmap(nextFile.getAbsolutePath(), 960, 400);
            if (bmp == null) {
                handler.post(() -> {
                    currentIndex = nextIndex;
                    isAnimating = false;
                });
                return;
            }

            handler.post(() -> {
                currentIndex = nextIndex;
                ImageView activeView = isAActive ? imageA : imageB;
                ImageView incomingView = isAActive ? imageB : imageA;

                int w = getWidth() > 0 ? getWidth() : container.getWidth();
                if (w <= 0) w = 960;
                final int slideW = w;

                incomingView.setImageBitmap(bmp);
                incomingView.setTranslationX(slideW);
                incomingView.setVisibility(View.VISIBLE);
                activeView.setTranslationX(0f);
                activeView.setVisibility(View.VISIBLE);

                caption.setText(formatCaption(nextFile.getName(), nextIndex));

                if (slideAnimator != null) slideAnimator.cancel();
                slideAnimator = ValueAnimator.ofFloat(0f, 1f);
                slideAnimator.setDuration(600);
                slideAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                slideAnimator.addUpdateListener(animation -> {
                    float f = (float) animation.getAnimatedValue();
                    activeView.setTranslationX(-slideW * f);
                    incomingView.setTranslationX(slideW * (1f - f));
                    if (f >= 1.0f) {
                        activeView.setVisibility(View.GONE);
                        activeView.setTranslationX(0f);
                        isAActive = !isAActive;
                        isAnimating = false;
                    }
                });
                slideAnimator.start();
            });
        }).start();
    }

    private String formatCaption(String f, int i) {
        int dot = f.lastIndexOf('.');
        String name = dot > 0 ? f.substring(0, dot) : f;
        return "✨ " + name.replace('_', ' ').replace('-', ' ') + " (" + (i + 1) + "/" + photoFiles.size() + ")";
    }

    private Bitmap decodeSampledBitmap(String path, int reqW, int reqH) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);

            int inSampleSize = 1;
            while ((opts.outHeight / inSampleSize) >= (reqH * 2) || (opts.outWidth / inSampleSize) >= (reqW * 2)) {
                inSampleSize *= 2;
            }
            opts.inSampleSize = inSampleSize;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            opts.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(path, opts);
        } catch (Throwable t) {
            return null;
        }
    }

    public void startLoop() {
        if (!isRunning) {
            isRunning = true;
            handler.removeCallbacks(slideNextRunnable);
            handler.postDelayed(slideNextRunnable, 5000L);
        }
    }

    public void stopLoop() {
        isRunning = false;
        handler.removeCallbacks(slideNextRunnable);
        if (slideAnimator != null) slideAnimator.cancel();
        isAnimating = false;
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
