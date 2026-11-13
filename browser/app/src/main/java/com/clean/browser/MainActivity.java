package com.clean.browser;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.Collections;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "browser_prefs";
    private static final String KEY_ZOOM = "page_zoom";
    private static final String KEY_DARK = "dark_mode";
    private static final String KEY_UA_MOBILE = "ua_mobile";
    private static final String KEY_CURSOR_SPEED = "cursor_speed";
    private static final String KEY_CURSOR_ACCEL = "cursor_accel";
    private static final String KEY_SCROLL_SPEED = "scroll_speed";

    private static final int MODE_MOUSE = 0;
    private static final int MODE_DPAD = 1;
    private static final int MODE_MOVIE = 2;

    private static final String UA_DESKTOP = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final String UA_MOBILE = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36";

    // Views
    private CleanTvWebView webView;
    private FrameLayout customViewContainer;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private View customVideoView;

    private ViewGroup speedDialView;
    private EditText editSearchUrl;
    private ProgressBar progressBar;
    private VirtualCursorView cursorView;

    // In-Video HUD
    private View hudOverlay;
    private TextView hudIcon;
    private TextView hudText;
    private final Handler hudHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideHudRunnable = () -> {
        if (hudOverlay != null) hudOverlay.setVisibility(View.GONE);
    };

    // Quick Actions & Settings Modals
    private View radialMenuLayout;
    private TextView menuBtnHome, menuBtnReload;
    private TextView menuBtnModeMouse, menuBtnModeDpad, menuBtnModeMovie;
    private TextView menuBtnZoomIn, textZoomScale, menuBtnZoomOut;
    private TextView menuBtnUserAgent, menuBtnDarkMode, menuBtnSettings;

    private View settingsMenuLayout;
    private TextView btnSpeedDown, textSettingSpeed, btnSpeedUp;
    private TextView btnAccelDown, textSettingAccel, btnAccelUp;
    private TextView btnScrollDown, textSettingScroll, btnScrollUp;
    private TextView btnResetPointer, btnClearCache, btnSettingsDone;

    // State
    private int currentZoom = 125; // Default 125% for 10-foot TV viewing
    private boolean isDarkMode = false;
    private boolean isMobileUa = true; // Mobile UA default for smooth H.264 video decoding
    private boolean isVideoActive = false;
    private volatile boolean isVideoPlaying = false; // Real-time media detector state
    private boolean isActivityResumed = true;
    private final Handler bgServiceHandler = new Handler(Looper.getMainLooper());
    private final Runnable stopBgServiceRunnable = () -> {
        if (!isActivityResumed && !isVideoPlaying) {
            BackgroundAudioService.stop(MainActivity.this);
            releaseWakeLock();
        }
    };
    private int controlMode = MODE_MOUSE; // Always Mouse by default
    private String lastMainUrl = "";

    // TV Bro-Style Tunable Pointer Settings
    private int pointerSpeedLevel = 4; // 1..10
    private int pointerAccelLevel = 4; // 0..10
    private int scrollSpeedLevel = 5;  // 1..10

    public class MediaBridge {
        @android.webkit.JavascriptInterface
        public void setFocusRect(final float nx, final float ny, final float nw, final float nh) {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (cursorView != null) {
                    boolean show = (controlMode == MODE_DPAD
                            && speedDialView != null && speedDialView.getVisibility() != View.VISIBLE
                            && !isModalVisible());
                    cursorView.setFocusBoxVisible(show);
                    if (show) {
                        cursorView.setFocusRect(nx, ny, nw, nh);
                    } else {
                        cursorView.clearFocusRect();
                    }
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void setVideoPlaying(boolean playing) {
            new Handler(Looper.getMainLooper()).post(() -> {
                isVideoPlaying = playing;
                if (playing) {
                    bgServiceHandler.removeCallbacks(stopBgServiceRunnable);
                    acquireWakeLock();
                    if (!isActivityResumed) {
                        BackgroundAudioService.start(MainActivity.this);
                    }
                } else {
                    if (!isActivityResumed) {
                        // When in background, media pause might be temporary buffering or track transition.
                        // Wait 15 seconds before stopping the foreground service and dropping CPU priority!
                        bgServiceHandler.removeCallbacks(stopBgServiceRunnable);
                        bgServiceHandler.postDelayed(stopBgServiceRunnable, 15000);
                    } else {
                        releaseWakeLock();
                        BackgroundAudioService.stop(MainActivity.this);
                    }
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void nativeScrollFallback(final int dx, final int dy) {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (webView != null) {
                    webView.scrollBy(dx, dy);
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void nativeTapNormalized(final float nx, final float ny) {
            new Handler(Looper.getMainLooper()).post(() -> {
                View target = getActiveContentView();
                if (target != null && target.getWidth() > 0 && target.getHeight() > 0) {
                    dispatchTouchToView(target, nx * target.getWidth(), ny * target.getHeight());
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void showSoftKeyboard() {
            new Handler(Looper.getMainLooper()).post(() -> {
                View target = getActiveContentView();
                if (target != null) {
                    target.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            });
        }
    }

    private float getCursorBaseSpeed() {
        return 2.0f + pointerSpeedLevel * 0.75f; // Lvl 4 = 5.0 px/frame
    }

    private float getCursorAccelStep() {
        return pointerAccelLevel == 0 ? 0f : (0.08f + pointerAccelLevel * 0.0675f); // Lvl 4 = 0.35
    }

    private float getCursorMaxSpeed() {
        if (pointerAccelLevel == 0) return getCursorBaseSpeed();
        return getCursorBaseSpeed() + 1.0f + pointerAccelLevel * 1.125f; // Lvl 4/4 = 10.5 px/frame
    }

    private int getEdgeScrollStep() {
        return Math.round(10f + scrollSpeedLevel * 3.4f); // Lvl 5 = 27 px/frame
    }

    // D-Pad Mouse movement handler (slow start for precision, tunable ramp on hold)
    private final Handler cursorHandler = new Handler(Looper.getMainLooper());
    private int cursorHoldFrames = 0;
    private long lastEdgeHudTime = 0;
    private long lastHoverMoveTime = 0;
    private boolean isMovingCursor = false;
    private int activeDpadCode = 0;

    // Long press tracking via Handler
    private final Handler keyHandler = new Handler(Looper.getMainLooper());
    private boolean okLongHandled = false;
    private boolean backLongHandled = false;
    private long menuOpenedTimestamp = 0;

    private final Runnable longPressOkRunnable = new Runnable() {
        @Override
        public void run() {
            okLongHandled = true;
            openRadialMenu();
        }
    };

    private final Runnable longPressBackRunnable = new Runnable() {
        @Override
        public void run() {
            handleLongBack();
        }
    };

    private void handleLongBack() {
        backLongHandled = true;
        keyHandler.removeCallbacks(longPressBackRunnable);
        if (isModalVisible()) {
            hideAllModals();
            return;
        }
        // Holding BACK moves FORWARD in webpage history if available
        if (speedDialView != null && speedDialView.getVisibility() != View.VISIBLE && webView != null && webView.canGoForward()) {
            webView.goForward();
            if (controlMode != MODE_MOUSE) {
                enterMouseMode(false);
            }
            showHud("⏩", "Forward ➔");
            return;
        }
        // If in D-Pad or Movie mode, holding BACK turns it off and returns to default Mouse mode
        if (controlMode != MODE_MOUSE) {
            enterMouseMode(true);
            cursorView.resetToCenter();
            return;
        }
        if (speedDialView != null && speedDialView.getVisibility() != View.VISIBLE) {
            showHud("ℹ️", "No Forward History");
        } else {
            cursorView.resetToCenter();
            showHud("🎯", "Cursor Centered");
        }
    }

    private void updateModeSelectionUi() {
        if (menuBtnModeMouse != null) menuBtnModeMouse.setSelected(controlMode == MODE_MOUSE);
        if (menuBtnModeDpad != null) menuBtnModeDpad.setSelected(controlMode == MODE_DPAD);
        if (menuBtnModeMovie != null) menuBtnModeMovie.setSelected(controlMode == MODE_MOVIE);
    }

    private void enterMouseMode(boolean showToast) {
        controlMode = MODE_MOUSE;
        boolean onHome = speedDialView != null && speedDialView.getVisibility() == View.VISIBLE;
        cursorView.setFocusBoxVisible(false);
        cursorView.clearFocusRect();
        cursorView.setPointerVisible(!onHome && !isModalVisible());
        if (!onHome && !isModalVisible()) {
            cursorView.requestFocus();
        }
        if (webView != null) {
            webView.evaluateJavascript(
                    "window._cleanTvDpadMode = false; if (window._cleanTvSetDpadFlag) window._cleanTvSetDpadFlag(false); " +
                    "if (document.activeElement && document.activeElement.blur) document.activeElement.blur();",
                    null);
        }
        updateModeSelectionUi();
        if (showToast && !onHome) {
            showHud("🎯", "Mouse Pointer Active");
        }
    }

    private void enterDpadMode() {
        controlMode = MODE_DPAD;
        cursorView.setPointerVisible(false);
        cursorView.clearFocusRect();
        cursorView.setFocusBoxVisible(true);
        View active = getActiveContentView();
        if (active != null) {
            active.setFocusable(true);
            active.requestFocus();
            dispatchHoverToActive(active.getWidth() * 0.5f, active.getHeight() * 0.5f);
        }
        if (webView != null) {
            webView.evaluateJavascript("if (window._cleanTvSetDpadFlag) window._cleanTvSetDpadFlag(true); else window._cleanTvDpadMode = true;", null);
            webView.postDelayed(() -> {
                if (controlMode == MODE_DPAD && webView != null) {
                    webView.evaluateJavascript(
                            "if (window._cleanTvSetDpadFlag) window._cleanTvSetDpadFlag(true); " +
                            "if (window._cleanTvInitFocus) window._cleanTvInitFocus();", null);
                }
            }, 90);
        }
        updateModeSelectionUi();
        showHud("🎮", "D-Pad Navigator (▲▼◀▶ Move, OK Click)\nHold BACK to return to Mouse");
    }

    private void enterMovieMode() {
        controlMode = MODE_MOVIE;
        cursorView.setPointerVisible(false);
        cursorView.setFocusBoxVisible(false);
        cursorView.clearFocusRect();
        View active = getActiveContentView();
        if (active != null) active.requestFocus();
        if (webView != null) {
            webView.evaluateJavascript("if (window._cleanTvSetDpadFlag) window._cleanTvSetDpadFlag(false); else window._cleanTvDpadMode = false;", null);
        }
        updateModeSelectionUi();
        showHud("🎬", "Movie Mode (▲/▼ Vol, ◀/▶ Seek, OK Pause)\nHold BACK to return to Mouse");
    }

    private void onPageOrSiteChanged(String url, boolean notifyIfSwitched) {
        if (url == null || url.isEmpty() || url.startsWith("about:")) return;
        if (!url.equals(lastMainUrl)) {
            lastMainUrl = url;
            isVideoPlaying = false;
            isVideoActive = false;
            if (speedDialView != null && speedDialView.getVisibility() != View.VISIBLE) {
                boolean wasNonMouse = (controlMode != MODE_MOUSE);
                enterMouseMode(notifyIfSwitched && wasNonMouse);
            }
        }
    }

    private void scrollPageSlow(int deltaY) {
        if ((deltaY > 0 && webView.canScrollVertically(1)) || (deltaY < 0 && webView.canScrollVertically(-1))) {
            webView.scrollBy(0, deltaY);
        } else if (cursorHoldFrames % 3 == 0) {
            webView.evaluateJavascript("window.scrollBy(0, " + (deltaY * 3) + ");", null);
        }
    }

    private void scrollPageSlowH(int deltaX) {
        if ((deltaX > 0 && webView.canScrollHorizontally(1)) || (deltaX < 0 && webView.canScrollHorizontally(-1))) {
            webView.scrollBy(deltaX, 0);
        } else if (cursorHoldFrames % 3 == 0) {
            webView.evaluateJavascript("window.scrollBy(" + (deltaX * 3) + ", 0);", null);
        }
    }

    private void scrollPageWebpage(int deltaY, boolean isRepeat) {
        webView.scrollBy(0, deltaY);
        String behavior = isRepeat ? "auto" : "smooth";
        webView.evaluateJavascript("window.scrollBy({top: " + deltaY + ", behavior: '" + behavior + "'});", null);
        if (controlMode == MODE_DPAD && !isRepeat) {
            showHud(deltaY < 0 ? "▲" : "▼", deltaY < 0 ? "Scroll Up" : "Scroll Down",
                    deltaY < 0 ? (Gravity.TOP | Gravity.CENTER_HORIZONTAL) : (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        }
    }

    private final Runnable cursorMoveRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isMovingCursor || controlMode != MODE_MOUSE || !cursorView.isPointerVisible()) return;

            cursorHoldFrames++;
            float baseSpeed = getCursorBaseSpeed();
            float accelStep = getCursorAccelStep();
            float maxSpeed = getCursorMaxSpeed();
            float speed = (cursorHoldFrames <= 8 || accelStep <= 0f)
                    ? baseSpeed
                    : Math.min(maxSpeed, baseSpeed + (cursorHoldFrames - 8) * accelStep);

            float dx = 0, dy = 0;
            if (activeDpadCode == KeyEvent.KEYCODE_DPAD_UP) dy = -speed;
            else if (activeDpadCode == KeyEvent.KEYCODE_DPAD_DOWN) dy = speed;
            else if (activeDpadCode == KeyEvent.KEYCODE_DPAD_LEFT) dx = -speed;
            else if (activeDpadCode == KeyEvent.KEYCODE_DPAD_RIGHT) dx = speed;

            cursorView.moveBy(dx, dy);

            float curX = cursorView.getCursorX();
            float curY = cursorView.getCursorY();
            int viewW = cursorView.getWidth() > 0 ? cursorView.getWidth() : 1280;
            int viewH = cursorView.getHeight() > 0 ? cursorView.getHeight() : 720;
            long now = SystemClock.uptimeMillis();

            // Wake up auto-hiding fullscreen/player controls while moving cursor over video
            long hoverInterval = (isVideoPlaying || isVideoActive) ? 450 : 120;
            if (now - lastHoverMoveTime > hoverInterval) {
                lastHoverMoveTime = now;
                dispatchHoverToActive(curX, curY);
            }

            // Tunable continuous edge scrolling while in pointer mode (smooth scrolling without hud popup)
            int edgeStep = getEdgeScrollStep();
            if (dy > 0 && curY >= viewH - 45) {
                scrollPageSlow(edgeStep);
            } else if (dy < 0 && curY <= 45) {
                scrollPageSlow(-edgeStep);
            }
            if (dx > 0 && curX >= viewW - 45) {
                scrollPageSlowH(edgeStep);
            } else if (dx < 0 && curX <= 45) {
                scrollPageSlowH(-edgeStep);
            }

            cursorHandler.postDelayed(this, 16);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(R.layout.activity_main);

        loadPreferences();
        initViews();
        setupWebView();
        setupSpeedDial();
        setupRadialMenu();
        setupSettingsMenu();

        // Check if intent contains URL
        if (getIntent() != null && getIntent().getData() != null) {
            loadUrl(getIntent().getData().toString());
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getData() != null) {
            loadUrl(intent.getData().toString());
        } else {
            // Re-opened from Home: If video is playing/active, ensure webView is visible!
            if (isVideoActive || isVideoPlaying) {
                if (speedDialView != null && speedDialView.getVisibility() == View.VISIBLE) {
                    speedDialView.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                    enterMouseMode(true);
                }
            }
        }
    }

    private PowerManager.WakeLock wakeLock;

    private void acquireWakeLock() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CleanTvBrowser:BgPlayback");
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(120 * 60 * 1000L); // 2h safe timeout
            }
        } catch (Exception ignored) {}
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityResumed = false;
        if (webView != null) {
            webView.setKeepBackgroundMediaActive(true);
            webView.resumeTimers();
        }
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        boolean isMusicActive = (am != null && am.isMusicActive());
        if (isVideoPlaying || isVideoActive || isMusicActive) {
            bgServiceHandler.removeCallbacks(stopBgServiceRunnable);
            acquireWakeLock();
            BackgroundAudioService.start(this);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (webView != null) {
            webView.setKeepBackgroundMediaActive(true);
            webView.resumeTimers();
        }
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        boolean isMusicActive = (am != null && am.isMusicActive());
        if (isVideoPlaying || isVideoActive || isMusicActive) {
            bgServiceHandler.removeCallbacks(stopBgServiceRunnable);
            acquireWakeLock();
            BackgroundAudioService.start(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityResumed = true;
        if (webView != null) {
            webView.setKeepBackgroundMediaActive(true);
            webView.resumeTimers();
            webView.onResume();
        }
        bgServiceHandler.removeCallbacks(stopBgServiceRunnable);
        BackgroundAudioService.stop(this);
    }

    @Override
    protected void onDestroy() {
        bgServiceHandler.removeCallbacks(stopBgServiceRunnable);
        BackgroundAudioService.stop(this);
        releaseWakeLock();
        super.onDestroy();
    }

    private void loadPreferences() {
        currentZoom = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_ZOOM, 125);
        isDarkMode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_DARK, false);
        isMobileUa = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_UA_MOBILE, true);
        pointerSpeedLevel = Math.max(1, Math.min(10, getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_CURSOR_SPEED, 4)));
        pointerAccelLevel = Math.max(0, Math.min(10, getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_CURSOR_ACCEL, 4)));
        scrollSpeedLevel = Math.max(1, Math.min(10, getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_SCROLL_SPEED, 5)));
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        customViewContainer = findViewById(R.id.customViewContainer);
        speedDialView = findViewById(R.id.speedDialView);
        editSearchUrl = findViewById(R.id.editSearchUrl);
        progressBar = findViewById(R.id.progressBar);
        cursorView = findViewById(R.id.cursorView);

        hudOverlay = findViewById(R.id.hudOverlay);
        hudIcon = findViewById(R.id.hudIcon);
        hudText = findViewById(R.id.hudText);

        radialMenuLayout = findViewById(R.id.radialMenuLayout);
        menuBtnHome = findViewById(R.id.menuBtnHome);
        menuBtnReload = findViewById(R.id.menuBtnReload);
        menuBtnModeMouse = findViewById(R.id.menuBtnModeMouse);
        menuBtnModeDpad = findViewById(R.id.menuBtnModeDpad);
        menuBtnModeMovie = findViewById(R.id.menuBtnModeMovie);
        menuBtnUserAgent = findViewById(R.id.menuBtnUserAgent);
        menuBtnZoomIn = findViewById(R.id.menuBtnZoomIn);
        textZoomScale = findViewById(R.id.textZoomScale);
        menuBtnZoomOut = findViewById(R.id.menuBtnZoomOut);
        menuBtnDarkMode = findViewById(R.id.menuBtnDarkMode);
        menuBtnSettings = findViewById(R.id.menuBtnSettings);

        settingsMenuLayout = findViewById(R.id.settingsMenuLayout);
        btnSpeedDown = findViewById(R.id.btnSpeedDown);
        textSettingSpeed = findViewById(R.id.textSettingSpeed);
        btnSpeedUp = findViewById(R.id.btnSpeedUp);
        btnAccelDown = findViewById(R.id.btnAccelDown);
        textSettingAccel = findViewById(R.id.textSettingAccel);
        btnAccelUp = findViewById(R.id.btnAccelUp);
        btnScrollDown = findViewById(R.id.btnScrollDown);
        textSettingScroll = findViewById(R.id.textSettingScroll);
        btnScrollUp = findViewById(R.id.btnScrollUp);
        btnResetPointer = findViewById(R.id.btnResetPointer);
        btnClearCache = findViewById(R.id.btnClearCache);
        btnSettingsDone = findViewById(R.id.btnSettingsDone);
    }

    private void setupWebView() {
        webView.setLayerType(View.LAYER_TYPE_NONE, null);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(currentZoom);
        s.setUserAgentString(isMobileUa ? UA_MOBILE : UA_DESKTOP);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setRenderPriority(WebSettings.RenderPriority.HIGH);

        webView.addJavascriptInterface(new MediaBridge(), "CleanTvBridge");
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, AdBlockEngine.getSubframeNavScript(), Collections.singleton("*"));
        }

        applyDarkMode();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    if (AdBlockEngine.isAdUrl(request.getUrl().toString())) {
                        return AdBlockEngine.createEmptyResponse();
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                onPageOrSiteChanged(url, true);
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(10);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                if (!isReload) {
                    onPageOrSiteChanged(url, true);
                }
                AdBlockEngine.injectVideoShield(view);
                view.postDelayed(() -> AdBlockEngine.injectVideoShield(view), 800);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                AdBlockEngine.injectVideoShield(view);
                if (isDarkMode) {
                    AdBlockEngine.applyForcedDarkMode(view, true);
                }
                boolean dpadActive = (controlMode == MODE_DPAD);
                view.evaluateJavascript(
                        "window._cleanTvDpadMode = " + dpadActive + "; if (window._cleanTvSetDpadFlag) window._cleanTvSetDpadFlag(" + dpadActive + ");" +
                        (dpadActive ? " if (window._cleanTvInitFocus) window._cleanTvInitFocus();" : ""),
                        null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress >= 75) {
                    AdBlockEngine.injectVideoShield(view);
                }
                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customVideoView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customVideoView = view;
                customViewCallback = callback;
                customVideoView.setFocusable(true);
                customViewContainer.setFocusable(true);
                customViewContainer.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                customViewContainer.setVisibility(View.VISIBLE);
                webView.setVisibility(View.INVISIBLE);
                isVideoActive = true;
                if (controlMode == MODE_DPAD || controlMode == MODE_MOVIE) {
                    customViewContainer.requestFocus();
                    if (controlMode == MODE_DPAD) {
                        webView.postDelayed(() -> {
                            if (controlMode == MODE_DPAD && customVideoView != null) {
                                dispatchHoverToActive(customViewContainer.getWidth() * 0.5f, customViewContainer.getHeight() * 0.5f);
                                webView.evaluateJavascript("if (window._cleanTvInitFocus) window._cleanTvInitFocus();", null);
                            }
                        }, 180);
                    }
                } else {
                    cursorView.requestFocus();
                }
                showHud("🎬", "Video Fullscreen Active");
            }

            @Override
            public void onHideCustomView() {
                if (customVideoView == null) return;
                customViewContainer.removeView(customVideoView);
                customViewContainer.setVisibility(View.GONE);
                customViewContainer.setFocusable(false);
                customVideoView = null;
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                webView.setVisibility(View.VISIBLE);
                isVideoActive = false;
                if (controlMode == MODE_MOUSE && speedDialView.getVisibility() != View.VISIBLE) {
                    cursorView.requestFocus();
                } else {
                    webView.requestFocus();
                }
            }
        });
    }

    private void showKeyboard(View view) {
        if (view == null) return;
        view.setFocusableInTouchMode(true);
        view.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard(View view) {
        if (view == null) return;
        view.setFocusableInTouchMode(false);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void setupSpeedDial() {
        editSearchUrl.setOnClickListener(v -> showKeyboard(editSearchUrl));

        View searchBarContainer = findViewById(R.id.searchBarContainer);
        editSearchUrl.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                hideKeyboard(v);
            }
            if (searchBarContainer != null && searchBarContainer.getBackground() instanceof android.graphics.drawable.GradientDrawable) {
                android.graphics.drawable.GradientDrawable gd = (android.graphics.drawable.GradientDrawable) searchBarContainer.getBackground().mutate();
                gd.setStroke((int) (2 * getResources().getDisplayMetrics().density), hasFocus ? 0xFF00E5FF : 0x33FFFFFF);
            }
        });

        editSearchUrl.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    hideKeyboard(editSearchUrl);
                    findViewById(R.id.cardHdMovie2).requestFocus();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && editSearchUrl.getSelectionEnd() >= editSearchUrl.getText().length()) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    hideKeyboard(editSearchUrl);
                    findViewById(R.id.btnGoSearch).requestFocus();
                }
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP &&
                    (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
                String text = editSearchUrl.getText().toString().trim();
                if (!text.isEmpty()) {
                    hideKeyboard(editSearchUrl);
                    handleSearchOrUrl(text);
                    return true;
                }
            }
            return false;
        });

        editSearchUrl.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE) {
                String text = editSearchUrl.getText().toString().trim();
                if (!text.isEmpty()) {
                    hideKeyboard(editSearchUrl);
                    handleSearchOrUrl(text);
                    return true;
                }
            }
            return false;
        });

        findViewById(R.id.btnGoSearch).setOnClickListener(v -> {
            hideKeyboard(editSearchUrl);
            handleSearchOrUrl(editSearchUrl.getText().toString());
        });

        // 5 Pinned Cards
        final String hdMovieSearchUrl = "https://www.google.com/search?q=hdmovie2&rlz=1C1GCEA_enIN1163IN1163&oq=hdmo&gs_lcrp=EgZjaHJvbWUqBggBECMYJzIGCAAQRRg5MgYIARAjGCcyBggCEEUYOzIGCAMQIxgnMgYIBBBFGEEyBggFEEUYQTIGCAYQRRhBMgYIBxBFGDzSAQgzOTg2ajBqN6gCCLACAfEF_YIRJvXL-AA&sourceid=chrome&source=chrome.ob&ie=UTF-8";

        View cardLastActivePage = findViewById(R.id.cardLastActivePage);
        if (cardLastActivePage != null) {
            cardLastActivePage.setOnClickListener(v -> {
                hideKeyboard(editSearchUrl);
                String current = (webView != null) ? webView.getUrl() : null;
                if (current != null && !current.isEmpty() && !current.equals("about:blank")) {
                    speedDialView.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                    enterMouseMode(true);
                    showHud("⏱️", "Resumed Active Page");
                } else if (lastMainUrl != null && !lastMainUrl.isEmpty() && !lastMainUrl.equals("about:blank")) {
                    loadUrl(lastMainUrl);
                } else {
                    loadUrl("https://www.google.com");
                }
            });
        }

        findViewById(R.id.cardHdMovie2).setOnClickListener(v -> loadUrl(hdMovieSearchUrl));
        findViewById(R.id.cardStreamPlay).setOnClickListener(v -> loadUrl("file:///android_asset/stream_hub.html?view=movies"));
        findViewById(R.id.cardGameLinks).setOnClickListener(v -> loadUrl("file:///android_asset/stream_hub.html?view=games"));
        findViewById(R.id.cardYouTube).setOnClickListener(v -> loadUrl("https://m.youtube.com"));
    }

    private void setupRadialMenu() {
        // Row 1: Speed Dial & Reload
        menuBtnHome.setOnClickListener(v -> {
            hideRadialMenu();
            openSpeedDial();
        });

        menuBtnReload.setOnClickListener(v -> {
            hideRadialMenu();
            webView.reload();
            showHud("🔄", "Reloading...");
        });

        // Row 2: 3 Explicit Mode Buttons (Mouse | D-Pad | Movie)
        menuBtnModeMouse.setOnClickListener(v -> {
            hideRadialMenu();
            enterMouseMode(true);
        });

        menuBtnModeDpad.setOnClickListener(v -> {
            hideRadialMenu();
            enterDpadMode();
        });

        menuBtnModeMovie.setOnClickListener(v -> {
            hideRadialMenu();
            enterMovieMode();
        });

        // Row 3: Zoom In, Scale Indicator, Zoom Out
        textZoomScale.setText(currentZoom + "%");
        menuBtnZoomIn.setOnClickListener(v -> {
            currentZoom = Math.min(200, currentZoom + 25);
            webView.getSettings().setTextZoom(currentZoom);
            textZoomScale.setText(currentZoom + "%");
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(KEY_ZOOM, currentZoom).apply();
            showHud("🔍", "Zoom: " + currentZoom + "%");
        });

        menuBtnZoomOut.setOnClickListener(v -> {
            currentZoom = Math.max(75, currentZoom - 25);
            webView.getSettings().setTextZoom(currentZoom);
            textZoomScale.setText(currentZoom + "%");
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(KEY_ZOOM, currentZoom).apply();
            showHud("🔎", "Zoom: " + currentZoom + "%");
        });

        // Row 4: User-Agent & Dark Mode
        menuBtnUserAgent.setOnClickListener(v -> {
            isMobileUa = !isMobileUa;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_UA_MOBILE, isMobileUa).apply();
            webView.getSettings().setUserAgentString(isMobileUa ? UA_MOBILE : UA_DESKTOP);
            menuBtnUserAgent.setText(isMobileUa ? "📱 Mobile (Smooth)" : "💻 Desktop (Full)");
            showHud("🔄", isMobileUa ? "Mobile UA (Smooth Video)" : "Desktop UA");
            webView.reload();
        });

        menuBtnDarkMode.setOnClickListener(v -> {
            isDarkMode = !isDarkMode;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_DARK, isDarkMode).apply();
            applyDarkMode();
            AdBlockEngine.applyForcedDarkMode(webView, isDarkMode);
            menuBtnDarkMode.setText(isDarkMode ? "☀️ Force Dark: ON" : "🌙 Force Dark: OFF");
            showHud(isDarkMode ? "🌙" : "☀️", isDarkMode ? "Dark Mode: ON" : "Dark Mode: OFF");
        });

        // Row 5: TV Bro-Style Settings Overlay
        menuBtnSettings.setOnClickListener(v -> {
            radialMenuLayout.setVisibility(View.GONE);
            openSettingsMenu();
        });
    }

    private void updatePointerSettingsUi() {
        if (textSettingSpeed != null) {
            textSettingSpeed.setText("Pointer Speed: " + pointerSpeedLevel + " / 10");
        }
        if (textSettingAccel != null) {
            textSettingAccel.setText(pointerAccelLevel == 0
                    ? "Acceleration: OFF"
                    : ("Acceleration: " + pointerAccelLevel + " / 10"));
        }
        if (textSettingScroll != null) {
            textSettingScroll.setText("Scroll Speed: " + scrollSpeedLevel + " / 10");
        }
    }

    private void savePointerSettings() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putInt(KEY_CURSOR_SPEED, pointerSpeedLevel)
                .putInt(KEY_CURSOR_ACCEL, pointerAccelLevel)
                .putInt(KEY_SCROLL_SPEED, scrollSpeedLevel)
                .apply();
        updatePointerSettingsUi();
    }

    private void setupSettingsMenu() {
        updatePointerSettingsUi();

        btnSpeedDown.setOnClickListener(v -> {
            pointerSpeedLevel = Math.max(1, pointerSpeedLevel - 1);
            savePointerSettings();
        });
        btnSpeedUp.setOnClickListener(v -> {
            pointerSpeedLevel = Math.min(10, pointerSpeedLevel + 1);
            savePointerSettings();
        });

        btnAccelDown.setOnClickListener(v -> {
            pointerAccelLevel = Math.max(0, pointerAccelLevel - 1);
            savePointerSettings();
        });
        btnAccelUp.setOnClickListener(v -> {
            pointerAccelLevel = Math.min(10, pointerAccelLevel + 1);
            savePointerSettings();
        });

        btnScrollDown.setOnClickListener(v -> {
            scrollSpeedLevel = Math.max(1, scrollSpeedLevel - 1);
            savePointerSettings();
        });
        btnScrollUp.setOnClickListener(v -> {
            scrollSpeedLevel = Math.min(10, scrollSpeedLevel + 1);
            savePointerSettings();
        });

        btnResetPointer.setOnClickListener(v -> {
            pointerSpeedLevel = 4;
            pointerAccelLevel = 4;
            scrollSpeedLevel = 5;
            savePointerSettings();
            showHud("↺", "Pointer Defaults Restored");
        });

        btnClearCache.setOnClickListener(v -> {
            webView.clearCache(true);
            webView.clearHistory();
            System.gc();
            showHud("🧹", "Memory & Cache Cleaned!");
            Toast.makeText(this, "Memory & Cache Cleaned ✓", Toast.LENGTH_SHORT).show();
        });

        btnSettingsDone.setOnClickListener(v -> hideSettingsMenu());
    }

    private void handleSearchOrUrl(String input) {
        if (input == null || input.trim().isEmpty()) return;
        String q = input.trim();
        if (q.startsWith("http://") || q.startsWith("https://")) {
            loadUrl(q);
        } else if (q.contains(".") && !q.contains(" ")) {
            loadUrl("https://" + q);
        } else {
            loadUrl("https://www.google.com/search?q=" + Uri.encode(q));
        }
    }

    public void loadUrl(String url) {
        hideKeyboard(editSearchUrl);
        lastMainUrl = url != null ? url : "";
        speedDialView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        enterMouseMode(true);
        webView.loadUrl(url);
    }

    private void openSpeedDial() {
        hideKeyboard(editSearchUrl);
        webView.setVisibility(View.GONE);
        if (customViewContainer != null && customViewContainer.getVisibility() == View.VISIBLE) {
            customViewContainer.setVisibility(View.GONE);
        }
        speedDialView.setVisibility(View.VISIBLE);
        controlMode = MODE_MOUSE;
        cursorView.setPointerVisible(false);
        cursorView.setFocusBoxVisible(false);
        cursorView.clearFocusRect();
        updateModeSelectionUi();
        View targetFocus = (webView != null && webView.getUrl() != null && !webView.getUrl().isEmpty() && !webView.getUrl().equals("about:blank"))
                ? findViewById(R.id.cardLastActivePage) : findViewById(R.id.cardHdMovie2);
        if (targetFocus != null) targetFocus.requestFocus();
    }

    private void applyDarkMode() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(webView.getSettings(),
                    isDarkMode ? WebSettingsCompat.FORCE_DARK_ON : WebSettingsCompat.FORCE_DARK_OFF);
        }
    }

    // =========================================================================
    // D-PAD & TV REMOTE INTERACTION ENGINE
    // =========================================================================

    private boolean isModalVisible() {
        return (radialMenuLayout != null && radialMenuLayout.getVisibility() == View.VISIBLE)
                || (settingsMenuLayout != null && settingsMenuLayout.getVisibility() == View.VISIBLE);
    }

    private View getActiveContentView() {
        if (customVideoView != null && customViewContainer != null && customViewContainer.getVisibility() == View.VISIBLE) {
            return customViewContainer;
        }
        return webView;
    }

    private void dispatchHoverToActive(float x, float y) {
        View target = getActiveContentView();
        if (target == null) return;
        long now = SystemClock.uptimeMillis();
        MotionEvent hover = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, x, y, 0);
        target.dispatchGenericMotionEvent(hover);
        hover.recycle();
    }

    private void dispatchTouchToView(View target, float x, float y) {
        if (target == null) return;
        long downTime = SystemClock.uptimeMillis();
        MotionEvent downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent upEvent = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, x, y, 0);
        target.dispatchTouchEvent(downEvent);
        target.dispatchTouchEvent(upEvent);
        downEvent.recycle();
        upEvent.recycle();
    }

    private void dispatchClickAtCursor() {
        dispatchTouchToView(getActiveContentView(), cursorView.getCursorX(), cursorView.getCursorY());
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        // 1. Long-press BACK detection -> Move Forward in Webpage / Return to Mouse Pointer Mode
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (backLongHandled && action == KeyEvent.ACTION_DOWN) {
                return true;
            }

            if ((event.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0) {
                if (!backLongHandled) {
                    handleLongBack();
                }
                return true;
            }
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    backLongHandled = false;
                    keyHandler.removeCallbacks(longPressBackRunnable);
                    keyHandler.postDelayed(longPressBackRunnable, 450);
                }
                return true;
            } else if (action == KeyEvent.ACTION_UP) {
                keyHandler.removeCallbacks(longPressBackRunnable);
                if (backLongHandled) {
                    backLongHandled = false;
                    return true;
                }
                // Regular short BACK press
                return handleShortBack();
            }
            return super.dispatchKeyEvent(event);
        }

        // 2. Long-press OK detection -> Quick Actions Menu
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (okLongHandled && action == KeyEvent.ACTION_DOWN) {
                return true;
            }

            if ((event.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0) {
                if (!okLongHandled) {
                    okLongHandled = true;
                    keyHandler.removeCallbacks(longPressOkRunnable);
                    openRadialMenu();
                }
                return true;
            }
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    okLongHandled = false;
                    keyHandler.removeCallbacks(longPressOkRunnable);
                    if (!isModalVisible()) {
                        keyHandler.postDelayed(longPressOkRunnable, 450);
                    }
                }
                if (isModalVisible()) {
                    return super.dispatchKeyEvent(event);
                }
                return true;
            } else if (action == KeyEvent.ACTION_UP) {
                keyHandler.removeCallbacks(longPressOkRunnable);
                if (okLongHandled || (SystemClock.uptimeMillis() - menuOpenedTimestamp < 450)) {
                    okLongHandled = false;
                    return true;
                }
                // Regular short OK click inside Quick Menu or Settings Menu
                if (isModalVisible()) {
                    View focused = getCurrentFocus();
                    if (focused != null && focused.isClickable()) {
                        focused.performClick();
                        return true;
                    }
                    return super.dispatchKeyEvent(event);
                }
                if (speedDialView.getVisibility() != View.VISIBLE) {
                    if (controlMode == MODE_MOUSE) {
                        dispatchClickAtCursor();
                        return true;
                    } else if (controlMode == MODE_MOVIE) {
                        toggleVideoPlayPause();
                        return true;
                    } else if (controlMode == MODE_DPAD) {
                        webView.evaluateJavascript("window._cleanTvActivateFocused ? window._cleanTvActivateFocused() : 'none'", res -> {
                            boolean handled = res != null && (res.contains("native-tap") || res.contains("post-tap")
                                    || res.contains("postmsg") || res.contains("clicked") || res.contains("text-input"));
                            if (!handled) {
                                View target = getActiveContentView();
                                if (target != null) {
                                    long now = SystemClock.uptimeMillis();
                                    target.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0));
                                    target.dispatchKeyEvent(new KeyEvent(now, now + 10, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0));
                                }
                            }
                        });
                        return true;
                    }
                }
                if (speedDialView.getVisibility() == View.VISIBLE) {
                    View focused = getCurrentFocus();
                    if (focused != null && focused.isClickable()) {
                        focused.performClick();
                        return true;
                    }
                }
                return super.dispatchKeyEvent(event);
            }
        }

        // 3. Modal Menu Active Navigation
        if (isModalVisible()) {
            return super.dispatchKeyEvent(event);
        }

        // 4. Explicit Movie Mode or D-Pad Navigator Mode
        if (controlMode != MODE_MOUSE && speedDialView.getVisibility() != View.VISIBLE) {
            if (controlMode == MODE_MOVIE) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (action == KeyEvent.ACTION_DOWN) {
                        adjustVolume(keyCode == KeyEvent.KEYCODE_DPAD_UP);
                    }
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (action == KeyEvent.ACTION_DOWN) {
                        seekVideo(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT);
                    }
                    return true;
                }
            } else if (controlMode == MODE_DPAD) {
                // Webpage & Fullscreen D-Pad Navigator Selector (▲/▼/◀/▶ + OK)
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                        keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {

                    String icon = "";
                    String text = "";
                    int gravity = Gravity.CENTER;
                    String jsArrow = "";
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        icon = "▲"; text = "Up / Scroll"; gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; jsArrow = "ArrowUp";
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        icon = "▼"; text = "Down / Scroll"; gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL; jsArrow = "ArrowDown";
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        icon = "◀"; text = "Left / Scroll"; gravity = Gravity.CENTER_VERTICAL | Gravity.START; jsArrow = "ArrowLeft";
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        icon = "▶"; text = "Right / Scroll"; gravity = Gravity.CENTER_VERTICAL | Gravity.END; jsArrow = "ArrowRight";
                    }

                    if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                        showHud(icon, text, gravity);
                    }

                    if (customVideoView != null) {
                        // In fullscreen WebView is View.INVISIBLE underneath customViewContainer; wake controls & step DOM/iframe focus
                        if (action == KeyEvent.ACTION_DOWN) {
                            dispatchHoverToActive(customViewContainer.getWidth() * 0.5f, customViewContainer.getHeight() * 0.5f);
                            final KeyEvent copyEvent = new KeyEvent(event);
                            webView.evaluateJavascript("window._cleanTvStepFocus ? window._cleanTvStepFocus('" + jsArrow + "') : 'false'", res -> {
                                if (res == null || res.contains("cross-fs") || res.contains("false")) {
                                    customViewContainer.requestFocus();
                                    int fallbackKey = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                                            ? KeyEvent.KEYCODE_TAB : copyEvent.getKeyCode();
                                    customViewContainer.dispatchKeyEvent(copyEvent);
                                    customViewContainer.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
                                    if (fallbackKey == KeyEvent.KEYCODE_TAB) {
                                        long now = SystemClock.uptimeMillis();
                                        customViewContainer.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, 0));
                                        customViewContainer.dispatchKeyEvent(new KeyEvent(now, now + 10, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB, 0));
                                    }
                                }
                            });
                        }
                        return true;
                    }

                    webView.dispatchKeyEvent(event);
                    return true;
                }
            }
        }

        // 5. Virtual Cursor Movement & Tunable Edge Scrolling (Active by default in Mouse Mode)
        if (controlMode == MODE_MOUSE && speedDialView.getVisibility() != View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {

                if (action == KeyEvent.ACTION_DOWN) {
                    if (activeDpadCode != keyCode) {
                        activeDpadCode = keyCode;
                        cursorHoldFrames = 0;
                    }
                    if (!isMovingCursor) {
                        isMovingCursor = true;
                        cursorHoldFrames = 0;
                        cursorHandler.post(cursorMoveRunnable);
                    }
                } else if (action == KeyEvent.ACTION_UP) {
                    isMovingCursor = false;
                    cursorHoldFrames = 0;
                    cursorHandler.removeCallbacks(cursorMoveRunnable);
                }
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private boolean handleShortBack() {
        if (settingsMenuLayout.getVisibility() == View.VISIBLE) {
            hideSettingsMenu();
            return true;
        }
        if (radialMenuLayout.getVisibility() == View.VISIBLE) {
            hideRadialMenu();
            return true;
        }
        if (customVideoView != null) {
            ((WebChromeClient) webView.getWebChromeClient()).onHideCustomView();
            return true;
        }
        if (speedDialView.getVisibility() != View.VISIBLE) {
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            } else {
                openSpeedDial();
                return true;
            }
        }
        finish();
        return true;
    }

    private void blockBackgroundFocus(boolean block) {
        int mode = block ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS;
        speedDialView.setDescendantFocusability(mode);
        customViewContainer.setDescendantFocusability(mode);
        webView.setFocusable(!block);
        if (customVideoView != null) {
            customViewContainer.setFocusable(!block);
            customVideoView.setFocusable(!block);
        }
    }

    private void restoreActiveFocus() {
        boolean onHome = speedDialView.getVisibility() == View.VISIBLE;
        cursorView.setPointerVisible(!onHome && controlMode == MODE_MOUSE && !isModalVisible());
        cursorView.setFocusBoxVisible(!onHome && controlMode == MODE_DPAD && !isModalVisible());
        if (onHome) {
            findViewById(R.id.cardHdMovie2).requestFocus();
        } else if (controlMode == MODE_MOUSE && cursorView.isPointerVisible()) {
            cursorView.requestFocus();
        } else {
            View active = getActiveContentView();
            if (active != null) active.requestFocus();
        }
    }

    private void openRadialMenu() {
        if (radialMenuLayout.getVisibility() == View.VISIBLE) return;
        settingsMenuLayout.setVisibility(View.GONE);

        menuOpenedTimestamp = SystemClock.uptimeMillis();

        updateModeSelectionUi();
        textZoomScale.setText(currentZoom + "%");
        menuBtnDarkMode.setText(isDarkMode ? "☀️ Force Dark: ON" : "🌙 Force Dark: OFF");
        menuBtnUserAgent.setText(isMobileUa ? "📱 Mobile (Smooth)" : "💻 Desktop (Full)");

        cursorView.setPointerVisible(false);
        cursorView.setFocusBoxVisible(false);
        blockBackgroundFocus(true);

        radialMenuLayout.setVisibility(View.VISIBLE);
        menuBtnHome.requestFocus();
    }

    private void hideRadialMenu() {
        radialMenuLayout.setVisibility(View.GONE);
        blockBackgroundFocus(false);
        restoreActiveFocus();
    }

    private void openSettingsMenu() {
        menuOpenedTimestamp = SystemClock.uptimeMillis();
        updatePointerSettingsUi();
        cursorView.setPointerVisible(false);
        cursorView.setFocusBoxVisible(false);
        blockBackgroundFocus(true);
        settingsMenuLayout.setVisibility(View.VISIBLE);
        btnSpeedDown.requestFocus();
    }

    private void hideSettingsMenu() {
        settingsMenuLayout.setVisibility(View.GONE);
        blockBackgroundFocus(false);
        restoreActiveFocus();
    }

    private void hideAllModals() {
        radialMenuLayout.setVisibility(View.GONE);
        settingsMenuLayout.setVisibility(View.GONE);
        blockBackgroundFocus(false);
        restoreActiveFocus();
    }

    private boolean isVideoElementPlaying() {
        return isVideoActive;
    }

    private void adjustVolume(boolean up) {
        if (controlMode != MODE_MOVIE) return;
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;

        int stream = AudioManager.STREAM_MUSIC;
        int max = am.getStreamMaxVolume(stream);
        int current = am.getStreamVolume(stream);

        if (up && current < max) {
            am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, 0);
            current++;
        } else if (!up && current > 0) {
            am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, 0);
            current--;
        }

        int percent = (int) ((current * 100f) / max);
        showHud(up ? "🔊" : "🔉", "Volume: " + percent + "%",
                up ? (Gravity.TOP | Gravity.CENTER_HORIZONTAL) : (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
    }

    private void seekVideo(boolean forward) {
        if (controlMode != MODE_MOVIE) return;
        int seconds = forward ? 10 : -10;
        String js = "(function() {\n" +
                "  if (window._cleanTvVideo && window._cleanTvVideo.seek) {\n" +
                "    return window._cleanTvVideo.seek(" + seconds + ");\n" +
                "  }\n" +
                "  try {\n" +
                "    var yt = document.getElementById('movie_player') || document.querySelector('.html5-video-player');\n" +
                "    if (yt) {\n" +
                "      if (typeof yt.seekBy === 'function') { yt.seekBy(" + seconds + "); return 'yt'; }\n" +
                "      if (typeof yt.seekTo === 'function') {\n" +
                "        var ct = typeof yt.getCurrentTime === 'function' ? yt.getCurrentTime() : 0;\n" +
                "        yt.seekTo(ct + (" + seconds + "), true);\n" +
                "        return 'yt-seekto';\n" +
                "      }\n" +
                "    }\n" +
                "  } catch(e) {}\n" +
                "  var v = document.querySelector('video');\n" +
                "  if (v) {\n" +
                "    var target = Math.max(0, Math.min(v.duration || 999999, v.currentTime + (" + seconds + ")));\n" +
                "    if (typeof v.fastSeek === 'function') { try { v.fastSeek(target); } catch(e) { v.currentTime = target; } }\n" +
                "    else { v.currentTime = target; }\n" +
                "    return 'html5';\n" +
                "  }\n" +
                "  return 'none';\n" +
                "})();";
        webView.evaluateJavascript(js, null);
        showHud(forward ? "⏩" : "⏪", (forward ? "+10s" : "-10s") + " Seek",
                forward ? (Gravity.CENTER_VERTICAL | Gravity.END) : (Gravity.CENTER_VERTICAL | Gravity.START));
    }

    private void toggleVideoPlayPause() {
        if (controlMode != MODE_MOVIE) return;
        String js = "(function() {\n" +
                "  if (window._cleanTvVideo && window._cleanTvVideo.togglePlayPause) {\n" +
                "    return window._cleanTvVideo.togglePlayPause();\n" +
                "  }\n" +
                "  try {\n" +
                "    var yt = document.getElementById('movie_player') || document.querySelector('.html5-video-player');\n" +
                "    if (yt && typeof yt.getPlayerState === 'function') {\n" +
                "      var state = yt.getPlayerState();\n" +
                "      if (state === 1) { yt.pauseVideo(); return 'pause'; }\n" +
                "      else { yt.playVideo(); return 'play'; }\n" +
                "    }\n" +
                "  } catch(e) {}\n" +
                "  var btn = document.querySelector('.ytp-play-button, button[aria-label*=\"Play\" i], button[aria-label*=\"Pause\" i]');\n" +
                "  if (btn) { btn.click(); return 'click'; }\n" +
                "  var v = document.querySelector('video');\n" +
                "  if (v) {\n" +
                "    if (v.paused) { v.play(); return 'play'; }\n" +
                "    else { v.pause(); return 'pause'; }\n" +
                "  }\n" +
                "  return 'none';\n" +
                "})();";
        webView.evaluateJavascript(js, result -> {
            if (result != null && result.contains("pause")) {
                showHud("⏸️", "Video Paused");
            } else if (result != null && result.contains("play")) {
                showHud("▶️", "Video Playing");
            } else {
                showHud("⏯️", "Play / Pause");
            }
        });
    }

    private void showHud(String icon, String text, int gravity) {
        hudIcon.setText(icon);
        hudText.setText(text);

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) hudOverlay.getLayoutParams();
        lp.gravity = gravity;
        int m = (int) (18 * getResources().getDisplayMetrics().density);

        boolean isTop = (gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.TOP;
        boolean isBottom = (gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM;
        boolean isStart = (gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == Gravity.START
                || (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT;
        boolean isEnd = (gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == Gravity.END
                || (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT;

        int leftMargin = isStart ? m : 0;
        int topMargin = isTop ? m : 0;
        int rightMargin = isEnd ? m : 0;
        int bottomMargin = isBottom ? m : 0;

        lp.setMargins(leftMargin, topMargin, rightMargin, bottomMargin);
        lp.setMarginStart(leftMargin);
        lp.setMarginEnd(rightMargin);
        hudOverlay.setLayoutParams(lp);

        hudOverlay.setVisibility(View.VISIBLE);
        hudHandler.removeCallbacks(hideHudRunnable);
        hudHandler.postDelayed(hideHudRunnable, 850);
    }

    private void showHud(String icon, String text) {
        showHud(icon, text, Gravity.CENTER);
    }
}
