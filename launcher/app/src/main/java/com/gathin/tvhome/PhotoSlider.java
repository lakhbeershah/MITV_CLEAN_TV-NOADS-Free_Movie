package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An auto-looping photo carousel that scans system storage
 * and smoothly slides through photos in the empty space between the clock and the dock.
 */
public class PhotoSlider extends FrameLayout {

    private static final long INTERVAL_MS = 5000L;
    private static final int ANIM_DURATION_MS = 550;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<File> photoFiles = new ArrayList<>();

    private FrameLayout container;
    private FrameLayout imageLayer;
    private ImageView imageA, imageB;
    private TextView caption;
    private View border;

    private boolean isAActive = true;
    private int currentIndex = -1;
    private boolean isRunning = false;
    private boolean isAnimating = false;

    private final Runnable slideNextRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            slideNext();
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    public PhotoSlider(@NonNull Context context) {
        super(context);
        init(context);
    }

    public PhotoSlider(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PhotoSlider(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = getResources().getDisplayMetrics().density;
        int radius = Math.round(18 * density);

        setFocusable(true);
        setFocusableInTouchMode(true);
        setClipChildren(false);
        setClipToPadding(false);

        container = new FrameLayout(context);
        container.setClipToOutline(true);
        container.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        container.setBackgroundColor(0xFF151820);

        imageLayer = new FrameLayout(context);
        imageA = new ImageView(context);
        imageA.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageLayer.addView(imageA, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        imageB = new ImageView(context);
        imageB.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageB.setVisibility(View.GONE);
        imageLayer.addView(imageB, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        container.addView(imageLayer, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View gradient = new View(context);
        GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xCC000000, 0x66000000, 0x00000000});
        gradient.setBackground(shade);
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
        borderDrawable.setColor(Color.TRANSPARENT);
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
        executor.execute(() -> {
            List<File> found = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            File[] searchDirs = new File[]{
                    new File(Environment.getExternalStorageDirectory(), "Pictures"),
                    new File(Environment.getExternalStorageDirectory(), "DCIM"),
                    new File(Environment.getExternalStorageDirectory(), "Download"),
                    Environment.getExternalStorageDirectory()
            };

            for (File dir : searchDirs) {
                if (dir != null && dir.exists() && dir.isDirectory()) {
                    scanDir(dir, found, seen, 0);
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
        });
    }

    private void scanDir(File dir, List<File> result, Set<String> seen, int depth) {
        if (depth > 2) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                scanDir(f, result, seen, depth + 1);
            } else if (f.isFile() && isImage(f.getName())) {
                String path = f.getAbsolutePath();
                if (seen.add(path)) result.add(f);
            }
        }
    }

    private boolean isImage(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    private void showPhotoAt(int index) {
        if (photoFiles.isEmpty() || index < 0 || index >= photoFiles.size()) return;
        File file = photoFiles.get(index);
        executor.execute(() -> {
            Bitmap bmp = decodeSampledBitmap(file.getAbsolutePath(), 960, 400);
            if (bmp != null) {
                handler.post(() -> {
                    ImageView active = isAActive ? imageA : imageB;
                    ImageView inactive = isAActive ? imageB : imageA;
                    active.setImageBitmap(bmp);
                    active.setTranslationX(0f);
                    active.setAlpha(1f);
                    active.setVisibility(View.VISIBLE);
                    inactive.setVisibility(View.GONE);
                    caption.setText(formatCaption(file.getName(), index));
                });
            }
        });
    }

    private void slideNext() {
        if (isAnimating || photoFiles.isEmpty()) return;
        int nextIndex = (currentIndex + 1) % photoFiles.size();
        File nextFile = photoFiles.get(nextIndex);

        isAnimating = true;
        executor.execute(() -> {
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

                incomingView.setImageBitmap(bmp);
                caption.setText(formatCaption(nextFile.getName(), nextIndex));

                int w = getWidth() > 0 ? getWidth() : container.getWidth();
                if (w <= 0) w = 960;

                incomingView.setTranslationX(w * 0.45f);
                incomingView.setAlpha(0f);
                incomingView.setVisibility(View.VISIBLE);

                activeView.animate()
                        .translationX(-w * 0.45f)
                        .alpha(0f)
                        .setDuration(ANIM_DURATION_MS)
                        .setInterpolator(new DecelerateInterpolator(1.5f))
                        .start();

                incomingView.animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setDuration(ANIM_DURATION_MS)
                        .setInterpolator(new DecelerateInterpolator(1.5f))
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                activeView.setVisibility(View.GONE);
                                activeView.setTranslationX(0f);
                                activeView.setAlpha(1f);
                                activeView.setImageDrawable(null);
                                isAActive = !isAActive;
                                isAnimating = false;
                            }
                        })
                        .start();
            });
        });
    }

    private String formatCaption(String filename, int index) {
        int dot = filename.lastIndexOf('.');
        String name = dot > 0 ? filename.substring(0, dot) : filename;
        name = name.replace('-', ' ').replace('_', ' ');
        return "✨ " + name + " (" + (index + 1) + "/" + photoFiles.size() + ")";
    }

    private Bitmap decodeSampledBitmap(String path, int reqW, int reqH) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);

            opts.inSampleSize = calculateInSampleSize(opts, reqW, reqH);
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            opts.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(path, opts);
        } catch (Throwable t) {
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight || (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }

    public void startLoop() {
        if (!isRunning) {
            isRunning = true;
            handler.removeCallbacks(slideNextRunnable);
            handler.postDelayed(slideNextRunnable, INTERVAL_MS);
        }
    }

    public void stopLoop() {
        isRunning = false;
        handler.removeCallbacks(slideNextRunnable);
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
