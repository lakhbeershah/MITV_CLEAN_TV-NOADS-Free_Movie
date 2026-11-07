package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Autonomous Google Photos Shared Album Auto-Downloader & Sync Engine.
 *
 * Checks the user's shared album for newly added photos, downloads them in native 1080p
 * into /sdcard/tv_screensaver_2/, updates PhotoSlider playlist without repeats,
 * and notifies MainActivity to display "✨ X New Memories" for exactly 1 minute.
 */
public class PhotoSyncEngine {

    private static final String TAG = "PhotoSyncEngine";

    public static final String ACTION_TEST_MEMORIES = "com.gathin.tvhome.ACTION_TEST_MEMORIES";
    public static final String ACTION_TRIGGER_SYNC = "com.gathin.tvhome.ACTION_TRIGGER_SYNC";

    private static final String PREF_NAME = "photo_sync_prefs";
    private static final String KEY_LAST_CHECK_TIME = "last_check_ms";
    private static final String KEY_LAST_COUNT = "last_photo_count";

    // Shared album configuration
    private static final String ALBUM_URL = "https://photos.google.com/share/AF1QipOT9wqQWjoIsoRjmBabVrI7Ik4HSEE3ADEfI6BMyHrj1hXyQ7p2ipOEaJl8MidnVA?key=V2M1RzNfb21ETDljZG1WN1g3Y1ZyUHp0SnZqSHB3";
    private static final String ALBUM_ID = "AF1QipOT9wqQWjoIsoRjmBabVrI7Ik4HSEE3ADEfI6BMyHrj1hXyQ7p2ipOEaJl8MidnVA";
    private static final String AUTH_KEY = "V2M1RzNfb21ETDljZG1WN1g3Y1ZyUHp0SnZqSHB3";

    // Check interval: every 15 minutes
    private static final long CHECK_INTERVAL_MS = 15 * 60 * 1000L;

    public interface SyncListener {
        void onNewMemoriesAdded(int count);
    }

    private static final ExecutorService sWorker = Executors.newSingleThreadExecutor();
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean sIsSyncing = new AtomicBoolean(false);
    private static SyncListener sActiveListener = null;

    public static void setSyncListener(SyncListener listener) {
        sActiveListener = listener;
    }

    private static Context sAppContext = null;
    private static final Runnable sPeriodicRunnable = new Runnable() {
        @Override
        public void run() {
            if (sAppContext != null) {
                checkAndSync(sAppContext, true);
            }
            sMainHandler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    public static void startPeriodicSync(Context context) {
        if (context == null) return;
        sAppContext = context.getApplicationContext();
        sMainHandler.removeCallbacks(sPeriodicRunnable);
        sMainHandler.postDelayed(sPeriodicRunnable, CHECK_INTERVAL_MS);
    }

    public static void checkOnStartup(Context context) {
        if (context != null) {
            sAppContext = context.getApplicationContext();
        }
        checkAndSync(context, false);
    }

    public static void forceSync(Context context) {
        checkAndSync(context, true);
    }

    public static void checkAndSync(Context context, boolean force) {
        if (context == null) return;
        final Context appCtx = context.getApplicationContext();

        SharedPreferences sp = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long lastCheck = sp.getLong(KEY_LAST_CHECK_TIME, 0);
        long now = System.currentTimeMillis();

        if (!force && (now - lastCheck < CHECK_INTERVAL_MS)) {
            Log.d(TAG, "Skipping sync: last check was " + ((now - lastCheck) / 60000) + " mins ago");
            return;
        }

        if (!sIsSyncing.compareAndSet(false, true)) {
            Log.d(TAG, "Sync already in progress, skipping.");
            return;
        }

        sWorker.execute(() -> {
            try {
                Log.i(TAG, "Starting Google Photos album check...");
                int newCount = performSync(appCtx);
                appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
                        .apply();

                if (newCount > 0) {
                    Log.i(TAG, "Successfully downloaded " + newCount + " new memories!");
                    PhotoSlider.resetPlaylist(appCtx);
                    sMainHandler.post(() -> {
                        if (sActiveListener != null) {
                            sActiveListener.onNewMemoriesAdded(newCount);
                        }
                    });
                } else {
                    Log.i(TAG, "No new photos found in album.");
                }
            } catch (Throwable t) {
                Log.e(TAG, "Error during photo sync", t);
            } finally {
                sIsSyncing.set(false);
            }
        });
    }

    private static class PhotoItem {
        final String id;
        final String baseUrl;

        PhotoItem(String id, String baseUrl) {
            this.id = id;
            this.baseUrl = baseUrl;
        }
    }

    private static int performSync(Context ctx) throws Exception {
        File targetDir = new File(Environment.getExternalStorageDirectory(), "tv_screensaver_2");
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        // 1. Load known photo IDs from disk
        File idFile = new File(targetDir, ".synced_ids.txt");
        Set<String> knownIds = new HashSet<>();
        if (idFile.exists()) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(idFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) knownIds.add(line);
                }
            }
        }

        // 2. Fetch album page 1
        HttpURLConnection conn = (HttpURLConnection) new URL(ALBUM_URL).openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);

        String html;
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            html = baos.toString("UTF-8");
        }

        Pattern pattern = Pattern.compile("AF_initDataCallback\\(\\s*\\{key:\\s*'ds:1'.*?data:\\s*(\\[.*?\\])\\s*,\\s*sideChannel", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) {
            Log.w(TAG, "Could not find ds:1 in album HTML");
            return 0;
        }

        JSONArray data = new JSONArray(matcher.group(1));
        JSONArray page1 = data.getJSONArray(1);
        List<PhotoItem> allPhotos = new ArrayList<>();
        for (int i = 0; i < page1.length(); i++) {
            try {
                JSONArray item = page1.getJSONArray(i);
                String id = item.getString(0);
                String u = item.getJSONArray(1).getString(0);
                allPhotos.add(new PhotoItem(id, u));
            } catch (Throwable ignored) {}
        }

        String nextToken = (data.length() > 2 && !data.isNull(2)) ? data.getString(2) : null;

        // Fetch remaining pages via RPC if token exists
        while (nextToken != null && !nextToken.isEmpty()) {
            String rpcData = new JSONArray()
                    .put(ALBUM_ID)
                    .put(nextToken)
                    .put(JSONObject.NULL)
                    .put(AUTH_KEY)
                    .toString();

            JSONArray rpcItem = new JSONArray()
                    .put("snAcKc")
                    .put(rpcData)
                    .put(JSONObject.NULL)
                    .put("generic");

            JSONArray envelope = new JSONArray().put(new JSONArray().put(rpcItem));
            String postBody = "f.req=" + URLEncoder.encode(envelope.toString(), "UTF-8");

            HttpURLConnection rpcConn = (HttpURLConnection) new URL("https://photos.google.com/_/PhotosUi/data/batchexecute").openConnection();
            rpcConn.setRequestMethod("POST");
            rpcConn.setDoOutput(true);
            rpcConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
            rpcConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            rpcConn.setConnectTimeout(15000);
            rpcConn.setReadTimeout(20000);

            try (OutputStream os = rpcConn.getOutputStream()) {
                os.write(postBody.getBytes(StandardCharsets.UTF_8));
            }

            String rpcResp;
            try (InputStream in = rpcConn.getInputStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) {
                    baos.write(buf, 0, read);
                }
                rpcResp = baos.toString("UTF-8");
            }

            nextToken = null;
            String[] lines = rpcResp.split("\n");
            for (String l : lines) {
                l = l.trim();
                if (l.startsWith("[")) {
                    try {
                        JSONArray outer = new JSONArray(l);
                        String innerStr = outer.getJSONArray(0).getString(2);
                        JSONArray inner = new JSONArray(innerStr);
                        JSONArray items = inner.getJSONArray(1);
                        for (int i = 0; i < items.length(); i++) {
                            try {
                                JSONArray it = items.getJSONArray(i);
                                String id = it.getString(0);
                                String u = it.getJSONArray(1).getString(0);
                                allPhotos.add(new PhotoItem(id, u));
                            } catch (Throwable ignored) {}
                        }
                        if (inner.length() > 2 && !inner.isNull(2)) {
                            nextToken = inner.getString(2);
                        }
                    } catch (Throwable ignored) {}
                    break;
                }
            }
        }

        Log.i(TAG, "Total photos found in album: " + allPhotos.size());

        // Initial Seed: If knownIds is empty, seed with current local files count
        if (knownIds.isEmpty()) {
            File[] existing = targetDir.listFiles((dir, name) -> name.toLowerCase(Locale.US).endsWith(".jpg"));
            int existingCount = existing != null ? existing.length : 0;
            // Record existing photo IDs so we only download true additions
            for (int i = 0; i < allPhotos.size() && i < existingCount; i++) {
                knownIds.add(allPhotos.get(i).id);
            }
            saveKnownIds(idFile, knownIds);
            if (allPhotos.size() <= existingCount) {
                return 0;
            }
        }

        // 3. Find newly added photos
        List<PhotoItem> toDownload = new ArrayList<>();
        for (PhotoItem item : allPhotos) {
            if (!knownIds.contains(item.id)) {
                toDownload.add(item);
            }
        }

        if (toDownload.isEmpty()) {
            return 0;
        }

        Log.i(TAG, "Found " + toDownload.size() + " NEW photos to download in 1080p!");

        // 4. Determine next photo sequence number
        File[] existing = targetDir.listFiles((dir, name) -> name.toLowerCase(Locale.US).endsWith(".jpg"));
        int nextIndex = 1;
        if (existing != null) {
            for (File f : existing) {
                String n = f.getName();
                if (n.startsWith("photo_") && n.endsWith(".jpg")) {
                    try {
                        int num = Integer.parseInt(n.substring(6, n.length() - 4));
                        if (num >= nextIndex) nextIndex = num + 1;
                    } catch (Throwable ignored) {}
                }
            }
        }

        // 5. Download each new photo in native 1080p
        int downloaded = 0;
        for (PhotoItem p : toDownload) {
            String url1080p = p.baseUrl + "=w1920-h1080-no";
            String filename = String.format(Locale.US, "photo_%04d.jpg", nextIndex++);
            File outFile = new File(targetDir, filename);
            File tmpFile = new File(targetDir, filename + ".tmp");

            boolean ok = false;
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    HttpURLConnection imgConn = (HttpURLConnection) new URL(url1080p).openConnection();
                    imgConn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    imgConn.setConnectTimeout(15000);
                    imgConn.setReadTimeout(20000);
                    try (InputStream in = imgConn.getInputStream();
                         FileOutputStream fos = new FileOutputStream(tmpFile)) {
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = in.read(buf)) != -1) {
                            fos.write(buf, 0, r);
                        }
                    }
                    if (tmpFile.length() > 1000) {
                        if (tmpFile.renameTo(outFile)) {
                            ok = true;
                            knownIds.add(p.id);
                            downloaded++;
                            break;
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Download attempt failed for " + filename + ": " + t.getMessage());
                }
            }
            if (!ok && tmpFile.exists()) {
                tmpFile.delete();
            }
        }

        saveKnownIds(idFile, knownIds);
        return downloaded;
    }

    private static void saveKnownIds(File file, Set<String> ids) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            for (String id : ids) {
                fos.write((id + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error saving known IDs", t);
        }
    }
}
