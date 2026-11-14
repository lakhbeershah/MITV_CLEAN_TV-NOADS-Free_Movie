package com.gathin.tvhome;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * Unified Control Center for Xiaomi Mi TV Home & Screensaver.
 *
 * Consolidates all top-bar tools and studio controls into a single clean interface:
 * 1. Quick Status & Tools (Wi-Fi, Remote, RAM/ROM, Inputs, TV Settings).
 * 2. Convenience Suite: Wi-Fi QR Share, Virtual Web Remote on port 8080.
 * 3. Quick System Toggles: Sleep Timer Presets, Sound Profile, Private DNS Ad-Block.
 * 4. Screensaver Studio: Google Photos Sync, Multi-Album selector/manager, Ken Burns Cinematic Motion.
 * 5. Home Layout Studio: Real-time Bottom Clearance, Column Stepper, Tile Size Scaler, Corner Radius, Wallpaper Dimming, Hidden Apps Manager.
 */
public class ControlCenterManager {

    private final MainActivity activity;
    private final View anchor;
    private final float density;

    private static ControlCenterManager activeInstance;

    public static ControlCenterManager getActiveInstance() {
        return activeInstance;
    }

    public static void dismissActive() {
        if (activeInstance != null) {
            activeInstance.dismiss();
            activeInstance = null;
        }
    }

    public PopupWindow getPopup() {
        return popup;
    }

    public boolean isOpen() {
        return popup != null && popup.isShowing();
    }

    public void dismiss() {
        if (popup != null) {
            try {
                popup.forceDismiss();
            } catch (Throwable ignored) {}
        }
        if (activeInstance == this) activeInstance = null;
    }

    private ControlCenterPopupWindow popup;
    private View panel;

    private class ControlCenterPopupWindow extends PopupWindow {
        private boolean forceDismissing = false;

        public ControlCenterPopupWindow(View contentView, int width, int height, boolean focusable) {
            super(contentView, width, height, focusable);
        }

        public void forceDismiss() {
            forceDismissing = true;
            super.dismiss();
        }

        @Override
        public void dismiss() {
            if (forceDismissing) {
                super.dismiss();
                return;
            }
            if ((viewScreensaver != null && viewScreensaver.getVisibility() == View.VISIBLE)
                    || (viewMargin != null && viewMargin.getVisibility() == View.VISIBLE)) {
                switchToView(viewMain, "CONTROL CENTER");
                return;
            }
            super.dismiss();
        }
    }

    // View Containers
    private View viewMain;
    private View viewScreensaver;
    private View viewMargin;
    private TextView txtTitle;

    // Home Studio State
    private int currentMarginPx;
    private TextView txtMarginValue;
    private int currentColumns;
    private TextView txtColsValue;
    private TextView btnScaleCompact, btnScaleStandard, btnScaleSpacious;
    private TextView btnRad0, btnRad14, btnRad20, btnRad28;
    private TextView btnDim0, btnDim25, btnDim50, btnDim75;

    // Screensaver State
    private SharedPreferences syncPrefs;
    private SharedPreferences ssPrefs;
    private SharedPreferences customPrefs;
    private TextView txtAlbumLink;
    private TextView btnSyncNow;
    private TextView txtSyncStatus;
    private ProgressBar syncProgress;
    private TextView[] autoSyncBtns;
    private TextView[] timeoutBtns;
    private TextView btnSsDim0, btnSsDim25, btnSsDim50, btnSsDim75;

    // Convenience & System Toggles
    private ImageView btnWebRemote;
    private TextView[] sleepBtns;
    private TextView[] soundBtns;
    private TextView[] dnsBtns;
    private View lastOpenedSubView;

    public ControlCenterManager(MainActivity activity, View anchor) {
        this.activity = activity;
        this.anchor = anchor;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.syncPrefs = activity.getSharedPreferences(GooglePhotosSyncEngine.PREFS_NAME, Context.MODE_PRIVATE);
        this.ssPrefs = activity.getSharedPreferences(ScreensaverSetupActivity.PREFS_NAME, Context.MODE_PRIVATE);
        this.customPrefs = activity.getSharedPreferences("home_custom_prefs", Context.MODE_PRIVATE);
    }

    public void show() {
        activeInstance = this;
        LayoutInflater inflater = LayoutInflater.from(activity);
        panel = inflater.inflate(R.layout.control_center_panel, null);

        if (panel instanceof ControlCenterRootView) {
            ((ControlCenterRootView) panel).setOnBackPressedListener(() -> {
                if ((viewScreensaver != null && viewScreensaver.getVisibility() == View.VISIBLE)
                        || (viewMargin != null && viewMargin.getVisibility() == View.VISIBLE)) {
                    switchToView(viewMain, "CONTROL CENTER");
                    return true;
                }
                dismiss();
                return true;
            });
        }

        int panelWidth = Math.round(440 * density);
        popup = new ControlCenterPopupWindow(panel, panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);

        initViews(panel);

        currentMarginPx = activity.getCustomMarginPx();
        updateMarginDisplay();

        popup.setOnDismissListener(() -> {
            if (activeInstance == this) activeInstance = null;
        });

        // Show drop-down hanging under anchor
        panel.setAlpha(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();

        // Focus first actionable button (Settings is first)
        View settingsBtn = panel.findViewById(R.id.ccBtnSettings);
        if (settingsBtn != null) settingsBtn.requestFocus();
    }

    private void initViews(View root) {
        txtTitle = root.findViewById(R.id.ccTitle);
        viewMain = root.findViewById(R.id.ccViewMain);
        viewScreensaver = root.findViewById(R.id.ccViewScreensaver);
        viewMargin = root.findViewById(R.id.ccViewMargin);



        setupQuickStatus(root);
        setupConvenienceAndToggles(root);
        setupNavigation(root);
        setupScreensaverView(root);
        setupHomeStudioView(root);

        attachBackKeyInterceptor(root);
    }

    private void attachBackKeyInterceptor(View v) {
        if (v == null) return;
        v.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_HOME) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    dismiss();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    return true;
                }
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    if ((viewScreensaver != null && viewScreensaver.getVisibility() == View.VISIBLE)
                            || (viewMargin != null && viewMargin.getVisibility() == View.VISIBLE)) {
                        switchToView(viewMain, "CONTROL CENTER");
                        return true;
                    }
                    dismiss();
                    return true;
                }
                return true;
            }
            return false;
        });
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                attachBackKeyInterceptor(vg.getChildAt(i));
            }
        }
    }

    // =========================================================================
    // 1. Quick Status & Tools
    // =========================================================================

    private void setupQuickStatus(View root) {
        ImageView btnWifi = root.findViewById(R.id.ccBtnWifi);
        ImageView btnRemote = root.findViewById(R.id.ccBtnRemote);
        TextView txtRamRom = root.findViewById(R.id.ccTxtRamRom);
        ImageView btnInputs = root.findViewById(R.id.ccBtnInputs);
        ImageView btnSettings = root.findViewById(R.id.ccBtnSettings);
        ImageView btnWifiQr = root.findViewById(R.id.ccBtnShareWifiQr);
        btnWebRemote = root.findViewById(R.id.ccBtnWebRemote);

        // 1. Wi-Fi / Ethernet Status
        boolean online = false;
        boolean isEthernet = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active = cm != null ? cm.getActiveNetwork() : null;
            if (active != null) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(active);
                if (nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    online = true;
                    if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        isEthernet = true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        if (btnWifi != null) {
            btnWifi.setImageResource(isEthernet ? R.drawable.ic_ethernet : R.drawable.ic_wifi);
            btnWifi.setColorFilter(online ? 0xFF00E676 : 0xFFFFFFFF);
            btnWifi.setSelected(online);
            btnWifi.setOnClickListener(v -> {
                dismiss();
                activity.openNetworkPill();
            });
        }

        // 2. Bluetooth Remote Status
        boolean btOn = false;
        try {
            for (int id : android.view.InputDevice.getDeviceIds()) {
                android.view.InputDevice dev = android.view.InputDevice.getDevice(id);
                if (dev != null && !dev.isVirtual()) {
                    String name = dev.getName();
                    if (name != null) {
                        String lower = name.toLowerCase(Locale.ROOT);
                        if (lower.contains("xiaomi") || lower.contains("remote") || lower.endsWith(" rc") || lower.startsWith("rc ")) {
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
                }
            }
        } catch (Throwable ignored) {}
        if (btnRemote != null) {
            btnRemote.setImageResource(R.drawable.ic_bluetooth);
            btnRemote.setColorFilter(btOn ? 0xFF00E676 : 0xFFFFFFFF);
            btnRemote.setSelected(btOn);
            btnRemote.setOnClickListener(v -> {
                dismiss();
                try {
                    Intent i = new Intent();
                    i.setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity"));
                    activity.startActivity(i);
                } catch (Exception e) {
                    activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                }
            });
        }

        // RAM & ROM Monitor in Header (purely informational, not focusable/clickable)
        if (txtRamRom != null) {
            String stats = activity.readDetailedStatsLine();
            if (TextUtils.isEmpty(stats)) stats = "RAM 58% • ROM 2.4 GB Free";
            txtRamRom.setText(stats);
            txtRamRom.setFocusable(false);
            txtRamRom.setClickable(false);
            txtRamRom.setOnClickListener(null);
        }

        // 3. TV / HDMI Inputs
        if (btnInputs != null) {
            btnInputs.setImageResource(R.drawable.ic_input);
            btnInputs.setColorFilter(0xFFFFFFFF);
            btnInputs.setOnClickListener(v -> {
                dismiss();
                activity.showInputs();
            });
        }

        // 4. System Settings
        if (btnSettings != null) {
            btnSettings.setImageResource(R.drawable.ic_settings);
            btnSettings.setColorFilter(0xFFFFFFFF);
            btnSettings.setOnClickListener(v -> {
                dismiss();
                activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
            });
        }

        // 5. Wi-Fi QR Share
        if (btnWifiQr != null) {
            btnWifiQr.setImageResource(R.drawable.ic_qr_code);
            btnWifiQr.setColorFilter(0xFFFFFFFF);
            btnWifiQr.setOnClickListener(v -> showWifiQrDialog());
        }

        // 6. Virtual Web Remote
        if (btnWebRemote != null) {
            btnWebRemote.setImageResource(R.drawable.ic_remote);
            updateWebRemoteButton();
            btnWebRemote.setOnClickListener(v -> toggleWebRemote());
        }
    }

    // =========================================================================
    // 2. Convenience Suite & System Toggles
    // =========================================================================

    private void setupConvenienceAndToggles(View root) {
        // Sleep Timer Presets (11)
        sleepBtns = new TextView[]{
                root.findViewById(R.id.ccBtnSleepOff),
                root.findViewById(R.id.ccBtnSleep15),
                root.findViewById(R.id.ccBtnSleep30),
                root.findViewById(R.id.ccBtnSleep45),
                root.findViewById(R.id.ccBtnSleep60),
                root.findViewById(R.id.ccBtnSleep90)
        };
        int[] sleepMins = {0, 15, 30, 45, 60, 90};
        updateSleepTimerDisplay();

        for (int i = 0; i < sleepBtns.length; i++) {
            final int m = sleepMins[i];
            TextView btn = sleepBtns[i];
            if (btn == null) continue;
            btn.setOnClickListener(v -> {
                SleepTimerManager.setTimer(activity, m, this::updateSleepTimerDisplay);
                updateSleepTimerDisplay();
            });
        }

        // D. Sound Profile (15)
        soundBtns = new TextView[]{
                root.findViewById(R.id.ccBtnSoundStandard),
                root.findViewById(R.id.ccBtnSoundVoice),
                root.findViewById(R.id.ccBtnSoundCinema),
                root.findViewById(R.id.ccBtnSoundMusic),
                root.findViewById(R.id.ccBtnSoundNight)
        };
        String[] soundProfiles = {"standard", "voice", "cinema", "music", "night"};
        String currentSound = customPrefs.getString("sound_profile", "standard");

        for (int i = 0; i < soundBtns.length; i++) {
            final String prof = soundProfiles[i];
            TextView btn = soundBtns[i];
            if (btn == null) continue;
            btn.setSelected(prof.equals(currentSound));
            btn.setOnClickListener(v -> {
                customPrefs.edit().putString("sound_profile", prof).apply();
                for (int j = 0; j < soundBtns.length; j++) {
                    if (soundBtns[j] != null) soundBtns[j].setSelected(soundProfiles[j].equals(prof));
                }
                String desc;
                if ("voice".equals(prof)) desc = "Voice Clarity (Crisp Dialogues)";
                else if ("cinema".equals(prof)) desc = "Cinema (Wide Dynamic Range + Bass)";
                else if ("music".equals(prof)) desc = "Music (Acoustic Fidelity)";
                else if ("night".equals(prof)) desc = "Night Mode (Normalized Peak Volume)";
                else desc = "Standard (Balanced Stereo)";
                Toast.makeText(activity, "🔊 Sound Profile: " + desc, Toast.LENGTH_SHORT).show();
            });
        }

        // E. Private DNS (22)
        dnsBtns = new TextView[]{
                root.findViewById(R.id.ccBtnDnsOff),
                root.findViewById(R.id.ccBtnDnsAdguard),
                root.findViewById(R.id.ccBtnDnsCloudflare),
                root.findViewById(R.id.ccBtnDnsGoogle)
        };
        updateDnsButtonsState();

        if (dnsBtns[0] != null) dnsBtns[0].setOnClickListener(v -> applyPrivateDns("off", null, "Off (ISP Default)"));
        if (dnsBtns[1] != null) dnsBtns[1].setOnClickListener(v -> applyPrivateDns("hostname", "dns.adguard-dns.com", "AdGuard Ad-Block"));
        if (dnsBtns[2] != null) dnsBtns[2].setOnClickListener(v -> applyPrivateDns("hostname", "one.one.one.one", "Cloudflare 1.1.1.1"));
        if (dnsBtns[3] != null) dnsBtns[3].setOnClickListener(v -> applyPrivateDns("hostname", "dns.google", "Google DNS"));
    }

    private void updateSleepTimerDisplay() {
        int sel = SleepTimerManager.getSelectedMinutes();
        int rem = SleepTimerManager.getRemainingMinutes();
        int[] sleepMins = {0, 15, 30, 45, 60, 90};


        if (sleepBtns != null) {
            for (int i = 0; i < sleepBtns.length; i++) {
                if (sleepBtns[i] != null) {
                    sleepBtns[i].setSelected(sleepMins[i] == sel);
                }
            }
        }
    }

    private void updateDnsButtonsState() {
        String active = "off";
        try {
            String mode = Settings.Global.getString(activity.getContentResolver(), "private_dns_mode");
            String spec = Settings.Global.getString(activity.getContentResolver(), "private_dns_specifier");
            if ("hostname".equalsIgnoreCase(mode) && spec != null) {
                if (spec.contains("adguard")) active = "adguard";
                else if (spec.contains("one.one") || spec.contains("cloudflare")) active = "cloudflare";
                else if (spec.contains("google")) active = "google";
            }
        } catch (Throwable ignored) {}

        if (dnsBtns != null && dnsBtns.length >= 4) {
            if (dnsBtns[0] != null) dnsBtns[0].setSelected("off".equals(active));
            if (dnsBtns[1] != null) dnsBtns[1].setSelected("adguard".equals(active));
            if (dnsBtns[2] != null) dnsBtns[2].setSelected("cloudflare".equals(active));
            if (dnsBtns[3] != null) dnsBtns[3].setSelected("google".equals(active));
        }
    }

    private void applyPrivateDns(String mode, String specifier, String label) {
        try {
            Settings.Global.putString(activity.getContentResolver(), "private_dns_mode", mode);
            if (specifier != null) {
                Settings.Global.putString(activity.getContentResolver(), "private_dns_specifier", specifier);
            }
        } catch (SecurityException se) {
            new Thread(() -> {
                try {
                    Runtime.getRuntime().exec("settings put global private_dns_mode " + mode);
                    if (specifier != null) {
                        Runtime.getRuntime().exec("settings put global private_dns_specifier " + specifier);
                    }
                } catch (Exception ignored) {}
            }).start();
        }
        updateDnsButtonsState();
        Toast.makeText(activity, "🛡️ Private DNS: " + label, Toast.LENGTH_SHORT).show();
    }

    private void showWifiQrDialog() {
        String ssid = "Home_Wi-Fi";
        try {
            WifiManager wm = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
            if (info != null && info.getSSID() != null) {
                String s = info.getSSID().replace("\"", "").trim();
                if (!s.isEmpty() && !s.contains("unknown") && !s.equals("0x")) ssid = s;
            }
        } catch (Exception ignored) {}

        String qrPayload = "WIFI:S:" + ssid + ";T:WPA;P:;;";
        try {
            QrCode qr = QrCode.encodeText(qrPayload, QrCode.Ecc.MEDIUM);
            Bitmap bmp = qr.toBitmap(8, 2);
            showQrDialog("📶 Wi-Fi Guest Connect", "Network: " + ssid + "\nScan with your phone to connect instantly", bmp);
        } catch (Exception e) {
            Toast.makeText(activity, "Unable to generate QR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateWebRemoteButton() {
        if (btnWebRemote == null) return;
        boolean running = WebRemoteServer.isRunning();
        btnWebRemote.setColorFilter(running ? 0xFF00E676 : 0xFFFFFFFF);
        btnWebRemote.setSelected(running);
    }

    private void toggleWebRemote() {
        if (!WebRemoteServer.isRunning()) {
            WebRemoteServer.startServer(activity);
            updateWebRemoteButton();
            String ip = getDeviceIp();
            String url = "http://" + ip + ":" + WebRemoteServer.PORT + "/";
            String urlAlt = "http://" + ip + ":" + WebRemoteServer.PORT_ALT + "/";
            try {
                QrCode qr = QrCode.encodeText(url, QrCode.Ecc.MEDIUM);
                Bitmap bmp = qr.toBitmap(8, 2);
                showQrDialog("📱 Virtual Web Remote", "Scan with phone to open TV remote:\n" + url + "\n(Alt: " + urlAlt + ")\n\n⚠️ Important:\n1. Phone MUST be on Excitel_2.0 (same as TV)\n2. Turn OFF Mobile Data (4G/5G) on phone", bmp);
            } catch (Exception e) {
                Toast.makeText(activity, "Web Remote active at: " + url, Toast.LENGTH_LONG).show();
            }
        } else {
            String ip = getDeviceIp();
            String url = "http://" + ip + ":" + WebRemoteServer.PORT + "/";
            String urlAlt = "http://" + ip + ":" + WebRemoteServer.PORT_ALT + "/";
            AlertDialog dialog = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Virtual Web Remote")
                    .setMessage("Server active on:\n• " + url + "\n• " + urlAlt + "\n\n⚠️ Important:\n1. Connect phone to Excitel_2.0\n2. Turn OFF Mobile Data / 5G\n3. In iOS/Android, allow Local Network")
                    .setPositiveButton("Show QR Code", (d, w) -> {
                        try {
                            QrCode qr = QrCode.encodeText(url, QrCode.Ecc.MEDIUM);
                            Bitmap bmp = qr.toBitmap(8, 2);
                            showQrDialog("📱 Virtual Web Remote", "Scan with phone:\n" + url + "\n(Alt: " + urlAlt + ")\n\n(Must be on Excitel_2.0 with Mobile Data OFF)", bmp);
                        } catch (Exception ignored) {}
                    })
                    .setNegativeButton("Stop Remote", (d, w) -> {
                        WebRemoteServer.stopServer();
                        updateWebRemoteButton();
                        Toast.makeText(activity, "Web Remote stopped", Toast.LENGTH_SHORT).show();
                    })
                    .setNeutralButton("Cancel", (d, w) -> d.dismiss())
                    .create();
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();
        }
    }

    private String getDeviceIp() {
        String ethIp = null;
        String wlanIp = null;
        String fallbackIp = null;
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                String name = intf.getName().toLowerCase(Locale.ROOT);
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        String ip = inetAddress.getHostAddress();
                        if (name.contains("eth")) ethIp = ip;
                        else if (name.contains("wlan")) wlanIp = ip;
                        else if (fallbackIp == null) fallbackIp = ip;
                    }
                }
            }
        } catch (Exception ignored) {}
        if (ethIp != null) return ethIp;
        if (wlanIp != null) return wlanIp;
        if (fallbackIp != null) return fallbackIp;
        return "192.168.1.43";
    }

    private void showQrDialog(String title, String subtitle, Bitmap qrBitmap) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (18 * density);
        layout.setPadding(pad, pad, pad, pad);
        layout.setBackgroundColor(0xFF1E242E);

        ImageView iv = new ImageView(activity);
        int sizePx = (int) (220 * density);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
        lp.bottomMargin = (int) (12 * density);
        iv.setLayoutParams(lp);
        iv.setImageBitmap(qrBitmap);
        layout.addView(iv);

        TextView sub = new TextView(activity);
        sub.setText(subtitle);
        sub.setTextColor(0xFFFFFFFF);
        sub.setTextSize(13);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.addView(sub);

        AlertDialog dialog = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(layout)
                .setPositiveButton("Done", (d, w) -> d.dismiss())
                .create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    // =========================================================================
    // 3. Navigation
    // =========================================================================

    private void setupNavigation(View root) {
        View btnOpenScreensaver = root.findViewById(R.id.ccBtnOpenScreensaver);
        View btnOpenMargin = root.findViewById(R.id.ccBtnOpenMargin);

        if (btnOpenScreensaver != null) {
            btnOpenScreensaver.setOnClickListener(v -> {
                lastOpenedSubView = viewScreensaver;
                updateScreensaverFolderStats();
                switchToView(viewScreensaver, "SCREENSAVER & SYNC");
            });
        }

        if (btnOpenMargin != null) {
            btnOpenMargin.setOnClickListener(v -> {
                lastOpenedSubView = viewMargin;
                switchToView(viewMargin, "PEACE LAUNCHER STUDIO");
                View firstFocus = panel != null ? panel.findViewById(R.id.ccBtnTileScaleStandard) : null;
                if (firstFocus == null && panel != null) firstFocus = panel.findViewById(R.id.ccBtnTileScaleCompact);
                if (firstFocus != null) firstFocus.requestFocus();
            });
        }

        View btnOpenSetupWizard = root.findViewById(R.id.ccBtnOpenSetupWizard);
        if (btnOpenSetupWizard != null) {
            btnOpenSetupWizard.setOnClickListener(v -> {
                dismiss();
                Intent wizard = new Intent(activity, SetupWizardActivity.class);
                activity.startActivity(wizard);
            });
        }
    }

    public void openScreensaverStudio() {
        lastOpenedSubView = viewScreensaver;
        updateScreensaverFolderStats();
        switchToView(viewScreensaver, "SCREENSAVER & SYNC");
    }

    public void openLauncherStudio() {
        openHomeStudio();
    }

    public void openHomeStudio() {
        if (viewMargin != null) {
            lastOpenedSubView = viewMargin;
            switchToView(viewMargin, "PEACE LAUNCHER STUDIO");
            View firstFocus = panel != null ? panel.findViewById(R.id.ccBtnTileScaleStandard) : null;
            if (firstFocus == null && panel != null) firstFocus = panel.findViewById(R.id.ccBtnTileScaleCompact);
            if (firstFocus != null) firstFocus.requestFocus();
        }
    }

    private void switchToView(View target, String title) {
        viewMain.setVisibility(View.GONE);
        viewScreensaver.setVisibility(View.GONE);
        viewMargin.setVisibility(View.GONE);

        target.setVisibility(View.VISIBLE);
        txtTitle.setText(title);

        ScrollView sv = panel != null ? panel.findViewById(R.id.ccScrollView) : null;
        if (sv != null) {
            sv.scrollTo(0, 0);
        }

        panel.post(() -> {
            if (sv != null) {
                sv.scrollTo(0, 0);
            }
            if (target == viewMargin) {
                View firstFocus = panel != null ? panel.findViewById(R.id.ccBtnTileScaleStandard) : null;
                if (firstFocus == null && panel != null) firstFocus = panel.findViewById(R.id.ccBtnTileScaleCompact);
                if (firstFocus != null) firstFocus.requestFocus();
            } else if (target == viewScreensaver) {
                if (sv != null) sv.scrollTo(0, 0);
                View editLink = panel.findViewById(R.id.ccBtnEditAlbumLink2);
                if (editLink != null) {
                    editLink.requestFocus();
                } else if (btnSyncNow != null) {
                    btnSyncNow.requestFocus();
                }
            } else {
                if (lastOpenedSubView == viewScreensaver && panel.findViewById(R.id.ccBtnOpenScreensaver) != null) {
                    panel.findViewById(R.id.ccBtnOpenScreensaver).requestFocus();
                } else if (lastOpenedSubView == viewMargin && panel.findViewById(R.id.ccBtnOpenMargin) != null) {
                    panel.findViewById(R.id.ccBtnOpenMargin).requestFocus();
                } else {
                    View settings = panel.findViewById(R.id.ccBtnSettings);
                    if (settings != null) settings.requestFocus();
                }
            }
        });
    }

    // =========================================================================
    // 4. Screensaver & Google Photos Studio Sub-View
    // =========================================================================

    private void setupScreensaverView(View root) {
        txtAlbumLink = root.findViewById(R.id.ccTxtAlbumLink);
        btnSyncNow = root.findViewById(R.id.ccBtnSyncNow);
        txtSyncStatus = root.findViewById(R.id.ccTxtSyncStatus);
        syncProgress = root.findViewById(R.id.ccSyncProgress);

        String savedUrl = syncPrefs.getString(GooglePhotosSyncEngine.KEY_ALBUM_URL, GooglePhotosSyncEngine.DEFAULT_ALBUM_URL);
        txtAlbumLink.setText(savedUrl);

        View btnEditLink2 = root.findViewById(R.id.ccBtnEditAlbumLink2);
        if (btnEditLink2 != null) {
            btnEditLink2.setOnClickListener(v -> showEditLinkDialog());
        }

        View btnQrPaste = root.findViewById(R.id.ccBtnQrPasteLink);
        if (btnQrPaste != null) {
            btnQrPaste.setOnClickListener(v -> showQrPhotoLinkDialog());
        }

        // Sync Now button
        btnSyncNow.setOnClickListener(v -> triggerSync());

        // Screensaver Photo Dimming
        btnSsDim0 = root.findViewById(R.id.ccBtnSsDim0);
        btnSsDim25 = root.findViewById(R.id.ccBtnSsDim25);
        btnSsDim50 = root.findViewById(R.id.ccBtnSsDim50);
        btnSsDim75 = root.findViewById(R.id.ccBtnSsDim75);
        int currentSsDim = customPrefs.getInt("screensaver_dim_percent", 35);
        updateSsDimSelection(currentSsDim);

        if (btnSsDim0 != null) btnSsDim0.setOnClickListener(v -> setScreensaverDimming(0));
        if (btnSsDim25 != null) btnSsDim25.setOnClickListener(v -> setScreensaverDimming(25));
        if (btnSsDim50 != null) btnSsDim50.setOnClickListener(v -> setScreensaverDimming(50));
        if (btnSsDim75 != null) btnSsDim75.setOnClickListener(v -> setScreensaverDimming(75));

        // Auto-Sync Schedule
        autoSyncBtns = new TextView[]{
                root.findViewById(R.id.ccBtnAutoSyncOff),
                root.findViewById(R.id.ccBtnAutoSync1h),
                root.findViewById(R.id.ccBtnAutoSync6h),
                root.findViewById(R.id.ccBtnAutoSyncDaily)
        };
        int[] autoHours = {0, 1, 6, 24};
        int currentAutoHours = syncPrefs.getInt(GooglePhotosSyncEngine.KEY_AUTO_SYNC_HOURS, 0);

        for (int i = 0; i < autoSyncBtns.length; i++) {
            final int h = autoHours[i];
            TextView btn = autoSyncBtns[i];
            if (btn == null) continue;
            btn.setSelected(currentAutoHours == h);
            btn.setOnClickListener(v -> {
                syncPrefs.edit().putInt(GooglePhotosSyncEngine.KEY_AUTO_SYNC_HOURS, h).apply();
                for (int j = 0; j < autoSyncBtns.length; j++) {
                    if (autoSyncBtns[j] != null) autoSyncBtns[j].setSelected(autoHours[j] == h);
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
            if (b == null) continue;
            b.setSelected(currentTimeout == t);
            b.setOnClickListener(v -> {
                ssPrefs.edit().putInt(ScreensaverSetupActivity.KEY_TIMEOUT_MIN, t).apply();
                for (int j = 0; j < timeoutBtns.length; j++) {
                    if (timeoutBtns[j] != null) timeoutBtns[j].setSelected(timeouts[j] == t);
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
            dismiss();
            activity.startActivity(new Intent(activity, ClockActivity.class));
        });

        // Screensaver Clock Style (Digital vs Analog)
        TextView btnSsClockDigital = root.findViewById(R.id.ccBtnSsStyleDigital);
        TextView btnSsClockAnalog = root.findViewById(R.id.ccBtnSsStyleAnalog);
        String currentSsStyle = customPrefs.getString(ClockHelper.KEY_SS_STYLE, "digital");
        if (btnSsClockDigital != null) {
            btnSsClockDigital.setSelected("digital".equals(currentSsStyle));
            btnSsClockDigital.setOnClickListener(v -> {
                customPrefs.edit().putString(ClockHelper.KEY_SS_STYLE, "digital").apply();
                btnSsClockDigital.setSelected(true);
                if (btnSsClockAnalog != null) btnSsClockAnalog.setSelected(false);
            });
        }
        if (btnSsClockAnalog != null) {
            btnSsClockAnalog.setSelected("analog".equals(currentSsStyle));
            btnSsClockAnalog.setOnClickListener(v -> {
                customPrefs.edit().putString(ClockHelper.KEY_SS_STYLE, "analog").apply();
                btnSsClockAnalog.setSelected(true);
                if (btnSsClockDigital != null) btnSsClockDigital.setSelected(false);
            });
        }

        // Screensaver Time Format
        TextView[] ssTimeBtns = {
                root.findViewById(R.id.ccBtnSsTime12Sec),
                root.findViewById(R.id.ccBtnSsTime12Clean),
                root.findViewById(R.id.ccBtnSsTime24Sec),
                root.findViewById(R.id.ccBtnSsTime24Clean)
        };
        String[] ssTimeFmts = {
                ClockHelper.FMT_12_SEC,
                ClockHelper.FMT_12_CLEAN,
                ClockHelper.FMT_24_SEC,
                ClockHelper.FMT_24_CLEAN
        };
        String currentSsTime = customPrefs.getString(ClockHelper.KEY_SS_TIME_FMT, ClockHelper.FMT_12_SEC);
        for (int i = 0; i < ssTimeBtns.length; i++) {
            final int idx = i;
            TextView b = ssTimeBtns[i];
            if (b == null) continue;
            b.setSelected(ssTimeFmts[i].equals(currentSsTime));
            b.setOnClickListener(v -> {
                customPrefs.edit().putString(ClockHelper.KEY_SS_TIME_FMT, ssTimeFmts[idx]).apply();
                for (int j = 0; j < ssTimeBtns.length; j++) {
                    if (ssTimeBtns[j] != null) ssTimeBtns[j].setSelected(j == idx);
                }
            });
        }

        // Screensaver Date Preset
        TextView[] ssDateBtns = {
                root.findViewById(R.id.ccBtnSsDateVerbal),
                root.findViewById(R.id.ccBtnSsDateFull),
                root.findViewById(R.id.ccBtnSsDateDmySlash),
                root.findViewById(R.id.ccBtnSsDateDmyDash),
                root.findViewById(R.id.ccBtnSsDateNone)
        };
        String[] ssDateFmts = {
                ClockHelper.DATE_VERBAL_STD,
                ClockHelper.DATE_VERBAL_FULL,
                ClockHelper.DATE_DMY_SLASH,
                ClockHelper.DATE_DMY_DASH,
                ClockHelper.DATE_HIDDEN
        };
        String currentSsDate = customPrefs.getString(ClockHelper.KEY_SS_DATE_FMT, ClockHelper.DATE_VERBAL_STD);
        for (int i = 0; i < ssDateBtns.length; i++) {
            final int idx = i;
            TextView b = ssDateBtns[i];
            if (b == null) continue;
            b.setSelected(ssDateFmts[i].equals(currentSsDate));
            b.setOnClickListener(v -> {
                customPrefs.edit().putString(ClockHelper.KEY_SS_DATE_FMT, ssDateFmts[idx]).apply();
                for (int j = 0; j < ssDateBtns.length; j++) {
                    if (ssDateBtns[j] != null) ssDateBtns[j].setSelected(j == idx);
                }
            });
        }

        // Screensaver Position
        TextView[] ssPosBtns = {
                root.findViewById(R.id.ccBtnSsPosBottomRight),
                root.findViewById(R.id.ccBtnSsPosCenter),
                root.findViewById(R.id.ccBtnSsPosTopRight),
                root.findViewById(R.id.ccBtnSsPosBottomLeft)
        };
        String[] ssPosKeys = {"bottom_right", "center", "top_right", "bottom_left"};
        String currentSsPos = customPrefs.getString(ClockHelper.KEY_SS_POS, "bottom_right");
        for (int i = 0; i < ssPosBtns.length; i++) {
            final int idx = i;
            TextView b = ssPosBtns[i];
            if (b == null) continue;
            b.setSelected(ssPosKeys[i].equals(currentSsPos));
            b.setOnClickListener(v -> {
                customPrefs.edit().putString(ClockHelper.KEY_SS_POS, ssPosKeys[idx]).apply();
                for (int j = 0; j < ssPosBtns.length; j++) {
                    if (ssPosBtns[j] != null) ssPosBtns[j].setSelected(j == idx);
                }
            });
        }

        updateScreensaverFolderStats();
    }

    private void setScreensaverDimming(int percent) {
        customPrefs.edit().putInt("screensaver_dim_percent", percent).apply();
        updateSsDimSelection(percent);
        Toast.makeText(activity, "Screensaver Dimming: " + percent + "%", Toast.LENGTH_SHORT).show();
    }

    private void updateSsDimSelection(int percent) {
        if (btnSsDim0 != null) btnSsDim0.setSelected(percent == 0);
        if (btnSsDim25 != null) btnSsDim25.setSelected(percent == 25);
        if (btnSsDim50 != null) btnSsDim50.setSelected(percent == 50);
        if (btnSsDim75 != null) btnSsDim75.setSelected(percent == 75);
    }

    private void updateScreensaverFolderStats() {
        File dir = GooglePhotosSyncEngine.getScreensaverDir(activity);
        if (dir == null) dir = new File(Environment.getExternalStorageDirectory(), "tv_screensaver_2");
        int count = countJpgs(dir, false);
        if (txtSyncStatus != null) {
            txtSyncStatus.setText(count + " photos in " + dir.getName() + " • Zero repeats");
        }
        TextView mainSub = panel != null ? panel.findViewById(R.id.ccMainScreensaverSub) : null;
        if (mainSub != null) {
            mainSub.setText(count + " photos synced • Zero repeats");
        }
    }

    private int countJpgs(File dir, boolean recursive) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File f : files) {
            if (f.isDirectory() && recursive) {
                count += countJpgs(f, true);
            } else if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".jpg")) {
                count++;
            }
        }
        return count;
    }

    public static void notifyAlbumLinkUpdated(String url) {
        if (activeInstance != null && activeInstance.txtAlbumLink != null) {
            activeInstance.activity.runOnUiThread(() -> activeInstance.txtAlbumLink.setText(url));
        }
    }

    private void showEditLinkDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("✏️ Google Photos Album Link");
        final EditText input = new EditText(activity);
        input.setText(txtAlbumLink != null ? txtAlbumLink.getText() : "");
        if (input.getText() != null) input.setSelection(input.getText().length());
        input.setTextColor(Color.WHITE);
        input.setHint("https://photos.app.goo.gl/...");
        input.setHintTextColor(Color.GRAY);
        input.setSingleLine(true);
        input.setPadding((int)(16 * density), (int)(14 * density), (int)(16 * density), (int)(14 * density));
        builder.setView(input);

        builder.setPositiveButton("Save Link", (dialog, which) -> {
            String newUrl = input.getText().toString().trim();
            if (!newUrl.isEmpty()) {
                if (txtAlbumLink != null) txtAlbumLink.setText(newUrl);
                syncPrefs.edit().putString(GooglePhotosSyncEngine.KEY_ALBUM_URL, newUrl).apply();
                Toast.makeText(activity, "Album link updated. Click 'Sync Photos Now' to download.", Toast.LENGTH_LONG).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void showQrPhotoLinkDialog() {
        if (!WebRemoteServer.isRunning()) {
            WebRemoteServer.startServer(activity);
            updateWebRemoteButton();
        }
        String ip = getDeviceIp();
        String url = "http://" + ip + ":" + WebRemoteServer.PORT + "/photo-link";
        try {
            QrCode qr = QrCode.encodeText(url, QrCode.Ecc.MEDIUM);
            Bitmap bmp = qr.toBitmap(8, 2);
            showQrDialog("📲 Paste Photos Link from Phone",
                    "Scan with phone camera to paste your Google Photos link directly into TV:\n" + url + "\n\n⚠️ Ensure phone is on the same Wi-Fi router", bmp);
        } catch (Exception e) {
            Toast.makeText(activity, "Web server running at: " + url, Toast.LENGTH_LONG).show();
        }
    }

    private void triggerSync() {
        triggerSyncForAlbum(null);
    }

    private void triggerSyncForAlbum(String subfolder) {
        String url = txtAlbumLink.getText().toString();
        String quality = GooglePhotosSyncEngine.getSystemDisplayResolution(activity);

        btnSyncNow.setEnabled(false);
        syncProgress.setVisibility(View.VISIBLE);
        txtSyncStatus.setText("Connecting to Google Photos" + (subfolder != null ? " (" + subfolder + ")..." : "..."));

        GooglePhotosSyncEngine.startSync(activity, url, quality, subfolder, new GooglePhotosSyncEngine.SyncListener() {
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
    // 5. Home Layout & Apps Studio Sub-View (Live Real-Time Preview)
    // =========================================================================

    private void setupHomeStudioView(View root) {
        // App Tile Size Scaler (32)
        btnScaleCompact = root.findViewById(R.id.ccBtnTileScaleCompact);
        btnScaleStandard = root.findViewById(R.id.ccBtnTileScaleStandard);
        btnScaleSpacious = root.findViewById(R.id.ccBtnTileScaleSpacious);
        String currentScale = activity.getCustomTileScale();

        if (btnScaleCompact != null) {
            btnScaleCompact.setSelected("compact".equals(currentScale));
            btnScaleCompact.setOnClickListener(v -> {
                btnScaleCompact.setSelected(true);
                if (btnScaleStandard != null) btnScaleStandard.setSelected(false);
                if (btnScaleSpacious != null) btnScaleSpacious.setSelected(false);
                activity.setCustomTileScale("compact");
            });
        }

        if (btnScaleStandard != null) {
            btnScaleStandard.setSelected("standard".equals(currentScale));
            btnScaleStandard.setOnClickListener(v -> {
                if (btnScaleCompact != null) btnScaleCompact.setSelected(false);
                btnScaleStandard.setSelected(true);
                if (btnScaleSpacious != null) btnScaleSpacious.setSelected(false);
                activity.setCustomTileScale("standard");
            });
        }

        if (btnScaleSpacious != null) {
            btnScaleSpacious.setSelected("spacious".equals(currentScale));
            btnScaleSpacious.setOnClickListener(v -> {
                if (btnScaleCompact != null) btnScaleCompact.setSelected(false);
                if (btnScaleStandard != null) btnScaleStandard.setSelected(false);
                btnScaleSpacious.setSelected(true);
                activity.setCustomTileScale("spacious");
            });
        }

        // 4. Corner Radius Stepper (33)
        btnRad0 = root.findViewById(R.id.ccBtnRadius0);
        btnRad14 = root.findViewById(R.id.ccBtnRadius14);
        btnRad20 = root.findViewById(R.id.ccBtnRadius20);
        btnRad28 = root.findViewById(R.id.ccBtnRadius28);
        int currentRadius = activity.getCustomCornerRadiusDp();

        if (btnRad0 != null) {
            btnRad0.setSelected(currentRadius == 0);
            btnRad0.setOnClickListener(v -> {
                selectRadiusBtn(btnRad0, btnRad14, btnRad20, btnRad28);
                activity.setCustomCornerRadiusDp(0);
            });
        }
        if (btnRad14 != null) {
            btnRad14.setSelected(currentRadius == 14);
            btnRad14.setOnClickListener(v -> {
                selectRadiusBtn(btnRad14, btnRad0, btnRad20, btnRad28);
                activity.setCustomCornerRadiusDp(14);
            });
        }
        if (btnRad20 != null) {
            btnRad20.setSelected(currentRadius == 20);
            btnRad20.setOnClickListener(v -> {
                selectRadiusBtn(btnRad20, btnRad0, btnRad14, btnRad28);
                activity.setCustomCornerRadiusDp(20);
            });
        }
        if (btnRad28 != null) {
            btnRad28.setSelected(currentRadius == 28);
            btnRad28.setOnClickListener(v -> {
                selectRadiusBtn(btnRad28, btnRad0, btnRad14, btnRad20);
                activity.setCustomCornerRadiusDp(28);
            });
        }

        // 5. Wallpaper Dimming (34)
        btnDim0 = root.findViewById(R.id.ccBtnDim0);
        btnDim25 = root.findViewById(R.id.ccBtnDim25);
        btnDim50 = root.findViewById(R.id.ccBtnDim50);
        btnDim75 = root.findViewById(R.id.ccBtnDim75);
        int currentDim = activity.getCustomDimmingPercent();

        if (btnDim0 != null) {
            btnDim0.setSelected(currentDim == 0);
            btnDim0.setOnClickListener(v -> {
                selectDimBtn(btnDim0, btnDim25, btnDim50, btnDim75);
                activity.setCustomDimmingPercent(0);
            });
        }
        if (btnDim25 != null) {
            btnDim25.setSelected(currentDim == 25);
            btnDim25.setOnClickListener(v -> {
                selectDimBtn(btnDim25, btnDim0, btnDim50, btnDim75);
                activity.setCustomDimmingPercent(25);
            });
        }
        if (btnDim50 != null) {
            btnDim50.setSelected(currentDim == 50);
            btnDim50.setOnClickListener(v -> {
                selectDimBtn(btnDim50, btnDim0, btnDim25, btnDim75);
                activity.setCustomDimmingPercent(50);
            });
        }
        if (btnDim75 != null) {
            btnDim75.setSelected(currentDim == 75);
            btnDim75.setOnClickListener(v -> {
                selectDimBtn(btnDim75, btnDim0, btnDim25, btnDim50);
                activity.setCustomDimmingPercent(75);
            });
        }

        // Live Clock & Date Preview Card
        TextView txtClockPreviewOutput = root.findViewById(R.id.ccTxtClockPreviewOutput);
        TextView txtClockPreviewSub = root.findViewById(R.id.ccTxtClockPreviewSub);

        final String[] curTime = {customPrefs.getString(ClockHelper.KEY_HOME_TIME_FMT, ClockHelper.FMT_12_SEC)};
        final String[] curDate = {customPrefs.getString(ClockHelper.KEY_HOME_DATE_FMT, ClockHelper.DATE_VERBAL_STD)};
        final String[] curPos = {customPrefs.getString(ClockHelper.KEY_HOME_POS, "top_right")};

        Runnable refreshPreview = () -> {
            if (txtClockPreviewOutput != null) {
                String timeStr = ClockHelper.getFormattedSampleTime(curTime[0]);
                String dateStr = ClockHelper.getFormattedSampleDate(curDate[0]);
                if (ClockHelper.DATE_HIDDEN.equals(curDate[0])) {
                    txtClockPreviewOutput.setText(timeStr);
                } else {
                    txtClockPreviewOutput.setText(timeStr + "  •  " + dateStr);
                }
            }
            if (txtClockPreviewSub != null) {
                String posDesc = "center".equals(curPos[0]) ? "Center Hero" : ("top_left".equals(curPos[0]) ? "Top Left" : "Top Right");
                txtClockPreviewSub.setText("Active: " + posDesc + " • " + (ClockHelper.DATE_HIDDEN.equals(curDate[0]) ? "Date Hidden" : "Date Visible"));
                txtClockPreviewSub.setTextColor(0xFFB0BEC5);
            }
        };

        refreshPreview.run();

        // Home Time Format
        TextView[] homeTimeBtns = {
                root.findViewById(R.id.ccBtnHomeTime12Sec),
                root.findViewById(R.id.ccBtnHomeTime12Clean),
                root.findViewById(R.id.ccBtnHomeTime24Sec),
                root.findViewById(R.id.ccBtnHomeTime24Clean)
        };
        String[] homeTimeFmts = {
                ClockHelper.FMT_12_SEC,
                ClockHelper.FMT_12_CLEAN,
                ClockHelper.FMT_24_SEC,
                ClockHelper.FMT_24_CLEAN
        };
        String[] homeTimeLabels = {"12h (Sec)", "12h (Clean)", "24h (Sec)", "24h (Clean)"};

        for (int i = 0; i < homeTimeBtns.length; i++) {
            final int idx = i;
            TextView b = homeTimeBtns[i];
            if (b == null) continue;
            b.setSelected(homeTimeFmts[i].equals(curTime[0]));
            b.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    if (txtClockPreviewOutput != null) {
                        String timeStr = ClockHelper.getFormattedSampleTime(homeTimeFmts[idx]);
                        String dateStr = ClockHelper.getFormattedSampleDate(curDate[0]);
                        txtClockPreviewOutput.setText(ClockHelper.DATE_HIDDEN.equals(curDate[0]) ? timeStr : (timeStr + "  •  " + dateStr));
                    }
                    if (txtClockPreviewSub != null) {
                        txtClockPreviewSub.setText("🔍 Hover: " + homeTimeLabels[idx] + " ➔ e.g. " + ClockHelper.getFormattedSampleTime(homeTimeFmts[idx]));
                        txtClockPreviewSub.setTextColor(0xFF00E5FF);
                    }
                } else {
                    refreshPreview.run();
                }
            });
            b.setOnClickListener(v -> {
                curTime[0] = homeTimeFmts[idx];
                customPrefs.edit().putString(ClockHelper.KEY_HOME_TIME_FMT, homeTimeFmts[idx]).apply();
                for (int j = 0; j < homeTimeBtns.length; j++) {
                    if (homeTimeBtns[j] != null) homeTimeBtns[j].setSelected(j == idx);
                }
                activity.applyHomeClockSettings();
                refreshPreview.run();
                if (txtClockPreviewSub != null) {
                    txtClockPreviewSub.setText("✅ " + homeTimeLabels[idx] + " applied to Home!");
                    txtClockPreviewSub.setTextColor(0xFF00E676);
                }
            });
        }

        // Home Date Preset
        TextView[] homeDateBtns = {
                root.findViewById(R.id.ccBtnHomeDateVerbal),
                root.findViewById(R.id.ccBtnHomeDateFull),
                root.findViewById(R.id.ccBtnHomeDateDmySlash),
                root.findViewById(R.id.ccBtnHomeDateDmyDash),
                root.findViewById(R.id.ccBtnHomeDateNone)
        };
        String[] homeDateFmts = {
                ClockHelper.DATE_VERBAL_STD,
                ClockHelper.DATE_VERBAL_FULL,
                ClockHelper.DATE_DMY_SLASH,
                ClockHelper.DATE_DMY_DASH,
                ClockHelper.DATE_HIDDEN
        };
        String[] homeDateLabels = {"Verbal Date", "Full Date", "DD/MM/YY", "DD-MM-YY", "Hide Date"};

        for (int i = 0; i < homeDateBtns.length; i++) {
            final int idx = i;
            TextView b = homeDateBtns[i];
            if (b == null) continue;
            b.setSelected(homeDateFmts[i].equals(curDate[0]));
            b.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    if (txtClockPreviewOutput != null) {
                        String timeStr = ClockHelper.getFormattedSampleTime(curTime[0]);
                        String dateStr = ClockHelper.getFormattedSampleDate(homeDateFmts[idx]);
                        txtClockPreviewOutput.setText(ClockHelper.DATE_HIDDEN.equals(homeDateFmts[idx]) ? timeStr : (timeStr + "  •  " + dateStr));
                    }
                    if (txtClockPreviewSub != null) {
                        String sample = ClockHelper.getFormattedSampleDate(homeDateFmts[idx]);
                        txtClockPreviewSub.setText("🔍 Hover: " + homeDateLabels[idx] + " ➔ " + sample);
                        txtClockPreviewSub.setTextColor(0xFF00E5FF);
                    }
                } else {
                    refreshPreview.run();
                }
            });
            b.setOnClickListener(v -> {
                curDate[0] = homeDateFmts[idx];
                customPrefs.edit().putString(ClockHelper.KEY_HOME_DATE_FMT, homeDateFmts[idx]).apply();
                for (int j = 0; j < homeDateBtns.length; j++) {
                    if (homeDateBtns[j] != null) homeDateBtns[j].setSelected(j == idx);
                }
                activity.applyHomeClockSettings();
                refreshPreview.run();
                if (txtClockPreviewSub != null) {
                    txtClockPreviewSub.setText("✅ " + homeDateLabels[idx] + " applied to Home!");
                    txtClockPreviewSub.setTextColor(0xFF00E676);
                }
            });
        }

        // Home Position
        TextView[] homePosBtns = {
                root.findViewById(R.id.ccBtnHomePosRight),
                root.findViewById(R.id.ccBtnHomePosCenter),
                root.findViewById(R.id.ccBtnHomePosLeft)
        };
        String[] homePosKeys = {"top_right", "center", "top_left"};
        String[] homePosLabels = {"Top Right Corner", "Center Above Dock", "Top Left Corner"};

        for (int i = 0; i < homePosBtns.length; i++) {
            final int idx = i;
            TextView b = homePosBtns[i];
            if (b == null) continue;
            b.setSelected(homePosKeys[i].equals(curPos[0]));
            b.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    if (txtClockPreviewSub != null) {
                        txtClockPreviewSub.setText("🔍 Hover: " + homePosLabels[idx] + " (Moves clock live)");
                        txtClockPreviewSub.setTextColor(0xFF00E5FF);
                    }
                } else {
                    refreshPreview.run();
                }
            });
            b.setOnClickListener(v -> {
                curPos[0] = homePosKeys[idx];
                customPrefs.edit().putString(ClockHelper.KEY_HOME_POS, homePosKeys[idx]).apply();
                for (int j = 0; j < homePosBtns.length; j++) {
                    if (homePosBtns[j] != null) homePosBtns[j].setSelected(j == idx);
                }
                activity.applyHomeClockSettings();
                refreshPreview.run();
                if (txtClockPreviewSub != null) {
                    txtClockPreviewSub.setText("✅ Position set to " + homePosLabels[idx]);
                    txtClockPreviewSub.setTextColor(0xFF00E676);
                }
            });
        }

        // 6. Manage Hidden Apps (37)
        View btnManageHidden = root.findViewById(R.id.ccBtnManageHiddenApps);
        if (btnManageHidden != null) {
            btnManageHidden.setOnClickListener(v -> {
                HiddenAppsManager.showManageDialog(activity, () -> activity.refreshApps());
            });
        }

        // 7. Reset Home to Defaults
        View btnResetHome = root.findViewById(R.id.ccBtnResetHome);
        if (btnResetHome != null) {
            btnResetHome.setOnClickListener(v -> {
                new AlertDialog.Builder(activity)
                        .setTitle("🔄 Reset Home Layout")
                        .setMessage("Reset Home layout to default settings?\n\n• Margin: 16 px\n• Columns: 7\n• Tile Size: Standard\n• Corner Radius: Smooth (14dp)\n• Dimming: 50%\n\n(Your Google Photos links, downloaded screensaver albums, and photo history will NOT be affected.)")
                        .setPositiveButton("Reset", (dialog, which) -> {
                            activity.resetHomeDefaults();
                            currentMarginPx = 16;
                            currentColumns = 7;
                            updateMarginDisplay();
                            updateColsDisplay();
                            if (btnScaleStandard != null) {
                                btnScaleStandard.setSelected(true);
                                if (btnScaleCompact != null) btnScaleCompact.setSelected(false);
                                if (btnScaleSpacious != null) btnScaleSpacious.setSelected(false);
                            }
                            selectRadiusBtn(btnRad14, btnRad0, btnRad20, btnRad28);
                            selectDimBtn(btnDim50, btnDim0, btnDim25, btnDim75);
                            Toast.makeText(activity, "Home layout reset to defaults", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }
    }

    private void selectRadiusBtn(TextView target, TextView... others) {
        target.setSelected(true);
        for (TextView o : others) if (o != null) o.setSelected(false);
    }

    private void selectDimBtn(TextView target, TextView... others) {
        target.setSelected(true);
        for (TextView o : others) if (o != null) o.setSelected(false);
    }

    private void updateMarginDisplay() {
        if (txtMarginValue != null) {
            if (currentMarginPx == 0) {
                txtMarginValue.setText("0 px (Edge)");
            } else {
                txtMarginValue.setText(currentMarginPx + " px");
            }
        }
    }

    private void updateColsDisplay() {
        if (txtColsValue != null) {
            txtColsValue.setText(currentColumns + " Columns");
        }
    }
}
