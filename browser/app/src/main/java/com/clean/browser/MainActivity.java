package com.clean.browser;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
import androidx.webkit.WebViewFeature;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "browser_prefs";
    private static final String KEY_ZOOM = "page_zoom";
    private static final String KEY_DARK = "dark_mode";
    private static final String KEY_UA_MOBILE = "ua_mobile";

    private static final String UA_DESKTOP = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final String UA_MOBILE = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36";

    // Views
    private WebView webView;
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

    // Radial Menu
    private View radialMenuLayout;
    private TextView menuBtnZoomIn, menuBtnZoomOut, textZoomScale, menuBtnDarkMode, menuBtnUserAgent, menuBtnReload, menuBtnHome;
    private TextView menuBtnDpadMode, menuBtnSettings;

    // State
    private int currentZoom = 125; // Default 125% for 10-foot TV viewing
    private boolean isDarkMode = false;
    private boolean isMobileUa = true; // Mobile UA default for smooth H.264 video decoding
    private boolean isVideoActive = false;
    private volatile boolean isVideoPlaying = false; // Real-time media detector state
    private boolean isMouseMode = true; // Fully mouse by default!

    public class MediaBridge {
        @android.webkit.JavascriptInterface
        public void setVideoPlaying(boolean playing) {
            new Handler(Looper.getMainLooper()).post(() -> {
                isVideoPlaying = playing;
            });
        }
    }

    private boolean isMovieOrVideoActive() {
        if (customVideoView != null || isVideoActive || isVideoPlaying) {
            return true;
        }
        if (webView != null && webView.getUrl() != null) {
            String lower = webView.getUrl().toLowerCase();
            if (lower.contains("youtube.com/watch") || lower.contains("youtu.be/") ||
                lower.contains("hdmovie2") || lower.contains("/movie/") ||
                lower.contains("/watch") || lower.contains("/stream") || lower.contains("/video")) {
                return true;
            }
        }
        return false;
    }

    // D-Pad Mouse movement handler
    private final Handler cursorHandler = new Handler(Looper.getMainLooper());
    private float cursorSpeed = 16f;
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
            backLongHandled = true;
            if (!isMouseMode) {
                enterMouseMode();
                cursorView.resetToCenter();
            } else {
                cursorView.resetToCenter();
                showHud("🎯", "Cursor Centered");
            }
        }
    };

    private void enterDpadMode() {
        isMouseMode = false;
        cursorView.setPointerVisible(false);
        if (isMovieOrVideoActive()) {
            showHud("🎬", "Movie Mode (▲/▼ Vol, ◀/▶ Seek, OK Pause)\nHold BACK to return to Mouse");
        } else {
            showHud("🎮", "D-Pad Arrow Mode (▲/▼ Scroll, OK Click)\nHold BACK to return to Mouse");
        }
    }

    private void enterMouseMode() {
        isMouseMode = true;
        cursorView.setPointerVisible(true);
        showHud("🎯", "Mouse Pointer Active");
    }

    private void scrollPageSlow(int deltaY) {
        webView.scrollBy(0, deltaY);
        webView.evaluateJavascript("window.scrollBy(0, " + deltaY + ");", null);
    }

    private void scrollPageWebpage(int deltaY, boolean isRepeat) {
        webView.scrollBy(0, deltaY);
        String behavior = isRepeat ? "auto" : "smooth";
        webView.evaluateJavascript("window.scrollBy({top: " + deltaY + ", behavior: '" + behavior + "'});", null);
        if (!isRepeat) {
            showHud(deltaY < 0 ? "▲" : "▼", deltaY < 0 ? "Scroll Up" : "Scroll Down");
        }
    }

    private final Runnable cursorMoveRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isMovingCursor || !isMouseMode || !cursorView.isPointerVisible()) return;

            float dx = 0, dy = 0;
            if (activeDpadCode == KeyEvent.KEYCODE_DPAD_UP) dy = -cursorSpeed;
            else if (activeDpadCode == KeyEvent.KEYCODE_DPAD_DOWN) dy = cursorSpeed;
            else if (activeDpadCode == KeyEvent.KEYCODE_DPAD_LEFT) dx = -cursorSpeed;
            else if (activeDpadCode == KeyEvent.KEYCODE_DPAD_RIGHT) dx = cursorSpeed;

            cursorView.moveBy(dx, dy);

            // Gentle continuous edge scrolling: 8px per frame, keeping cursor visible at edge
            float curY = cursorView.getCursorY();
            int viewH = cursorView.getHeight() > 0 ? cursorView.getHeight() : 720;
            if (dy > 0 && curY >= viewH - 45) {
                scrollPageSlow(8);
            } else if (dy < 0 && curY <= 45) {
                scrollPageSlow(-8);
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
        setContentView(R.layout.activity_main);

        loadPreferences();
        initViews();
        setupWebView();
        setupSpeedDial();
        setupRadialMenu();

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
        }
    }

    private void loadPreferences() {
        currentZoom = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_ZOOM, 125);
        isDarkMode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_DARK, false);
        isMobileUa = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_UA_MOBILE, true);
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
        menuBtnDpadMode = findViewById(R.id.menuBtnDpadMode);
        menuBtnUserAgent = findViewById(R.id.menuBtnUserAgent);
        menuBtnZoomIn = findViewById(R.id.menuBtnZoomIn);
        textZoomScale = findViewById(R.id.textZoomScale);
        menuBtnZoomOut = findViewById(R.id.menuBtnZoomOut);
        menuBtnDarkMode = findViewById(R.id.menuBtnDarkMode);
        menuBtnSettings = findViewById(R.id.menuBtnSettings);
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
                isVideoPlaying = false;
                isVideoActive = false;
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(10);
                AdBlockEngine.injectVideoShield(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                AdBlockEngine.injectVideoShield(view);
                if (isDarkMode) {
                    AdBlockEngine.applyForcedDarkMode(view, true);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
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
                customViewContainer.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                customViewContainer.setVisibility(View.VISIBLE);
                webView.setVisibility(View.GONE);
                isVideoActive = true;
                showHud("🎬", "Video Fullscreen Active");
            }

            @Override
            public void onHideCustomView() {
                if (customVideoView == null) return;
                customViewContainer.removeView(customVideoView);
                customViewContainer.setVisibility(View.GONE);
                customVideoView = null;
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                webView.setVisibility(View.VISIBLE);
                isVideoActive = false;
            }
        });
    }

    private void showKeyboard(View view) {
        if (view == null) return;
        view.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard(View view) {
        if (view == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void setupSpeedDial() {
        editSearchUrl.setOnClickListener(v -> showKeyboard(editSearchUrl));
        editSearchUrl.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                showKeyboard(editSearchUrl);
            }
        });

        editSearchUrl.setOnKeyListener((v, keyCode, event) -> {
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

        // 4 Pinned Cards
        findViewById(R.id.cardHdMovie2).setOnClickListener(v -> loadUrl("https://hdmovie2.vg"));
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

        // Row 2: Turn ON/OFF D-Pad Mode & Mobile/Desktop
        menuBtnDpadMode.setOnClickListener(v -> {
            hideRadialMenu();
            if (isMouseMode) {
                enterDpadMode();
            } else {
                enterMouseMode();
            }
        });

        menuBtnUserAgent.setOnClickListener(v -> {
            isMobileUa = !isMobileUa;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_UA_MOBILE, isMobileUa).apply();
            webView.getSettings().setUserAgentString(isMobileUa ? UA_MOBILE : UA_DESKTOP);
            menuBtnUserAgent.setText(isMobileUa ? "📱 Mobile (Smooth)" : "💻 Desktop (Full)");
            showHud("🔄", isMobileUa ? "Mobile UA (Smooth Video)" : "Desktop UA");
            webView.reload();
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

        // Row 4: Dark Mode & Settings / Clear RAM
        menuBtnDarkMode.setOnClickListener(v -> {
            isDarkMode = !isDarkMode;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_DARK, isDarkMode).apply();
            applyDarkMode();
            AdBlockEngine.applyForcedDarkMode(webView, isDarkMode);
            menuBtnDarkMode.setText(isDarkMode ? "☀️ Force Dark: ON" : "🌙 Force Dark: OFF");
            showHud(isDarkMode ? "🌙" : "☀️", isDarkMode ? "Dark Mode: ON" : "Dark Mode: OFF");
        });

        menuBtnSettings.setOnClickListener(v -> {
            hideRadialMenu();
            webView.clearCache(true);
            webView.clearHistory();
            System.gc();
            showHud("🧹", "Memory & Cache Cleaned!");
            Toast.makeText(this, "Memory & Cache Cleaned ✓", Toast.LENGTH_SHORT).show();
        });
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
        speedDialView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        enterMouseMode();
        webView.loadUrl(url);
    }

    private void openSpeedDial() {
        webView.setVisibility(View.GONE);
        if (customViewContainer != null && customViewContainer.getVisibility() == View.VISIBLE) {
            customViewContainer.setVisibility(View.GONE);
        }
        speedDialView.setVisibility(View.VISIBLE);
        cursorView.setPointerVisible(false);
        isMouseMode = true;
        findViewById(R.id.cardHdMovie2).requestFocus();
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        // 1. Long-press BACK detection -> Return to Mouse Pointer Mode
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (backLongHandled && action == KeyEvent.ACTION_DOWN) {
                return true;
            }

            if ((event.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0) {
                if (!backLongHandled) {
                    backLongHandled = true;
                    keyHandler.removeCallbacks(longPressBackRunnable);
                    if (!isMouseMode) {
                        enterMouseMode();
                        cursorView.resetToCenter();
                    } else {
                        cursorView.resetToCenter();
                        showHud("🎯", "Cursor Centered");
                    }
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

        // 2. Long-press OK detection -> Radial Quick Menu
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
                    if (radialMenuLayout.getVisibility() != View.VISIBLE) {
                        keyHandler.postDelayed(longPressOkRunnable, 450);
                    }
                }
                if (radialMenuLayout.getVisibility() == View.VISIBLE) {
                    return super.dispatchKeyEvent(event);
                }
                return true;
            } else if (action == KeyEvent.ACTION_UP) {
                keyHandler.removeCallbacks(longPressOkRunnable);
                if (okLongHandled || (SystemClock.uptimeMillis() - menuOpenedTimestamp < 450)) {
                    okLongHandled = false;
                    return true;
                }
                // Regular short OK click
                if (radialMenuLayout.getVisibility() == View.VISIBLE) {
                    View focused = getCurrentFocus();
                    if (focused != null && focused.isClickable()) {
                        focused.performClick();
                        return true;
                    }
                    return super.dispatchKeyEvent(event);
                }
                if (isMouseMode && speedDialView.getVisibility() != View.VISIBLE) {
                    dispatchClickAtCursor();
                    return true;
                }
                if (!isMouseMode && speedDialView.getVisibility() != View.VISIBLE) {
                    if (isMovieOrVideoActive()) {
                        toggleVideoPlayPause();
                        return true;
                    } else {
                        return super.dispatchKeyEvent(event);
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

        // 3. Radial Menu Active Navigation
        if (radialMenuLayout.getVisibility() == View.VISIBLE) {
            return super.dispatchKeyEvent(event);
        }

        // 4. In-Video & Media Remote Mode or Webpage Arrow Mode (when user enabled D-Pad Mode)
        if (!isMouseMode && speedDialView.getVisibility() != View.VISIBLE) {
            if (isMovieOrVideoActive()) {
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
            } else {
                // Regular Webpage: D-Pad Arrow Navigation Mode!
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (action == KeyEvent.ACTION_DOWN) {
                        scrollPageWebpage(-240, event.getRepeatCount() > 0);
                    }
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (action == KeyEvent.ACTION_DOWN) {
                        scrollPageWebpage(240, event.getRepeatCount() > 0);
                    }
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return super.dispatchKeyEvent(event);
                }
            }
        }

        // 5. Virtual Cursor Movement & Gentle Edge Scrolling (Active by default in Mouse Mode)
        if (isMouseMode && speedDialView.getVisibility() != View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {

                if (action == KeyEvent.ACTION_DOWN) {
                    activeDpadCode = keyCode;
                    if (!isMovingCursor) {
                        isMovingCursor = true;
                        cursorHandler.post(cursorMoveRunnable);
                    }
                } else if (action == KeyEvent.ACTION_UP) {
                    isMovingCursor = false;
                    cursorHandler.removeCallbacks(cursorMoveRunnable);
                }
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private boolean handleShortBack() {
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

    private void openRadialMenu() {
        if (radialMenuLayout.getVisibility() == View.VISIBLE) return;

        menuOpenedTimestamp = SystemClock.uptimeMillis();

        menuBtnDpadMode.setText(isMouseMode ? "🎮 Turn ON D-Pad" : "🎯 Turn ON Mouse");
        textZoomScale.setText(currentZoom + "%");
        menuBtnDarkMode.setText(isDarkMode ? "☀️ Force Dark: ON" : "🌙 Force Dark: OFF");
        menuBtnUserAgent.setText(isMobileUa ? "📱 Mobile (Smooth)" : "💻 Desktop (Full)");

        speedDialView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        webView.setFocusable(false);

        radialMenuLayout.setVisibility(View.VISIBLE);
        menuBtnHome.requestFocus();
    }

    private void toggleRadialMenu() {
        if (radialMenuLayout.getVisibility() == View.VISIBLE) {
            hideRadialMenu();
        } else {
            openRadialMenu();
        }
    }

    private void hideRadialMenu() {
        radialMenuLayout.setVisibility(View.GONE);
        speedDialView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        webView.setFocusable(true);

        if (speedDialView.getVisibility() == View.VISIBLE) {
            findViewById(R.id.cardHdMovie2).requestFocus();
        } else if (isMouseMode && cursorView.isPointerVisible()) {
            cursorView.requestFocus();
        } else {
            webView.requestFocus();
        }
    }

    private void dispatchClickAtCursor() {
        float x = cursorView.getCursorX();
        float y = cursorView.getCursorY();
        long downTime = SystemClock.uptimeMillis();

        MotionEvent downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent upEvent = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, x, y, 0);

        webView.dispatchTouchEvent(downEvent);
        webView.dispatchTouchEvent(upEvent);

        downEvent.recycle();
        upEvent.recycle();
    }

    private boolean isVideoElementPlaying() {
        return isVideoActive;
    }

    private void adjustVolume(boolean up) {
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
        showHud("🔊", "Volume: " + percent + "%");
    }

    private void seekVideo(boolean forward) {
        int seconds = forward ? 10 : -10;
        String js = "(function() {\n" +
                "  if (window._cleanTvVideo && window._cleanTvVideo.seek) {\n" +
                "    return window._cleanTvVideo.seek(" + seconds + ");\n" +
                "  }\n" +
                "  try {\n" +
                "    var yt = document.getElementById('movie_player') || document.querySelector('.html5-video-player');\n" +
                "    if (yt && typeof yt.seekBy === 'function') { yt.seekBy(" + seconds + "); return 'yt'; }\n" +
                "  } catch(e) {}\n" +
                "  var v = document.querySelector('video');\n" +
                "  if (v) { v.currentTime = Math.max(0, Math.min(v.duration || 999999, v.currentTime + (" + seconds + "))); return 'html5'; }\n" +
                "  return 'none';\n" +
                "})();";
        webView.evaluateJavascript(js, null);
        showHud(forward ? "⏩" : "⏪", (forward ? "+10s" : "-10s") + " Seek");
    }

    private void toggleVideoPlayPause() {
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

    private void showHud(String icon, String text) {
        hudIcon.setText(icon);
        hudText.setText(text);
        hudOverlay.setVisibility(View.VISIBLE);

        hudHandler.removeCallbacks(hideHudRunnable);
        hudHandler.postDelayed(hideHudRunnable, 1400);
    }
}
