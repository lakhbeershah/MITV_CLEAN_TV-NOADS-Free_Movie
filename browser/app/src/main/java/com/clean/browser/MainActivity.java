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

    private View speedDialView;
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
    private TextView menuBtnZoomIn, menuBtnZoomOut, menuBtnDarkMode, menuBtnUserAgent, menuBtnReload, menuBtnHome;
    private TextView menuBtnFullscreen, menuBtnScrollDown;

    // State
    private int currentZoom = 125; // Default 125% for 10-foot TV viewing
    private boolean isDarkMode = false;
    private boolean isMobileUa = true; // Mobile UA default for smooth H.264 video decoding
    private boolean isVideoActive = false;

    // D-Pad Mouse movement handler
    private final Handler cursorHandler = new Handler(Looper.getMainLooper());
    private float cursorSpeed = 16f;
    private boolean isMovingCursor = false;
    private int activeDpadCode = 0;

    // Long press tracking via Handler
    private final Handler keyHandler = new Handler(Looper.getMainLooper());
    private boolean okLongHandled = false;
    private boolean backLongHandled = false;

    private final Runnable longPressOkRunnable = new Runnable() {
        @Override
        public void run() {
            okLongHandled = true;
            toggleRadialMenu();
        }
    };

    private final Runnable longPressBackRunnable = new Runnable() {
        @Override
        public void run() {
            backLongHandled = true;
            toggleCursorMode();
        }
    };

    private void toggleCursorMode() {
        cursorView.togglePointerVisible();
        if (cursorView.isPointerVisible()) {
            showHud("🎯", "Pointer Mode (Edge Scroll ON)");
        } else {
            showHud("🎮", "Video Mode (▲/▼ Vol, ◀/▶ Seek, OK Pause)");
        }
    }

    private void scrollPage(int deltaY) {
        webView.scrollBy(0, deltaY);
        webView.evaluateJavascript("window.scrollBy({top: " + deltaY + ", behavior: 'instant'});", null);
    }

    private final Runnable cursorMoveRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isMovingCursor || !cursorView.isPointerVisible()) return;

            float dx = 0, dy = 0;
            if (activeDpadCode == KeyEvent.KEYCODE_DPAD_UP) dy = -cursorSpeed;
            else if (activeDpadCode == KeyEvent.KEYCODE_DPAD_DOWN) dy = cursorSpeed;
            else if (activeDpadCode == KeyEvent.KEYCODE_DPAD_LEFT) dx = -cursorSpeed;
            else if (activeDpadCode == KeyEvent.KEYCODE_DPAD_RIGHT) dx = cursorSpeed;

            cursorView.moveBy(dx, dy);

            // Smooth Edge Scrolling
            float curY = cursorView.getCursorY();
            int viewH = cursorView.getHeight() > 0 ? cursorView.getHeight() : 720;
            if (dy > 0 && curY >= viewH - 80) {
                scrollPage(36);
            } else if (dy < 0 && curY <= 80) {
                scrollPage(-36);
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
        menuBtnZoomIn = findViewById(R.id.menuBtnZoomIn);
        menuBtnZoomOut = findViewById(R.id.menuBtnZoomOut);
        menuBtnDarkMode = findViewById(R.id.menuBtnDarkMode);
        menuBtnUserAgent = findViewById(R.id.menuBtnUserAgent);
        menuBtnReload = findViewById(R.id.menuBtnReload);
        menuBtnHome = findViewById(R.id.menuBtnHome);
        menuBtnFullscreen = findViewById(R.id.menuBtnFullscreen);
        menuBtnScrollDown = findViewById(R.id.menuBtnScrollDown);
    }

    private void setupWebView() {
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(currentZoom);
        s.setUserAgentString(isMobileUa ? UA_MOBILE : UA_DESKTOP);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

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

    private void setupSpeedDial() {
        editSearchUrl.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                handleSearchOrUrl(editSearchUrl.getText().toString());
                return true;
            }
            return false;
        });

        findViewById(R.id.btnGoSearch).setOnClickListener(v -> handleSearchOrUrl(editSearchUrl.getText().toString()));

        findViewById(R.id.cardHdMovie2).setOnClickListener(v -> loadUrl("https://www.google.com/search?q=hdmovie2"));
        findViewById(R.id.cardNetMirror).setOnClickListener(v -> loadUrl("https://netmirror.app"));
        findViewById(R.id.cardYouTube).setOnClickListener(v -> loadUrl("https://m.youtube.com"));
        findViewById(R.id.cardHotstar).setOnClickListener(v -> loadUrl("https://www.hotstar.com"));

        findViewById(R.id.cardGoogle).setOnClickListener(v -> loadUrl("https://www.google.com"));
        findViewById(R.id.cardWikipedia).setOnClickListener(v -> loadUrl("https://www.wikipedia.org"));
        findViewById(R.id.cardDuckDuckGo).setOnClickListener(v -> loadUrl("https://duckduckgo.com"));

        findViewById(R.id.cardClearCache).setOnClickListener(v -> {
            webView.clearCache(true);
            webView.clearHistory();
            System.gc();
            showHud("🧹", "Memory & Cache Cleaned!");
            Toast.makeText(this, "Browser Cache Cleared ✓", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupRadialMenu() {
        menuBtnZoomIn.setOnClickListener(v -> {
            currentZoom = Math.min(200, currentZoom + 25);
            webView.getSettings().setTextZoom(currentZoom);
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(KEY_ZOOM, currentZoom).apply();
            showHud("🔍", "Zoom: " + currentZoom + "%");
        });

        menuBtnZoomOut.setOnClickListener(v -> {
            currentZoom = Math.max(75, currentZoom - 25);
            webView.getSettings().setTextZoom(currentZoom);
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(KEY_ZOOM, currentZoom).apply();
            showHud("🔎", "Zoom: " + currentZoom + "%");
        });

        menuBtnDarkMode.setOnClickListener(v -> {
            isDarkMode = !isDarkMode;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_DARK, isDarkMode).apply();
            applyDarkMode();
            AdBlockEngine.applyForcedDarkMode(webView, isDarkMode);
            menuBtnDarkMode.setText(isDarkMode ? "☀️ Force Dark: ON" : "🌙 Force Dark: OFF");
            showHud(isDarkMode ? "🌙" : "☀️", isDarkMode ? "Dark Mode: ON" : "Dark Mode: OFF");
        });

        menuBtnUserAgent.setOnClickListener(v -> {
            isMobileUa = !isMobileUa;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_UA_MOBILE, isMobileUa).apply();
            webView.getSettings().setUserAgentString(isMobileUa ? UA_MOBILE : UA_DESKTOP);
            menuBtnUserAgent.setText(isMobileUa ? "📱 Mobile (Smooth)" : "💻 Desktop (Full)");
            showHud("🔄", isMobileUa ? "Mobile UA (Smooth Video)" : "Desktop UA");
            webView.reload();
        });

        menuBtnReload.setOnClickListener(v -> {
            hideRadialMenu();
            webView.reload();
            showHud("🔄", "Reloading...");
        });

        menuBtnHome.setOnClickListener(v -> {
            hideRadialMenu();
            openSpeedDial();
        });

        menuBtnFullscreen.setOnClickListener(v -> {
            hideRadialMenu();
            webView.evaluateJavascript("if (window._cleanTvVideo) window._cleanTvVideo.fullscreen();", null);
            showHud("📺", "Fullscreen Video Request");
        });

        menuBtnScrollDown.setOnClickListener(v -> {
            scrollPage(280);
            showHud("📜", "Scrolled Down");
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
        cursorView.setPointerVisible(true);
        webView.loadUrl(url);
    }

    private void openSpeedDial() {
        webView.setVisibility(View.GONE);
        if (customViewContainer != null && customViewContainer.getVisibility() == View.VISIBLE) {
            customViewContainer.setVisibility(View.GONE);
        }
        speedDialView.setVisibility(View.VISIBLE);
        cursorView.setPointerVisible(false);
        findViewById(R.id.searchBarContainer).requestFocus();
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

        // 1. Long-press BACK detection -> Toggle Mouse Cursor
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if ((event.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0) {
                backLongHandled = true;
                keyHandler.removeCallbacks(longPressBackRunnable);
                toggleCursorMode();
                return true;
            }
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    backLongHandled = false;
                    keyHandler.postDelayed(longPressBackRunnable, 600);
                }
                return true;
            } else if (action == KeyEvent.ACTION_UP) {
                keyHandler.removeCallbacks(longPressBackRunnable);
                if (backLongHandled) {
                    return true;
                }
                // Regular short BACK press
                return handleShortBack();
            }
            return super.dispatchKeyEvent(event);
        }

        // 2. Long-press OK detection -> Radial Quick Menu
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if ((event.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0) {
                okLongHandled = true;
                keyHandler.removeCallbacks(longPressOkRunnable);
                toggleRadialMenu();
                return true;
            }
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    okLongHandled = false;
                    keyHandler.postDelayed(longPressOkRunnable, 600);
                }
                return true;
            } else if (action == KeyEvent.ACTION_UP) {
                keyHandler.removeCallbacks(longPressOkRunnable);
                if (okLongHandled) {
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
                if (cursorView.isPointerVisible() && speedDialView.getVisibility() != View.VISIBLE) {
                    dispatchClickAtCursor();
                    return true;
                }
                if (!cursorView.isPointerVisible() && speedDialView.getVisibility() != View.VISIBLE) {
                    toggleVideoPlayPause();
                    return true;
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

        // 4. In-Video & Media Remote Control Mode:
        // Active when: in fullscreen video, or when cursor is toggled OFF while browsing a page
        boolean inVideoControls = isVideoActive || (!cursorView.isPointerVisible() && speedDialView.getVisibility() != View.VISIBLE);
        if (inVideoControls) {
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
        }

        // 5. Virtual Cursor Movement & Edge Scrolling via D-Pad
        if (cursorView.isPointerVisible() && speedDialView.getVisibility() != View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {

                if (action == KeyEvent.ACTION_DOWN) {
                    activeDpadCode = keyCode;
                    int viewH = cursorView.getHeight() > 0 ? cursorView.getHeight() : 720;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && cursorView.getCursorY() >= viewH - 90) {
                        scrollPage(60);
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && cursorView.getCursorY() <= 90) {
                        scrollPage(-60);
                    }
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

    private void toggleRadialMenu() {
        if (radialMenuLayout.getVisibility() == View.VISIBLE) {
            hideRadialMenu();
        } else {
            radialMenuLayout.setVisibility(View.VISIBLE);
            menuBtnZoomIn.requestFocus();
        }
    }

    private void hideRadialMenu() {
        radialMenuLayout.setVisibility(View.GONE);
        if (speedDialView.getVisibility() == View.VISIBLE) {
            findViewById(R.id.cardHdMovie2).requestFocus();
        } else if (cursorView.isPointerVisible()) {
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
