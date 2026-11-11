package com.clean.browser;

import android.net.Uri;
import android.util.Base64;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * High-performance Ad & Video Shield for Android TV WebView.
 */
public class AdBlockEngine {

    private static final Set<String> BLOCKED_DOMAINS = new HashSet<>();

    static {
        // Top Global Ad Networks & Trackers
        String[] domains = {
                "doubleclick.net", "googleadservices.com", "googlesyndication.com",
                "adnxs.com", "adroll.com", "popads.net", "propellerads.com",
                "adsterra.com", "outbrain.com", "taboola.com", "scorecardresearch.com",
                "zedo.com", "criteo.com", "exponential.com", "infolinks.com",
                "amazon-adsystem.com", "revcontent.com", "clicksor.com", "adpushup.com",
                "trafficjunky.com", "exoclick.com", "juicyads.com", "adcolony.com",
                "applovin.com", "chartboost.com", "unityads.unity3d.com", "vungle.com",
                "openx.net", "rubiconproject.com", "pubmatic.com", "yieldmo.com",
                "admob.com", "moatads.com", "adform.net", "casalemedia.com",
                "betweendigital.com", "smartadserver.com", "advertising.com",
                "adtrue.com", "monetag.com", "hilltopads.net", "yllix.com",
                "ad-maven.com", "clickadu.com", "richads.com", "evadav.com"
        };
        for (String d : domains) {
            BLOCKED_DOMAINS.add(d);
        }
    }

    public static boolean isAdUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase();

            for (String blocked : BLOCKED_DOMAINS) {
                if (host.equals(blocked) || host.endsWith("." + blocked)) {
                    return true;
                }
            }

            // Keyword pattern checks
            String path = uri.getPath();
            if (path != null && (path.contains("/ad/") || path.contains("/ads/") || path.contains("/popunder") || path.contains("/adbanner"))) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static WebResourceResponse createEmptyResponse() {
        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
    }

    /**
     * Injects JavaScript to kill in-video ads, skip video timers, and neutralize popups.
     */
    public static void injectVideoShield(WebView view) {
        String js = "(function() {\n" +
                "  if (window._cleanTvShieldInjected) return;\n" +
                "  window._cleanTvShieldInjected = true;\n" +
                "\n" +
                "  // 1. Anti-Anti-AdBlock Defuser\n" +
                "  window.canRunAds = true;\n" +
                "  window.isAdBlockActive = false;\n" +
                "  window.adsBlocked = false;\n" +
                "\n" +
                "  // 2. Block Popunder / New Tab spam without user gesture\n" +
                "  var originalOpen = window.open;\n" +
                "  window.open = function(url, target, features) {\n" +
                "    console.log('Blocked popup window: ' + url);\n" +
                "    return null;\n" +
                "  };\n" +
                "\n" +
                "  // 3. Fast Video Ad Skip Loop\n" +
                "  setInterval(function() {\n" +
                "    // YouTube & General Web Video Ad detect\n" +
                "    var adContainer = document.querySelector('.video-ads, .ytp-ad-module, .ad-container, [class*=\"ad-showing\"]');\n" +
                "    var video = document.querySelector('video');\n" +
                "    if (video) {\n" +
                "      if (adContainer && adContainer.children.length > 0) {\n" +
                "        video.muted = true;\n" +
                "        video.playbackRate = 16.0;\n" +
                "        if (isFinite(video.duration) && video.duration > 0) {\n" +
                "          video.currentTime = video.duration;\n" +
                "        }\n" +
                "      }\n" +
                "      // Click any skip button immediately\n" +
                "      var skipBtns = document.querySelectorAll('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .videoAdUiSkipButton, [class*=\"skip-button\"], [class*=\"ad-skip\"], button[id*=\"skip\"]');\n" +
                "      for (var i = 0; i < skipBtns.length; i++) {\n" +
                "        skipBtns[i].click();\n" +
                "      }\n" +
                "    }\n" +
                "  }, 250);\n" +
                "\n" +
                "  // 4. Cosmetic element blocker\n" +
                "  var style = document.createElement('style');\n" +
                "  style.innerHTML = '.ad, .ads, .ad-banner, .popunder, [class*=\"sponsored\"], .ytp-ad-overlay-container, #player-ads { display: none !important; opacity: 0 !important; visibility: hidden !important; }';\n" +
                "  (document.head || document.documentElement).appendChild(style);\n" +
                "})();";

        view.evaluateJavascript(js, null);
    }

    /**
     * Injects forced dark mode CSS.
     */
    public static void applyForcedDarkMode(WebView view, boolean enable) {
        if (enable) {
            String darkJs = "(function() {\n" +
                    "  var id = 'clean_tv_dark_style';\n" +
                    "  var el = document.getElementById(id);\n" +
                    "  if (!el) {\n" +
                    "    el = document.createElement('style');\n" +
                    "    el.id = id;\n" +
                    "    el.innerHTML = 'html { filter: invert(90%) hue-rotate(180deg) !important; background: #121212 !important; } img, video, canvas, svg { filter: invert(100%) hue-rotate(180deg) !important; }';\n" +
                    "    (document.head || document.documentElement).appendChild(el);\n" +
                    "  }\n" +
                    "})();";
            view.evaluateJavascript(darkJs, null);
        } else {
            String removeDarkJs = "(function() {\n" +
                    "  var el = document.getElementById('clean_tv_dark_style');\n" +
                    "  if (el) el.remove();\n" +
                    "})();";
            view.evaluateJavascript(removeDarkJs, null);
        }
    }
}
