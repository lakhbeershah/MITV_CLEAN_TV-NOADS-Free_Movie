package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Environment;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance, lightweight Google Photos cloud sync engine for Android TV.
 *
 * Capabilities:
 * - Accepts short links (photos.app.goo.gl/...) or direct album links.
 * - Parses Google Photos shared album payloads (initial batch + batchexecute RPC pagination).
 * - Tracks downloaded photos by unique media keys so new photos are appended without duplicates.
 * - Downloads with exact 1:1 720p (=w1280-h720-no) or 1080p quality.
 * - Automatically triggers PhotoSlider playlist reset so newly added photos immediately appear in screensaver.
 */
public class GooglePhotosSyncEngine {

    private static final String TAG = "GooglePhotosSync";
    public static final String PREFS_NAME = "google_photos_sync_prefs";
    public static final String KEY_ALBUM_URL = "album_url";
    public static final String KEY_QUALITY = "photo_quality"; // "720p" or "1080p"
    public static final String KEY_AUTO_SYNC_HOURS = "auto_sync_hours"; // 0 (off), 1, 6, 24
    public static final String KEY_LAST_SYNC_TIME = "last_sync_time";
    public static final String KEY_SYNCED_COUNT = "synced_count";

    public static final String DEFAULT_ALBUM_URL =
            "https://photos.google.com/share/AF1QipOT9wqQWjoIsoRjmBabVrI7Ik4HSEE3ADEfI6BMyHrj1hXyQ7p2ipOEaJl8MidnVA?key=V2M1RzNfb21ETDljZG1WN1g3Y1ZyUHp0SnZqSHB3";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    public interface SyncListener {
        void onStatus(String status);
        void onProgress(int current, int total, String photoName);
        void onComplete(boolean success, int newPhotos, int totalPhotos, String message);
    }

    public static class PhotoItem {
        public String mediaKey;
        public String baseUrl;
        public int origW;
        public int origH;
    }

    public static String getSystemDisplayResolution(Context context) {
        if (context == null) return "720p";
        try {
            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int width = Math.max(dm.widthPixels, dm.heightPixels);
            int height = Math.min(dm.widthPixels, dm.heightPixels);
            if (width >= 1920 || height >= 1080) {
                return "1080p";
            } else if (width >= 1280 || height >= 720) {
                return "720p";
            }
        } catch (Throwable ignored) {}
        return "720p";
    }

    public static File getScreensaverDir(Context context) {
        File dir = new File(Environment.getExternalStorageDirectory(), "tv_screensaver_2");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static void startSync(Context context, String albumUrl, String quality, SyncListener listener) {
        startSync(context, albumUrl, quality, null, listener);
    }

    public static void startSync(Context context, String albumUrl, String quality, String albumSubfolder, SyncListener listener) {
        new Thread(() -> {
            try {
                if (albumUrl == null || albumUrl.trim().isEmpty()) {
                    if (listener != null) listener.onComplete(false, 0, 0, "Album URL is empty");
                    return;
                }

                if (listener != null) listener.onStatus("Connecting to Google Photos...");

                // 1. Resolve short URL redirect if needed
                String directUrl = resolveDirectUrl(albumUrl.trim());
                Log.d(TAG, "Direct URL: " + directUrl);

                // 2. Fetch Album HTML
                if (listener != null) listener.onStatus("Fetching album details...");
                String html = fetchUrl(directUrl);
                if (html == null || html.isEmpty()) {
                    if (listener != null) listener.onComplete(false, 0, 0, "Failed to load album page");
                    return;
                }

                // 3. Extract items from HTML and Batchexecute RPC
                if (listener != null) listener.onStatus("Analyzing photos in album...");
                List<PhotoItem> albumPhotos = extractAllPhotos(directUrl, html);
                Log.d(TAG, "Total photos discovered in album: " + albumPhotos.size());

                if (albumPhotos.isEmpty()) {
                    if (listener != null) listener.onComplete(false, 0, 0, "No photos found in album");
                    return;
                }

                // 4. Prepare local directory
                File baseDir = new File(Environment.getExternalStorageDirectory(), "tv_screensaver_2");
                File targetDir = (albumSubfolder != null && !albumSubfolder.trim().isEmpty() && !"all".equalsIgnoreCase(albumSubfolder))
                        ? new File(baseDir, albumSubfolder.trim())
                        : baseDir;
                if (!targetDir.exists()) {
                    targetDir.mkdirs();
                }

                // 5. Load or build existing manifest mapping (mediaKey -> local filename)
                Map<String, String> existingManifest = loadManifest(targetDir, albumPhotos);

                // Also scan existing files to find highest file index
                int maxIndex = 0;
                File[] existingFiles = targetDir.listFiles();
                if (existingFiles != null) {
                    for (File f : existingFiles) {
                        String name = f.getName().toLowerCase();
                        if (name.startsWith("photo_") && name.endsWith(".jpg")) {
                            try {
                                String numPart = name.substring(6, name.length() - 4);
                                int num = Integer.parseInt(numPart);
                                if (num > maxIndex) maxIndex = num;
                            } catch (Exception ignored) {}
                        }
                    }
                }

                // 6. Identify new photos that need downloading
                List<PhotoItem> toDownload = new ArrayList<>();
                for (PhotoItem p : albumPhotos) {
                    if (!existingManifest.containsKey(p.mediaKey)) {
                        toDownload.add(p);
                    }
                }

                Log.d(TAG, "New photos to download: " + toDownload.size() + ", already present: " + (albumPhotos.size() - toDownload.size()));

                String targetQuality = getSystemDisplayResolution(context);
                String qualityParam = "1080p".equalsIgnoreCase(targetQuality) ? "=w1920-h1080-no" : "=w1280-h720-no";
                int downloadedCount = 0;
                int failedCount = 0;

                if (!toDownload.isEmpty()) {
                    if (listener != null) listener.onStatus("Downloading " + toDownload.size() + " new photos...");

                    for (int i = 0; i < toDownload.size(); i++) {
                        PhotoItem item = toDownload.get(i);
                        maxIndex++;
                        String fileName = String.format("photo_%03d.jpg", maxIndex);
                        File destFile = new File(targetDir, fileName);

                        if (listener != null) {
                            listener.onProgress(i + 1, toDownload.size(), fileName);
                        }

                        boolean ok = downloadAndVerifyPhoto(item.baseUrl + qualityParam, destFile);
                        if (ok) {
                            downloadedCount++;
                            existingManifest.put(item.mediaKey, fileName);
                            // Save manifest incrementally so progress is preserved immediately
                            saveManifest(targetDir, existingManifest);
                        } else {
                            failedCount++;
                        }
                    }

                    // Refresh Screensaver deck immediately
                    PhotoSlider.resetPlaylist(context);
                }

                // Count total files now
                int finalTotal = 0;
                File[] finalFiles = targetDir.listFiles();
                if (finalFiles != null) {
                    for (File f : finalFiles) {
                        if (f.getName().toLowerCase().endsWith(".jpg")) finalTotal++;
                    }
                }

                // Save last sync info in preferences
                SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                sp.edit()
                        .putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                        .putInt(KEY_SYNCED_COUNT, finalTotal)
                        .apply();

                if (listener != null) {
                    String msg = downloadedCount > 0
                            ? "Downloaded " + downloadedCount + " new photos! Total: " + finalTotal + " photos."
                            : "All " + finalTotal + " photos are already up-to-date.";
                    listener.onComplete(true, downloadedCount, finalTotal, msg);
                }

            } catch (Exception e) {
                Log.e(TAG, "Sync error", e);
                if (listener != null) {
                    listener.onComplete(false, 0, 0, "Sync failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private static String resolveDirectUrl(String initialUrl) {
        if (!initialUrl.contains("photos.app.goo.gl/")) {
            return initialUrl;
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(initialUrl).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = conn.getHeaderField("Location");
                if (loc != null && !loc.isEmpty()) {
                    return loc;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Redirect resolution error", e);
        }
        return initialUrl;
    }

    private static String fetchUrl(String urlStr) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Fetch URL error", e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static List<PhotoItem> extractAllPhotos(String albumUrl, String html) {
        List<PhotoItem> results = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        String continuationToken = null;
        String albumId = null;
        String authKey = null;

        Matcher keyMatcher = Pattern.compile("key=([^&]+)").matcher(albumUrl);
        if (keyMatcher.find()) {
            authKey = keyMatcher.group(1);
        }

        // Try extracting AF_initDataCallback with ds:1
        try {
            Pattern p = Pattern.compile("AF_initDataCallback\\(\\{key:\\s*'ds:1'[^}]*data:\\s*(\\[.*?\\])\\s*,\\s*sideChannel:", Pattern.DOTALL);
            Matcher m = p.matcher(html);
            if (m.find()) {
                JSONArray data = new JSONArray(m.group(1));
                if (data.length() > 1 && !data.isNull(1)) {
                    JSONArray batch1 = data.getJSONArray(1);
                    for (int i = 0; i < batch1.length(); i++) {
                        JSONArray item = batch1.getJSONArray(i);
                        String mediaKey = item.getString(0);
                        JSONArray meta = item.getJSONArray(1);
                        String baseUrl = meta.getString(0);
                        int w = meta.optInt(1, 1280);
                        int h = meta.optInt(2, 720);

                        if (seenKeys.add(mediaKey)) {
                            PhotoItem pi = new PhotoItem();
                            pi.mediaKey = mediaKey;
                            pi.baseUrl = baseUrl;
                            pi.origW = w;
                            pi.origH = h;
                            results.add(pi);
                        }
                    }
                }
                if (data.length() > 2 && !data.isNull(2)) {
                    continuationToken = data.getString(2);
                }
                if (data.length() > 3 && !data.isNull(3)) {
                    JSONArray metaArr = data.getJSONArray(3);
                    if (metaArr.length() > 0) {
                        albumId = metaArr.getString(0);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing initial data callback", e);
        }

        // If continuation token exists, fetch batch 2 via RPC
        if (continuationToken != null && !continuationToken.isEmpty() && albumId != null && authKey != null) {
            try {
                String rpcUrl = "https://photos.google.com/_/PhotosUi/data/batchexecute";
                JSONArray innerArr = new JSONArray();
                innerArr.put(albumId);
                innerArr.put(continuationToken);
                innerArr.put(JSONObject.NULL);
                innerArr.put(authKey);

                JSONArray outer = new JSONArray();
                JSONArray callArr = new JSONArray();
                JSONArray single = new JSONArray();
                single.put("snAcKc");
                single.put(innerArr.toString());
                single.put(JSONObject.NULL);
                single.put("generic");
                callArr.put(single);
                outer.put(callArr);

                String postBody = "f.req=" + URLEncoder.encode(outer.toString(), "UTF-8");

                HttpURLConnection conn = (HttpURLConnection) new URL(rpcUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
                conn.setRequestProperty("Referer", albumUrl);
                conn.setConnectTimeout(25000);
                conn.setReadTimeout(25000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postBody.getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty() || line.startsWith(")]}'")) continue;
                            try {
                                JSONArray respArr = new JSONArray(line);
                                if (respArr.length() > 0 && !respArr.isNull(0)) {
                                    JSONArray item0 = respArr.getJSONArray(0);
                                    if (item0.length() > 2 && "snAcKc".equals(item0.getString(1))) {
                                        String payloadStr = item0.getString(2);
                                        JSONArray payload = new JSONArray(payloadStr);
                                        if (payload.length() > 1 && !payload.isNull(1)) {
                                            JSONArray batch2 = payload.getJSONArray(1);
                                            for (int i = 0; i < batch2.length(); i++) {
                                                JSONArray item = batch2.getJSONArray(i);
                                                String mediaKey = item.getString(0);
                                                JSONArray meta = item.getJSONArray(1);
                                                String baseUrl = meta.getString(0);
                                                int w = meta.optInt(1, 1280);
                                                int h = meta.optInt(2, 720);

                                                if (seenKeys.add(mediaKey)) {
                                                    PhotoItem pi = new PhotoItem();
                                                    pi.mediaKey = mediaKey;
                                                    pi.baseUrl = baseUrl;
                                                    pi.origW = w;
                                                    pi.origH = h;
                                                    results.add(pi);
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching batch 2 RPC", e);
            }
        }

        // Regex fallback if structured callbacks were incomplete
        if (results.size() < 10) {
            Pattern pwPat = Pattern.compile("\"(https://lh3\\.googleusercontent\\.com/pw/[^\"]+)\"");
            Matcher pwm = pwPat.matcher(html);
            while (pwm.find()) {
                String full = pwm.group(1);
                String base = full.split("=")[0];
                String idPart = base.substring(base.lastIndexOf('/') + 1);
                if (idPart.length() > 50 && seenKeys.add(idPart)) {
                    PhotoItem pi = new PhotoItem();
                    pi.mediaKey = idPart;
                    pi.baseUrl = base;
                    pi.origW = 1280;
                    pi.origH = 720;
                    results.add(pi);
                }
            }
        }

        return results;
    }

    private static boolean downloadAndVerifyPhoto(String urlStr, File destFile) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            if (conn.getResponseCode() != 200) {
                return false;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream is = conn.getInputStream()) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) {
                    baos.write(buf, 0, len);
                }
            }
            byte[] bytes = baos.toByteArray();
            if (bytes.length < 1000) return false;

            // Verify JPEG header & validity
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                return false;
            }

            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                fos.write(bytes);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Download photo failed for: " + urlStr, e);
            if (destFile.exists()) destFile.delete();
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static Map<String, String> loadManifest(File targetDir, List<PhotoItem> albumPhotos) {
        Map<String, String> map = new HashMap<>();
        File f = new File(targetDir, ".sync_manifest.json");
        if (!f.exists()) {
            // If manifest doesn't exist yet, map existing photo files in order to album items
            File[] files = targetDir.listFiles();
            int existingCount = 0;
            if (files != null) {
                for (File file : files) {
                    String name = file.getName().toLowerCase();
                    if (name.startsWith("photo_") && name.endsWith(".jpg")) {
                        existingCount++;
                    }
                }
            }
            if (existingCount > 0 && albumPhotos != null) {
                int limit = Math.min(existingCount, albumPhotos.size());
                for (int i = 0; i < limit; i++) {
                    String fileName = String.format("photo_%03d.jpg", i + 1);
                    map.put(albumPhotos.get(i).mediaKey, fileName);
                }
                saveManifest(targetDir, map);
            }
            return map;
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            JSONObject obj = new JSONObject(sb.toString());
            for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); ) {
                String k = it.next();
                map.put(k, obj.getString(k));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading manifest", e);
        }
        return map;
    }

    private static void saveManifest(File targetDir, Map<String, String> map) {
        File f = new File(targetDir, ".sync_manifest.json");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            JSONObject obj = new JSONObject(map);
            fos.write(obj.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "Error saving manifest", e);
        }
    }
}
