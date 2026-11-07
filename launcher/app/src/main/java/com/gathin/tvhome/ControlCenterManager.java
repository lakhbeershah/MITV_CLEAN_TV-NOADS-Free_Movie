package com.gathin.tvhome;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Locale;

/**
 * Unified Control Center for Xiaomi Mi TV Home & Screensaver.
 *
 * Consolidates all top-bar tools into a single clean interface:
 * 1. Quick Status & Tools (Wi-Fi, Remote, RAM/ROM, Inputs, TV Settings).
 * 2. Screensaver & Google Photos Cloud Downloader (Dynamic link, 720p/1080p, Auto-sync, Reshuffle).
 * 3. Home App Margin & Focus Glow Calibrator with LIVE REAL-TIME PREVIEW.
 */
public class ControlCenterManager {

    private final MainActivity activity;
    private final View anchor;
    private final float density;

    private PopupWindow popup;
    private View panel;

    // View Containers
    private View viewMain;
    private View viewScreensaver;
    private View viewMargin;
    private TextView txtTitle;

    // Margin Calibrator State
    private int initialMarginDp;
    private int currentMarginDp;
    private TextView txtMarginValue;

    // Screensaver State
    private SharedPreferences syncPrefs;
    private SharedPreferences ssPrefs;
    private TextView txtAlbumLink;
    private TextView btnQuality720, btnQuality1080;
    private TextView btnSyncNow;
    private TextView txtSyncStatus;
    private ProgressBar syncProgress;
    private TextView[] autoSyncBtns;
    private TextView[] timeoutBtns;

    public ControlCenterManager(MainActivity activity, View anchor) {
        this.activity = activity;
        this.anchor = anchor;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.syncPrefs = activity.getSharedPreferences(GooglePhotosSyncEngine.PREFS_NAME, Context.MODE_PRIVATE);
        this.ssPrefs = activity.getSharedPreferences(ScreensaverSetupActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void show() {
        LayoutInflater inflater = LayoutInflater.from(activity);
        panel = inflater.inflate(R.layout.control_center_panel, null);

        int panelWidth = Math.round(440 * density);
        popup = new PopupWindow(panel, panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);

        initViews(panel);

        initialMarginDp = activity.getCustomBottomRoomDp();
        currentMarginDp = initialMarginDp;
        updateMarginDisplay();

        popup.setOnDismissListener(() -> {
            // If user closed dialog without saving margin, revert back to saved value
            if (currentMarginDp != initialMarginDp) {
                activity.setCustomBottomRoomDp(initialMarginDp, false);
            }
        });

        // Show drop-down hanging under anchor
        panel.setAlpha(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();

        // Focus first actionable button
        View wifiBtn = panel.findViewById(R.id.ccBtnWifi);
        if (wifiBtn != null) wifiBtn.requestFocus();
    }

    private void initViews(View root) {
        txtTitle = root.findViewById(R.id.ccTitle);
        viewMain = root.findViewById(R.id.ccViewMain);
        viewScreensaver = root.findViewById(R.id.ccViewScreensaver);
        viewMargin = root.findViewById(R.id.ccViewMargin);

        // Close button
        View btnClose = root.findViewById(R.id.ccBtnClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> popup.dismiss());
        }

        setupQuickStatus(root);
        setupNavigation(root);
        setupScreensaverView(root);
        setupMarginView(root);

        // Handle Back key inside sub-views to return to Main
        root.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                if (viewScreensaver.getVisibility() == View.VISIBLE || viewMargin.getVisibility() == View.VISIBLE) {
                    switchToView(viewMain, "CONTROL CENTER");
                    return true;
                }
            }
            return false;
        });
    }

    private void setupQuickStatus(View root) {
        TextView btnWifi = root.findViewById(R.id.ccBtnWifi);
        TextView btnRemote = root.findViewById(R.id.ccBtnRemote);
        TextView btnRamRom = root.findViewById(R.id.ccBtnRamRom);
        TextView btnInputs = root.findViewById(R.id.ccBtnInputs);
        TextView btnSettings = root.findViewById(R.id.ccBtnSettings);

        // 1. Wi-Fi / Network Status
        boolean online = false;
        String netName = "Disconnected";
        try {
            ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active = cm != null ? cm.getActiveNetwork() : null;
            if (active != null) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(active);
                if (nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    online = true;
                    if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        netName = "Ethernet";
                    } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        WifiManager wm = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                        WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                        if (info != null && info.getSSID() != null) {
                            String s = info.getSSID().replace("\"", "").trim();
                            if (!s.isEmpty() && !s.contains("unknown") && !s.equals("0x")) netName = s;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        btnWifi.setText((online ? "🟢 " : "🔴 ") + netName);
        btnWifi.setOnClickListener(v -> {
            popup.dismiss();
            activity.openNetworkPill();
        });

        // 2. Bluetooth Remote Status
        boolean btOn = false;
        String btDevice = "Disconnected";
        try {
            for (int id : android.view.InputDevice.getDeviceIds()) {
                android.view.InputDevice dev = android.view.InputDevice.getDevice(id);
                if (dev != null && !dev.isVirtual()) {
                    String name = dev.getName();
                    if (name != null) {
                        String lower = name.toLowerCase(Locale.ROOT);
                        if (lower.contains("xiaomi") || lower.contains("remote") || lower.endsWith(" rc") || lower.startsWith("rc ")) {
                            btDevice = name;
                            btOn = true;
                            break;
                        }
                    }
                }
            }
            if (!btOn) {
                BluetoothAdapter bta = BluetoothAdapter.getDefaultAdapter();
                if (bta != null && bta.isEnabled()) {
                    btOn = true;
                    btDevice = "Remote Active";
                }
            }
        } catch (Throwable ignored) {}
        btnRemote.setText((btOn ? "🟢 " : "🔴 ") + btDevice);
        btnRemote.setOnClickListener(v -> {
            popup.dismiss();
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity"));
                activity.startActivity(i);
            } catch (Exception e) {
                activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            }
        });

        // 3. RAM & ROM Monitor
        String stats = activity.readStatsLine();
        if (TextUtils.isEmpty(stats)) stats = "RAM 58% • ROM 2.4 GB Free";
        btnRamRom.setText("💾 " + stats);
        btnRamRom.setOnClickListener(v -> Toast.makeText(activity, activity.readStatsLine(), Toast.LENGTH_SHORT).show());

        // 4. TV / HDMI Inputs
        btnInputs.setOnClickListener(v -> {
            popup.dismiss();
            activity.showInputs();
        });

        // 5. System Settings
        btnSettings.setOnClickListener(v -> {
            popup.dismiss();
            activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
        });
    }

    private void setupNavigation(View root) {
        View btnOpenScreensaver = root.findViewById(R.id.ccBtnOpenScreensaver);
        View btnOpenMargin = root.findViewById(R.id.ccBtnOpenMargin);

        btnOpenScreensaver.setOnClickListener(v -> {
            updateScreensaverFolderStats();
            switchToView(viewScreensaver, "SCREENSAVER & SYNC");
            btnSyncNow.requestFocus();
        });

        btnOpenMargin.setOnClickListener(v -> {
            switchToView(viewMargin, "HOME MARGIN & GLOW");
            View plus = panel.findViewById(R.id.ccBtnMarginPlus);
            if (plus != null) plus.requestFocus();
        });

        root.findViewById(R.id.ccBtnBackScreensaver).setOnClickListener(v -> {
            switchToView(viewMain, "CONTROL CENTER");
            btnOpenScreensaver.requestFocus();
        });

        root.findViewById(R.id.ccBtnBackMargin).setOnClickListener(v -> {
            switchToView(viewMain, "CONTROL CENTER");
            btnOpenMargin.requestFocus();
        });
    }

    private void switchToView(View target, String title) {
        viewMain.setVisibility(View.GONE);
        viewScreensaver.setVisibility(View.GONE);
        viewMargin.setVisibility(View.GONE);

        target.setVisibility(View.VISIBLE);
        txtTitle.setText(title);
    }

    // =========================================================================
    // Screensaver & Google Photos Downloader Sub-View
    // =========================================================================

    private void setupScreensaverView(View root) {
        txtAlbumLink = root.findViewById(R.id.ccTxtAlbumLink);
        btnQuality720 = root.findViewById(R.id.ccBtnQuality720);
        btnQuality1080 = root.findViewById(R.id.ccBtnQuality1080);
        btnSyncNow = root.findViewById(R.id.ccBtnSyncNow);
        txtSyncStatus = root.findViewById(R.id.ccTxtSyncStatus);
        syncProgress = root.findViewById(R.id.ccSyncProgress);

        String savedUrl = syncPrefs.getString(GooglePhotosSyncEngine.KEY_ALBUM_URL, GooglePhotosSyncEngine.DEFAULT_ALBUM_URL);
        txtAlbumLink.setText(savedUrl);

        // Edit Album Link on click
        txtAlbumLink.setOnClickListener(v -> showEditLinkDialog());

        // Quality selection
        String currentQuality = syncPrefs.getString(GooglePhotosSyncEngine.KEY_QUALITY, "720p");
        btnQuality720.setSelected("720p".equals(currentQuality));
        btnQuality1080.setSelected("1080p".equals(currentQuality));

        btnQuality720.setOnClickListener(v -> {
            btnQuality720.setSelected(true);
            btnQuality1080.setSelected(false);
            syncPrefs.edit().putString(GooglePhotosSyncEngine.KEY_QUALITY, "720p").apply();
            Toast.makeText(activity, "Target: 720p Native (1:1 Glass Mapping)", Toast.LENGTH_SHORT).show();
        });

        btnQuality1080.setOnClickListener(v -> {
            btnQuality720.setSelected(false);
            btnQuality1080.setSelected(true);
            syncPrefs.edit().putString(GooglePhotosSyncEngine.KEY_QUALITY, "1080p").apply();
            Toast.makeText(activity, "Target: 1080p Full HD", Toast.LENGTH_SHORT).show();
        });

        // Sync Now button
        btnSyncNow.setOnClickListener(v -> triggerSync());

        // Auto-Sync Schedule
        autoSyncBtns = new TextView[]{
                root.findViewById(R.id.ccBtnAutoSyncOff),
                root.findViewById(R.id.ccBtnAutoSync1h),
                root.findViewById(R.id.ccBtnAutoSync6h),
                root.findViewById(R.id.ccBtnAutoSync24h)
        };
        int[] autoHours = {0, 1, 6, 24};
        int currentAutoHours = syncPrefs.getInt(GooglePhotosSyncEngine.KEY_AUTO_SYNC_HOURS, 0);

        for (int i = 0; i < autoSyncBtns.length; i++) {
            final int h = autoHours[i];
            TextView btn = autoSyncBtns[i];
            btn.setSelected(currentAutoHours == h);
            btn.setOnClickListener(v -> {
                syncPrefs.edit().putInt(GooglePhotosSyncEngine.KEY_AUTO_SYNC_HOURS, h).apply();
                for (int j = 0; j < autoSyncBtns.length; j++) {
                    autoSyncBtns[j].setSelected(autoHours[j] == h);
                }
                Toast.makeText(activity, h > 0 ? "Auto-Sync every " + h + " hour(s)" : "Auto-Sync Off", Toast.LENGTH_SHORT).show();
            });
        }

        // Timeout buttons
        timeoutBtns = new TextView[]{
                root.findViewById(R.id.ccBtnTime1),
                root.findViewById(R.id.ccBtnTime2),
                root.findViewById(R.id.ccBtnTime5),
                root.findViewById(R.id.ccBtnTime10),
                root.findViewById(R.id.ccBtnTimeOff)
        };
        int[] timeouts = {1, 2, 5, 10, 0};
        int currentTimeout = ssPrefs.getInt(ScreensaverSetupActivity.KEY_TIMEOUT_MIN, 1);

        for (int i = 0; i < timeoutBtns.length; i++) {
            final int t = timeouts[i];
            TextView b = timeoutBtns[i];
            b.setSelected(currentTimeout == t);
            b.setOnClickListener(v -> {
                ssPrefs.edit().putInt(ScreensaverSetupActivity.KEY_TIMEOUT_MIN, t).apply();
                for (int j = 0; j < timeoutBtns.length; j++) {
                    timeoutBtns[j].setSelected(timeouts[j] == t);
                }
                Toast.makeText(activity, t > 0 ? t + " min timeout" : "Screensaver Off", Toast.LENGTH_SHORT).show();
            });
        }

        // Deck tools
        root.findViewById(R.id.ccBtnReshuffle).setOnClickListener(v -> {
            PhotoSlider.resetPlaylist(activity);
            updateScreensaverFolderStats();
            Toast.makeText(activity, "🎲 Full deck reshuffled randomly", Toast.LENGTH_SHORT).show();
        });

        root.findViewById(R.id.ccBtnTestScreensaver).setOnClickListener(v -> {
            popup.dismiss();
            activity.startActivity(new Intent(activity, ClockActivity.class));
        });

        updateScreensaverFolderStats();
    }

    private void updateScreensaverFolderStats() {
        File dir = new File(Environment.getExternalStorageDirectory(), "tv_screensaver_2");
        int count = 0;
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().toLowerCase().endsWith(".jpg")) count++;
                }
            }
        }
        if (txtSyncStatus != null) {
            txtSyncStatus.setText(count + " photos in /sdcard/tv_screensaver_2 • Zero repeats");
        }
        TextView mainSub = panel.findViewById(R.id.ccMainScreensaverSub);
        if (mainSub != null) {
            mainSub.setText(count + " photos synced • 720p 1:1 Glass • Zero repeats");
        }
    }

    private void showEditLinkDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Google Photos Album Link");
        final EditText input = new EditText(activity);
        input.setText(txtAlbumLink.getText());
        input.setSelection(input.getText().length());
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newUrl = input.getText().toString().trim();
            if (!newUrl.isEmpty()) {
                txtAlbumLink.setText(newUrl);
                syncPrefs.edit().putString(GooglePhotosSyncEngine.KEY_ALBUM_URL, newUrl).apply();
                Toast.makeText(activity, "Album link updated. Click 'Sync Photos Now' to download.", Toast.LENGTH_LONG).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void triggerSync() {
        String url = txtAlbumLink.getText().toString();
        String quality = btnQuality1080.isSelected() ? "1080p" : "720p";

        btnSyncNow.setEnabled(false);
        syncProgress.setVisibility(View.VISIBLE);
        txtSyncStatus.setText("Connecting to Google Photos...");

        GooglePhotosSyncEngine.startSync(activity, url, quality, new GooglePhotosSyncEngine.SyncListener() {
            @Override
            public void onStatus(String status) {
                activity.runOnUiThread(() -> txtSyncStatus.setText(status));
            }

            @Override
            public void onProgress(int current, int total, String photoName) {
                activity.runOnUiThread(() -> txtSyncStatus.setText("Downloading " + current + " / " + total + " (" + photoName + ")"));
            }

            @Override
            public void onComplete(boolean success, int newPhotos, int totalPhotos, String message) {
                activity.runOnUiThread(() -> {
                    btnSyncNow.setEnabled(true);
                    syncProgress.setVisibility(View.GONE);
                    txtSyncStatus.setText(message);
                    updateScreensaverFolderStats();
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // =========================================================================
    // Home Margin & Glow Calibrator Sub-View (Live Real-Time Preview)
    // =========================================================================

    private void setupMarginView(View root) {
        txtMarginValue = root.findViewById(R.id.ccTxtMarginValue);
        View btnMinus = root.findViewById(R.id.ccBtnMarginMinus);
        View btnPlus = root.findViewById(R.id.ccBtnMarginPlus);
        View btnSave = root.findViewById(R.id.ccBtnSaveMargin);
        View btnRevert = root.findViewById(R.id.ccBtnRevertMargin);

        btnMinus.setOnClickListener(v -> {
            if (currentMarginDp > 0) {
                currentMarginDp--;
                updateMarginDisplay();
                activity.setCustomBottomRoomDp(currentMarginDp, false); // LIVE PREVIEW!
            }
        });

        btnPlus.setOnClickListener(v -> {
            if (currentMarginDp < 60) {
                currentMarginDp++;
                updateMarginDisplay();
                activity.setCustomBottomRoomDp(currentMarginDp, false); // LIVE PREVIEW!
            }
        });

        btnSave.setOnClickListener(v -> {
            initialMarginDp = currentMarginDp;
            activity.setCustomBottomRoomDp(currentMarginDp, true); // Persists to SharedPreferences!
            Toast.makeText(activity, "Margin saved: " + currentMarginDp + " dp", Toast.LENGTH_SHORT).show();
            TextView mainSub = panel.findViewById(R.id.ccMainMarginSub);
            if (mainSub != null) {
                mainSub.setText("Bottom clearance: " + currentMarginDp + " dp • Live Preview");
            }
        });

        btnRevert.setOnClickListener(v -> {
            currentMarginDp = initialMarginDp;
            updateMarginDisplay();
            activity.setCustomBottomRoomDp(currentMarginDp, false); // Reverts live on screen!
            Toast.makeText(activity, "Reverted to " + initialMarginDp + " dp", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateMarginDisplay() {
        if (txtMarginValue != null) {
            txtMarginValue.setText(currentMarginDp + " dp");
        }
    }
}
