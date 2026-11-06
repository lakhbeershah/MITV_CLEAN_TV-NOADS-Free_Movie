package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.ExifInterface;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Continuous, smooth-scrolling horizontal ambient filmstrip screensaver.
 *
 * Full-Deck Playlist Architecture:
 * - Scans all photos in tv_screensaver_2 (or fallback folders).
 * - Shuffles all photos into a randomized playlist order.
 * - Streams photos one by one: NO photo repeats until ALL photos in the album have scrolled!
 * - Once the last photo scrolls across, automatically reshuffles the entire album into a new random cycle.
 * - Current playlist index and order are persisted so returning to screensaver continues seamlessly.
 * - Memory-bounded: maintains only ~5 active decoded Bitmaps (~4MB RAM), recycling off-screen images immediately.
 */
public class PhotoSlider extends View implements View.OnFocusChangeListener, View.OnClickListener, Runnable {

    private static final String PREF_NAME = "screensaver_playlist";
    private static final String PREF_INDEX = "playlist_index";
    private static final String PREF_COUNT = "playlist_count";
    private static final String PREF_ORDER = "playlist_order";

    // Master Playlist (Shared & Persisted across screensaver sessions)
    private static List<File> sPlaylist = null;
    private static int sPlaylistIndex = 0;

    private static class SlideItem {
        final Bitmap bitmap;
        final int displayW;
        float x;

        SlideItem(Bitmap bmp, int w, float xPos) {
            this.bitmap = bmp;
            this.displayW = w;
            this.x = xPos;
        }
    }

    private final List<SlideItem> activeItems = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isLoading = new AtomicBoolean(false);

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint seamPaint = new Paint();
    private final Paint vignettePaint = new Paint();
    private final RectF destRect = new RectF();

    private float density;
    private float offset = 0f;
    private float nextAppendX = 0f;
    private boolean isRunning = false;

    private LinearGradient topVignette, seamGrad;
    private int vignetteH = 0;
    private int seamW = 0;
    private float halfSeamW = 0f;
    private int lastW = 0, lastH = 0;

    public PhotoSlider(Context context) {
        this(context, null);
    }

    public PhotoSlider(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PhotoSlider(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        setFocusable(false);
        setFocusableInTouchMode(false);
        setBackgroundColor(0xFF0E1116);

        density = getResources().getDisplayMetrics().density;
        vignetteH = Math.round(24 * density);
        seamW = Math.round(16 * density);
        halfSeamW = seamW * 0.5f;

        initPlaylist(context);
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
    }

    @Override
    public void onClick(View v) {
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0 && (w != lastW || h != lastH)) {
            lastW = w;
            lastH = h;
            topVignette = new LinearGradient(0, 0, 0, vignetteH, 0xFF0E1116, 0x000E1116, Shader.TileMode.CLAMP);
            seamGrad = new LinearGradient(0, 0, seamW, 0, new int[]{0x000E1116, 0x380E1116, 0x000E1116}, null, Shader.TileMode.CLAMP);
            checkNeedMorePhotos();
        }
    }

    @Override
    public void run() {
        if (!isRunning) return;

        // Cinematic ambient scrolling speed: ~48 pixels per second at 60fps
        offset += 0.8f;

        // 1. Recycle and prune items that have completely scrolled off screen to the left
        while (!activeItems.isEmpty()) {
            SlideItem first = activeItems.get(0);
            if (first.x + first.displayW < offset) {
                activeItems.remove(0);
                if (first.bitmap != null && !first.bitmap.isRecycled()) {
                    first.bitmap.recycle();
                }
            } else {
                break;
            }
        }

        // 2. Prevent float precision drift over days of continuous running
        if (!activeItems.isEmpty() && activeItems.get(0).x > 10000f) {
            float shift = activeItems.get(0).x;
            offset -= shift;
            nextAppendX -= shift;
            for (SlideItem item : activeItems) {
                item.x -= shift;
            }
        }

        // 3. Ensure right buffer is always stocked with upcoming photos
        checkNeedMorePhotos();

        invalidate();
        postOnAnimation(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        int targetH = h > 0 ? h : 720;

        // 1. Draw solid dark backdrop
        canvas.drawColor(0xFF0E1116);

        // 2. Draw active photo strip
        for (int i = 0; i < activeItems.size(); i++) {
            SlideItem item = activeItems.get(i);
            float screenX = item.x - offset;

            // Only draw if within or overlapping view boundaries
            if (screenX + item.displayW > 0 && screenX < w) {
                if (item.bitmap != null && !item.bitmap.isRecycled()) {
                    destRect.set(screenX, 0, screenX + item.displayW, targetH);
                    canvas.drawBitmap(item.bitmap, null, destRect, bitmapPaint);
                }

                // Draw seam shadow between adjacent photos
                if (screenX > 0 && screenX < w && seamGrad != null) {
                    seamPaint.setShader(seamGrad);
                    canvas.save();
                    canvas.translate(screenX - halfSeamW, 0);
                    canvas.drawRect(0, 0, seamW, targetH, seamPaint);
                    canvas.restore();
                }
            }
        }

        // 3. Draw cinema top and bottom vignettes
        if (topVignette != null) {
            vignettePaint.setShader(topVignette);
            canvas.drawRect(0, 0, w, vignetteH, vignettePaint);

            canvas.save();
            canvas.translate(0, h);
            canvas.scale(1, -1);
            canvas.drawRect(0, 0, w, vignetteH, vignettePaint);
            canvas.restore();
        }
    }

    private void checkNeedMorePhotos() {
        int w = getWidth();
        if (w <= 0) w = 1280;
        int targetH = getHeight() > 0 ? getHeight() : 720;

        float rightEdge = nextAppendX - offset;
        // Keep buffer filled at least 1500px past right screen edge
        if (rightEdge < w + 1500f) {
            if (isLoading.compareAndSet(false, true)) {
                final int finalTargetH = targetH;
                final Context appCtx = getContext().getApplicationContext();

                loader.execute(() -> {
                    File nextFile = getNextPhotoFile(appCtx);
                    Bitmap bmp = null;
                    if (nextFile != null) {
                        bmp = decodeScaled(nextFile.getPath(), finalTargetH);
                    }

                    final Bitmap finalBmp = bmp;
                    mainHandler.post(() -> {
                        if (finalBmp != null) {
                            int bmpH = finalBmp.getHeight() > 0 ? finalBmp.getHeight() : finalTargetH;
                            int displayW = Math.round((float) finalBmp.getWidth() * finalTargetH / bmpH);
                            if (displayW < Math.round(40 * density)) {
                                displayW = Math.round(40 * density);
                            }

                            SlideItem item = new SlideItem(finalBmp, displayW, nextAppendX);
                            nextAppendX += displayW;
                            activeItems.add(item);
                            invalidate();
                        }
                        isLoading.set(false);

                        // If still need more to fill buffer, queue next
                        if (isRunning) {
                            checkNeedMorePhotos();
                        }
                    });
                });
            }
        }
    }

    public void startLoop() {
        if (!isRunning) {
            isRunning = true;
            removeCallbacks(this);
            postOnAnimation(this);
            checkNeedMorePhotos();
        }
    }

    public void stopLoop() {
        isRunning = false;
        removeCallbacks(this);
    }

    public void reloadPhotos() {
        checkNeedMorePhotos();
        startLoop();
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
        // Free memory when leaving screensaver
        for (SlideItem item : activeItems) {
            if (item.bitmap != null && !item.bitmap.isRecycled()) {
                item.bitmap.recycle();
            }
        }
        activeItems.clear();
        nextAppendX = 0f;
        offset = 0f;
    }

    // =========================================================================
    // Master Playlist & Shuffle Management
    // =========================================================================

    public static synchronized void resetPlaylist(Context ctx) {
        sPlaylist = null;
        sPlaylistIndex = 0;
        try {
            ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply();
        } catch (Throwable ignored) {}
        initPlaylist(ctx);
    }

    private static synchronized void initPlaylist(Context ctx) {
        if (sPlaylist != null && !sPlaylist.isEmpty()) return;

        List<File> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        File ext = Environment.getExternalStorageDirectory();
        File[] searchDirs = {
            new File(ext, "tv_screensaver_2"),
            new File(ext, "tv_screensaver"),
            new File(ext, "Download")
        };
        for (File dir : searchDirs) {
            if (dir.isDirectory()) {
                scanDir(dir, found, seen);
                if (!found.isEmpty()) break;
            }
        }

        if (found.isEmpty()) {
            sPlaylist = new ArrayList<>();
            sPlaylistIndex = 0;
            return;
        }

        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int savedCount = sp.getInt(PREF_COUNT, 0);
        int savedIndex = sp.getInt(PREF_INDEX, 0);

        if (savedCount == found.size() && savedIndex >= 0 && savedIndex < found.size()) {
            String savedOrder = sp.getString(PREF_ORDER, null);
            if (savedOrder != null) {
                Map<String, File> map = new HashMap<>();
                for (File f : found) map.put(f.getName(), f);
                String[] names = savedOrder.split("\n");
                if (names.length == found.size()) {
                    List<File> restored = new ArrayList<>();
                    boolean allMatch = true;
                    for (String name : names) {
                        File match = map.get(name);
                        if (match != null) {
                            restored.add(match);
                        } else {
                            allMatch = false;
                            break;
                        }
                    }
                    if (allMatch) {
                        sPlaylist = restored;
                        sPlaylistIndex = savedIndex;
                        return;
                    }
                }
            }
        }

        // Full Deck Random Shuffle: all photos shuffled once into a master deck
        Collections.shuffle(found);
        sPlaylist = found;
        sPlaylistIndex = 0;
        savePlaylistState(ctx);
    }

    private static synchronized File getNextPhotoFile(Context ctx) {
        if (sPlaylist == null || sPlaylist.isEmpty()) {
            initPlaylist(ctx);
        }
        if (sPlaylist == null || sPlaylist.isEmpty()) return null;

        if (sPlaylistIndex >= sPlaylist.size()) {
            // Full cycle complete! Every single photo has been scrolled!
            // Reshuffle all photos into a fresh random cycle:
            Collections.shuffle(sPlaylist);
            sPlaylistIndex = 0;
            savePlaylistState(ctx);
        }

        File f = sPlaylist.get(sPlaylistIndex);
        sPlaylistIndex++;
        savePlaylistIndex(ctx);
        return f;
    }

    private static synchronized void savePlaylistState(Context ctx) {
        if (sPlaylist == null) return;
        try {
            StringBuilder sb = new StringBuilder();
            for (File f : sPlaylist) {
                sb.append(f.getName()).append('\n');
            }
            ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                    .putInt(PREF_COUNT, sPlaylist.size())
                    .putString(PREF_ORDER, sb.toString())
                    .putInt(PREF_INDEX, sPlaylistIndex)
                    .apply();
        } catch (Throwable ignored) {}
    }

    private static synchronized void savePlaylistIndex(Context ctx) {
        try {
            ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                    .putInt(PREF_INDEX, sPlaylistIndex)
                    .apply();
        } catch (Throwable ignored) {}
    }

    private static void scanDir(File dir, List<File> result, Set<String> seen) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.isDirectory()) {
                String n = f.getName().toLowerCase();
                if ((n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")) && seen.add(f.getPath())) {
                    result.add(f);
                }
            }
        }
    }

    private static Bitmap decodeScaled(String path, int targetH) {
        try {
            int rot = 0;
            try {
                int o = new ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                if (o == ExifInterface.ORIENTATION_ROTATE_90) rot = 90;
                else if (o == ExifInterface.ORIENTATION_ROTATE_180) rot = 180;
                else if (o == ExifInterface.ORIENTATION_ROTATE_270) rot = 270;
            } catch (Throwable ignored) {}

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);

            boolean swap = (rot == 90 || rot == 270);
            int srcH = swap ? opts.outWidth : opts.outHeight;
            int srcW = swap ? opts.outHeight : opts.outWidth;
            int inSampleSize = 1;
            int limitH = (targetH * 5) / 4;
            while ((srcH / inSampleSize) > limitH || (srcW / inSampleSize) > 1280) {
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
}
