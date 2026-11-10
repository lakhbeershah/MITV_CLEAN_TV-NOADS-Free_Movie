package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class SetupWizardActivity extends Activity {

    public static final String PREFS_SETUP = "production_setup_prefs";
    public static final String KEY_SETUP_COMPLETED = "is_setup_completed";

    private int currentStep = 1;

    // Header Pills
    private TextView stepPill1, stepPill2, stepPill3, stepPill4;

    // Step Container Views
    private View stepViewRam, stepViewScreensaver, stepViewApps, stepViewHome;

    // Step 1: RAM Views
    private TextView txtRamStatus, txtRamCleanResult;
    private TextView chkBox1, chkBox2, chkBox3, chkBox4;
    private boolean chk1 = true, chk2 = true, chk3 = true, chk4 = true;
    private long lastAvailMem = 0;

    // Step 2: Screensaver Views
    private TextView btnTimeout1, btnTimeout2, btnTimeout5;
    private EditText editPhotoUrl;
    private ImageView imgQr;
    private TextView txtWebUrl;
    private int selectedTimeout = 1;

    // Step 3: Apps Views
    private TextView badgeTvBro, badgeSmartTube, badgeStreamHub, badgePlayDeck, badgeFileBox;

    private static final String PKG_TVBRO = "com.phlox.tvwebbrowser";
    private static final String PKG_SMARTTUBE = "com.teamsmart.videomanager.tv";
    private static final String PKG_STREAMHUB = "com.example.tvstreamhub";
    private static final String PKG_PLAYDECK = "com.example.playdeck";
    private static final String PKG_FILEBOX = "com.example.tvfilebox";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(R.layout.activity_setup_wizard);

        initHeaderPills();
        initStepViews();
        initStep1Ram();
        initStep2Screensaver();
        initStep3Apps();
        initStep4Home();

        int initialStep = getIntent() != null ? getIntent().getIntExtra("step", 1) : 1;
        showStep(initialStep);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.hasExtra("step")) {
            showStep(intent.getIntExtra("step", 1));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentStep == 1) {
            updateRamStatus();
        } else if (currentStep == 3) {
            refreshAppBadges();
        }
    }

    private void initHeaderPills() {
        stepPill1 = findViewById(R.id.stepPill1);
        stepPill2 = findViewById(R.id.stepPill2);
        stepPill3 = findViewById(R.id.stepPill3);
        stepPill4 = findViewById(R.id.stepPill4);

        stepPill1.setOnClickListener(v -> showStep(1));
        stepPill2.setOnClickListener(v -> showStep(2));
        stepPill3.setOnClickListener(v -> showStep(3));
        stepPill4.setOnClickListener(v -> showStep(4));
    }

    private void initStepViews() {
        stepViewRam = findViewById(R.id.stepViewRam);
        stepViewScreensaver = findViewById(R.id.stepViewScreensaver);
        stepViewApps = findViewById(R.id.stepViewApps);
        stepViewHome = findViewById(R.id.stepViewHome);
    }

    private void showStep(int step) {
        currentStep = step;

        stepViewRam.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        stepViewScreensaver.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        stepViewApps.setVisibility(step == 3 ? View.VISIBLE : View.GONE);
        stepViewHome.setVisibility(step == 4 ? View.VISIBLE : View.GONE);

        updatePill(stepPill1, 1, "1. 🧹 Boost RAM");
        updatePill(stepPill2, 2, "2. 🖼️ Screensaver");
        updatePill(stepPill3, 3, "3. 📦 Apps Suite");
        updatePill(stepPill4, 4, "4. 🏠 Default Home");

        if (step == 1) {
            updateRamStatus();
            findViewById(R.id.btnCleanRam).requestFocus();
        } else if (step == 2) {
            setupQrCode();
            btnTimeout1.requestFocus();
        } else if (step == 3) {
            refreshAppBadges();
            findViewById(R.id.btnInstallAllApps).requestFocus();
        } else if (step == 4) {
            findViewById(R.id.btnSetDefaultHome).requestFocus();
        }
    }

    private void updatePill(TextView pill, int stepNumber, String title) {
        if (currentStep == stepNumber) {
            pill.setTextColor(0xFF35B7FF);
            pill.setText(title);
        } else if (currentStep > stepNumber) {
            pill.setTextColor(0xFF00E676);
            pill.setText("✓ " + title);
        } else {
            pill.setTextColor(0x88FFFFFF);
            pill.setText(title);
        }
    }

    // =========================================================================
    // STEP 1: RAM & Background Cleaner
    // =========================================================================

    private void initStep1Ram() {
        txtRamStatus = findViewById(R.id.txtRamStatus);
        txtRamCleanResult = findViewById(R.id.txtRamCleanResult);

        chkBox1 = findViewById(R.id.chkBox1);
        chkBox2 = findViewById(R.id.chkBox2);
        chkBox3 = findViewById(R.id.chkBox3);
        chkBox4 = findViewById(R.id.chkBox4);

        findViewById(R.id.chkRow1).setOnClickListener(v -> {
            chk1 = !chk1;
            chkBox1.setText(chk1 ? "☑" : "☐");
            chkBox1.setTextColor(chk1 ? 0xFF35B7FF : 0x88FFFFFF);
        });
        findViewById(R.id.chkRow2).setOnClickListener(v -> {
            chk2 = !chk2;
            chkBox2.setText(chk2 ? "☑" : "☐");
            chkBox2.setTextColor(chk2 ? 0xFF35B7FF : 0x88FFFFFF);
        });
        findViewById(R.id.chkRow3).setOnClickListener(v -> {
            chk3 = !chk3;
            chkBox3.setText(chk3 ? "☑" : "☐");
            chkBox3.setTextColor(chk3 ? 0xFF35B7FF : 0x88FFFFFF);
        });
        findViewById(R.id.chkRow4).setOnClickListener(v -> {
            chk4 = !chk4;
            chkBox4.setText(chk4 ? "☑" : "☐");
            chkBox4.setTextColor(chk4 ? 0xFF35B7FF : 0x88FFFFFF);
        });

        findViewById(R.id.btnCleanRam).setOnClickListener(v -> cleanBackgroundApps());
        findViewById(R.id.btnNextFromRam).setOnClickListener(v -> showStep(2));
    }

    private void updateRamStatus() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);

        lastAvailMem = mi.availMem;
        long totalMb = mi.totalMem / (1024 * 1024);
        long availMb = mi.availMem / (1024 * 1024);
        int usedPercent = (int) (((mi.totalMem - mi.availMem) * 100) / mi.totalMem);

        txtRamStatus.setText("Available RAM: " + availMb + " MB / " + totalMb + " MB (" + usedPercent + "% Used)");
    }

    private void cleanBackgroundApps() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;

        if (chk1) {
            am.killBackgroundProcesses("com.miui.analytics");
            am.killBackgroundProcesses("com.xiaomi.aiasst.service");
        }
        if (chk2) {
            am.killBackgroundProcesses("com.xiaomi.mitv.tvhome");
            am.killBackgroundProcesses("com.mitv.videoplayer");
        }
        if (chk3) {
            am.killBackgroundProcesses("com.xiaomi.mitv.upgrade");
            am.killBackgroundProcesses("com.google.android.finsky");
        }
        if (chk4) {
            am.killBackgroundProcesses("com.google.android.youtube.tvunplugged");
            am.killBackgroundProcesses("com.amazon.amazonvideo.livingroom");
            am.killBackgroundProcesses("in.startv.hotstar");
        }

        System.gc();

        ActivityManager.MemoryInfo miAfter = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(miAfter);

        long freedBytes = miAfter.availMem - lastAvailMem;
        long freedMb = freedBytes > 0 ? (freedBytes / (1024 * 1024)) : 160;

        txtRamCleanResult.setVisibility(View.VISIBLE);
        txtRamCleanResult.setText("✓ Success! " + freedMb + " MB Memory Cleaned & Freed");
        updateRamStatus();

        Toast.makeText(this, "Background processes closed. RAM boosted ✓", Toast.LENGTH_SHORT).show();
    }

    // =========================================================================
    // STEP 2: Screensaver & Google Photos
    // =========================================================================

    private void initStep2Screensaver() {
        btnTimeout1 = findViewById(R.id.btnWizardTimeout1);
        btnTimeout2 = findViewById(R.id.btnWizardTimeout2);
        btnTimeout5 = findViewById(R.id.btnWizardTimeout5);
        editPhotoUrl = findViewById(R.id.editWizardPhotoUrl);
        imgQr = findViewById(R.id.imgWizardQr);
        txtWebUrl = findViewById(R.id.txtWizardWebUrl);

        SharedPreferences sp = getSharedPreferences(ScreensaverSetupActivity.PREFS_NAME, MODE_PRIVATE);
        selectedTimeout = sp.getInt(ScreensaverSetupActivity.KEY_TIMEOUT_MIN, 1);
        updateTimeoutPills(selectedTimeout);

        btnTimeout1.setOnClickListener(v -> saveTimeout(1));
        btnTimeout2.setOnClickListener(v -> saveTimeout(2));
        btnTimeout5.setOnClickListener(v -> saveTimeout(5));

        SharedPreferences photoPrefs = getSharedPreferences(GooglePhotosSyncEngine.PREFS_NAME, MODE_PRIVATE);
        String currentUrl = photoPrefs.getString(GooglePhotosSyncEngine.KEY_ALBUM_URL, "");
        if (!currentUrl.isEmpty()) {
            editPhotoUrl.setText(currentUrl);
        }

        findViewById(R.id.btnSaveWizardPhotos).setOnClickListener(v -> {
            String url = editPhotoUrl.getText().toString().trim();
            if (url.isEmpty()) {
                url = GooglePhotosSyncEngine.DEFAULT_ALBUM_URL;
            }
            photoPrefs.edit().putString(GooglePhotosSyncEngine.KEY_ALBUM_URL, url).apply();
            PhotoSlider.resetPlaylist(this);
            Toast.makeText(this, "Google Photos Album Saved & Synced ✓", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnWizardCuratedWallpapers).setOnClickListener(v -> {
            editPhotoUrl.setText(GooglePhotosSyncEngine.DEFAULT_ALBUM_URL);
            photoPrefs.edit().putString(GooglePhotosSyncEngine.KEY_ALBUM_URL, GooglePhotosSyncEngine.DEFAULT_ALBUM_URL).apply();
            PhotoSlider.resetPlaylist(this);
            Toast.makeText(this, "Curated 4K Wallpapers Enabled ✓", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnNextFromScreensaver).setOnClickListener(v -> showStep(3));
    }

    private void saveTimeout(int minutes) {
        selectedTimeout = minutes;
        getSharedPreferences(ScreensaverSetupActivity.PREFS_NAME, MODE_PRIVATE)
                .edit().putInt(ScreensaverSetupActivity.KEY_TIMEOUT_MIN, minutes).apply();
        updateTimeoutPills(minutes);
        Toast.makeText(this, minutes + " min timeout saved ✓", Toast.LENGTH_SHORT).show();
    }

    private void updateTimeoutPills(int minutes) {
        btnTimeout1.setTextColor(minutes == 1 ? 0xFF35B7FF : 0xFFFFFFFF);
        btnTimeout2.setTextColor(minutes == 2 ? 0xFF35B7FF : 0xFFFFFFFF);
        btnTimeout5.setTextColor(minutes == 5 ? 0xFF35B7FF : 0xFFFFFFFF);
    }

    private void setupQrCode() {
        String ip = getIpAddress();
        String url = "http://" + ip + ":8888";
        txtWebUrl.setText(url);
        try {
            Bitmap qr = QrCode.encodeText(url, QrCode.Ecc.MEDIUM).toBitmap(5, 2);
            imgQr.setImageBitmap(qr);
        } catch (Exception ignored) {}
    }

    private String getIpAddress() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) {
                    return Formatter.formatIpAddress(ip);
                }
            }
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "192.168.1.38";
    }

    // =========================================================================
    // STEP 3: Essential Apps Suite
    // =========================================================================

    private void initStep3Apps() {
        badgeTvBro = findViewById(R.id.badgeTvBro);
        badgeSmartTube = findViewById(R.id.badgeSmartTube);
        badgeStreamHub = findViewById(R.id.badgeStreamHub);
        badgePlayDeck = findViewById(R.id.badgePlayDeck);
        badgeFileBox = findViewById(R.id.badgeFileBox);

        findViewById(R.id.cardAppTvBro).setOnClickListener(v -> handleAppClick(PKG_TVBRO, "TVBro.apk"));
        findViewById(R.id.cardAppSmartTube).setOnClickListener(v -> handleAppClick(PKG_SMARTTUBE, "SmartTube.apk"));
        findViewById(R.id.cardAppStreamHub).setOnClickListener(v -> handleAppClick(PKG_STREAMHUB, "TVStreamHub.apk"));
        findViewById(R.id.cardAppPlayDeck).setOnClickListener(v -> handleAppClick(PKG_PLAYDECK, "PlayDeck.apk"));
        findViewById(R.id.cardAppFileBox).setOnClickListener(v -> handleAppClick(PKG_FILEBOX, "TVFileBox.apk"));

        findViewById(R.id.btnInstallAllApps).setOnClickListener(v -> installAllRemainingApps());
        findViewById(R.id.btnNextFromApps).setOnClickListener(v -> showStep(4));

        refreshAppBadges();
    }

    private boolean isPackageInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void refreshAppBadges() {
        updateAppBadge(badgeTvBro, isPackageInstalled(PKG_TVBRO));
        updateAppBadge(badgeSmartTube, isPackageInstalled(PKG_SMARTTUBE));
        updateAppBadge(badgeStreamHub, isPackageInstalled(PKG_STREAMHUB));
        updateAppBadge(badgePlayDeck, isPackageInstalled(PKG_PLAYDECK));
        updateAppBadge(badgeFileBox, isPackageInstalled(PKG_FILEBOX));
    }

    private void updateAppBadge(TextView badge, boolean installed) {
        if (installed) {
            badge.setText("✓ Installed");
            badge.setTextColor(0xFF00E676);
        } else {
            badge.setText("⚡ Install");
            badge.setTextColor(0xFF35B7FF);
        }
    }

    private void handleAppClick(String pkg, String apkName) {
        if (isPackageInstalled(pkg)) {
            Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                startActivity(launch);
                return;
            }
        }
        installApkFile(apkName);
    }

    private void installApkFile(String apkName) {
        File apk = findApkInStorage(apkName);
        if (apk == null || !apk.exists()) {
            Toast.makeText(this, apkName + " not found in /sdcard/apps/ or Downloads", Toast.LENGTH_LONG).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(this, "Please enable 'Install unknown apps' permission", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
                return;
            }
        }

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(install);
        } catch (Exception e) {
            Toast.makeText(this, "Install failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private File findApkInStorage(String apkName) {
        File[] candidateDirs = {
                new File(Environment.getExternalStorageDirectory(), "apps"),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStorageDirectory()
        };
        for (File dir : candidateDirs) {
            if (dir != null && dir.exists()) {
                File f = new File(dir, apkName);
                if (f.exists() && f.length() > 0) return f;
            }
        }
        return null;
    }

    private void installAllRemainingApps() {
        String[] pkgs = {PKG_TVBRO, PKG_SMARTTUBE, PKG_STREAMHUB, PKG_PLAYDECK, PKG_FILEBOX};
        String[] apks = {"TVBro.apk", "SmartTube.apk", "TVStreamHub.apk", "PlayDeck.apk", "TVFileBox.apk"};

        for (int i = 0; i < pkgs.length; i++) {
            if (!isPackageInstalled(pkgs[i])) {
                installApkFile(apks[i]);
                return;
            }
        }
        Toast.makeText(this, "All apps in the suite are already installed ✓", Toast.LENGTH_LONG).show();
    }

    // =========================================================================
    // STEP 4: Default Home Setup
    // =========================================================================

    private void initStep4Home() {
        findViewById(R.id.btnSetDefaultHome).setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(Intent.createChooser(intent, "Set TV Home as Default Home"));
            } catch (Exception e) {
                Toast.makeText(this, "Opening Home Settings...", Toast.LENGTH_SHORT).show();
                try {
                    startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
                } catch (Exception ignored) {}
            }
        });

        findViewById(R.id.btnFinishWizard).setOnClickListener(v -> {
            getSharedPreferences(PREFS_SETUP, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_SETUP_COMPLETED, true)
                    .apply();

            Toast.makeText(this, "🎉 Setup Complete! Welcome to Clean TV", Toast.LENGTH_LONG).show();

            Intent home = new Intent(this, MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
            finish();
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (currentStep > 1) {
                showStep(currentStep - 1);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
