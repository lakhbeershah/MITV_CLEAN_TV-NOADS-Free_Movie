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
                "ad-maven.com", "clickadu.com", "richads.com", "evadav.com",
                // Movie streaming ad & popunder networks
                "popcash.net", "adcash.com", "exdynsrv.com", "tsyndicate.com",
                "alwingulla.com", "highcpmgate.com", "creativecdn.com", "delivery.ad",
                "onclickperformance.com", "in-page-push.com", "onclickgate.com",
                "whos.amung.us", "histats.com", "mobicow.com", "syndication.exdynsrv.com"
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
     * Injects JavaScript to kill in-video ads, skip video timers, neutralize popups,
     * and provide high-performance video seeking/pause APIs.
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
                "  window.open = function(url, target, features) {\n" +
                "    console.log('Blocked popup window: ' + url);\n" +
                "    return null;\n" +
                "  };\n" +
                "\n" +
                "  // 3. Force Hardware H.264 Video Decoding: Disable software-heavy AV1 and VP9 codecs\n" +
                "  try {\n" +
                "    var origIsTypeSupported = window.MediaSource ? window.MediaSource.isTypeSupported : null;\n" +
                "    if (window.MediaSource && origIsTypeSupported) {\n" +
                "      window.MediaSource.isTypeSupported = function(type) {\n" +
                "        if (type && (type.indexOf('av01') !== -1 || type.indexOf('av1') !== -1 || type.indexOf('vp09') !== -1 || type.indexOf('vp9') !== -1)) {\n" +
                "          return false;\n" +
                "        }\n" +
                "        return origIsTypeSupported.apply(this, arguments);\n" +
                "      };\n" +
                "    }\n" +
                "    var origCanPlay = HTMLMediaElement.prototype.canPlayType;\n" +
                "    HTMLMediaElement.prototype.canPlayType = function(type) {\n" +
                "      if (type && (type.indexOf('av01') !== -1 || type.indexOf('av1') !== -1 || type.indexOf('vp09') !== -1 || type.indexOf('vp9') !== -1)) {\n" +
                "        return '';\n" +
                "      }\n" +
                "      return origCanPlay.apply(this, arguments);\n" +
                "    };\n" +
                "  } catch(e) {}\n" +
                "\n" +
                "  // 4. Ultra-Safe Video Ad Skipper: Only triggers when player is actively showing an ad\n" +
                "  setInterval(function() {\n" +
                "    try {\n" +
                "      var adActive = document.querySelector('.ad-showing, .ad-interrupting');\n" +
                "      if (adActive) {\n" +
                "        var video = adActive.querySelector('video') || document.querySelector('video');\n" +
                "        if (video && isFinite(video.duration) && video.duration > 0) {\n" +
                "          video.muted = true;\n" +
                "          video.playbackRate = 16.0;\n" +
                "          video.currentTime = video.duration;\n" +
                "        }\n" +
                "        var skipBtns = document.querySelectorAll('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .videoAdUiSkipButton, [class*=\"skip-button\"], [class*=\"ad-skip\"], button[id*=\"skip\"], .ytp-skip-ad-button');\n" +
                "        for (var i = 0; i < skipBtns.length; i++) {\n" +
                "          try { skipBtns[i].click(); } catch(e) {}\n" +
                "        }\n" +
                "      }\n" +
                "      // Remove invisible clickjack overlays on movie streaming sites\n" +
                "      var overlays = document.querySelectorAll('div[style*=\"z-index: 2147483647\"], div[style*=\"z-index: 999999\"], a[target=\"_blank\"][style*=\"position: fixed\"]');\n" +
                "      for (var j = 0; j < overlays.length; j++) {\n" +
                "        if (!overlays[j].querySelector('video')) {\n" +
                "          overlays[j].remove();\n" +
                "        }\n" +
                "      }\n" +
                "    } catch(e) {}\n" +
                "  }, 1000);\n" +
                "\n" +
                "  // 4. Cosmetic element blocker\n" +
                "  var style = document.createElement('style');\n" +
                "  style.innerHTML = '.ad, .ads, .ad-banner, .popunder, [class*=\"sponsored\"], .ytp-ad-overlay-container, #player-ads { display: none !important; opacity: 0 !important; visibility: hidden !important; }';\n" +
                "  (document.head || document.documentElement).appendChild(style);\n" +
                "\n" +
                "  // 5. Fail-Safe TV Video Remote Controller API\n" +
                "  window._cleanTvVideo = {\n" +
                "    seek: function(seconds) {\n" +
                "      // A. YouTube movie_player native seek\n" +
                "      try {\n" +
                "        var yt = document.getElementById('movie_player') || document.querySelector('.html5-video-player');\n" +
                "        if (yt && typeof yt.seekBy === 'function') {\n" +
                "          yt.seekBy(seconds);\n" +
                "          return 'yt';\n" +
                "        }\n" +
                "      } catch(e) {}\n" +
                "      // B. Standard HTML5 Video element\n" +
                "      var v = document.querySelector('video');\n" +
                "      if (!v) {\n" +
                "        var iframes = document.querySelectorAll('iframe');\n" +
                "        for (var i = 0; i < iframes.length; i++) {\n" +
                "          try { var iv = iframes[i].contentDocument.querySelector('video'); if (iv) { v = iv; break; } } catch(e) {}\n" +
                "        }\n" +
                "      }\n" +
                "      if (v) {\n" +
                "        var target = Math.max(0, Math.min(v.duration || 999999, v.currentTime + seconds));\n" +
                "        v.currentTime = target;\n" +
                "        return 'html5';\n" +
                "      }\n" +
                "      // C. Keyboard fallback\n" +
                "      var code = seconds > 0 ? 39 : 37;\n" +
                "      var key = seconds > 0 ? 'ArrowRight' : 'ArrowLeft';\n" +
                "      var ev = new KeyboardEvent('keydown', {key: key, code: key, keyCode: code, which: code, bubbles: true});\n" +
                "      (document.activeElement || document.body).dispatchEvent(ev);\n" +
                "      return 'key';\n" +
                "    },\n" +
                "\n" +
                "    togglePlayPause: function() {\n" +
                "      // A. YouTube movie_player native play/pause\n" +
                "      try {\n" +
                "        var yt = document.getElementById('movie_player') || document.querySelector('.html5-video-player');\n" +
                "        if (yt && typeof yt.getPlayerState === 'function') {\n" +
                "          var state = yt.getPlayerState();\n" +
                "          if (state === 1) { yt.pauseVideo(); return 'pause'; }\n" +
                "          else { yt.playVideo(); return 'play'; }\n" +
                "        }\n" +
                "      } catch(e) {}\n" +
                "      // B. Play/Pause UI Button Click\n" +
                "      var playBtn = document.querySelector('.ytp-play-button, button[aria-label*=\"Play\" i], button[aria-label*=\"Pause\" i], .play-button');\n" +
                "      if (playBtn) {\n" +
                "        playBtn.click();\n" +
                "        return 'click';\n" +
                "      }\n" +
                "      // C. Standard HTML5 Video element\n" +
                "      var v = document.querySelector('video');\n" +
                "      if (!v) {\n" +
                "        var iframes = document.querySelectorAll('iframe');\n" +
                "        for (var i = 0; i < iframes.length; i++) {\n" +
                "          try { var iv = iframes[i].contentDocument.querySelector('video'); if (iv) { v = iv; break; } } catch(e) {}\n" +
                "        }\n" +
                "      }\n" +
                "      if (v) {\n" +
                "        if (v.paused) { v.play(); return 'play'; }\n" +
                "        else { v.pause(); return 'pause'; }\n" +
                "      }\n" +
                "      // D. Keyboard shortcut 'k'\n" +
                "      var ev = new KeyboardEvent('keydown', {key: 'k', code: 'KeyK', keyCode: 75, which: 75, bubbles: true});\n" +
                "      (document.activeElement || document.body).dispatchEvent(ev);\n" +
                "      return 'key';\n" +
                "    },\n" +
                "\n" +
                "    fullscreen: function() {\n" +
                "      var fsBtn = document.querySelector('.ytp-fullscreen-button, button[aria-label*=\"Full screen\" i], .fullscreen-button');\n" +
                "      if (fsBtn) { fsBtn.click(); return true; }\n" +
                "      var v = document.querySelector('video');\n" +
                "      if (v) {\n" +
                "        if (v.requestFullscreen) v.requestFullscreen();\n" +
                "        else if (v.webkitRequestFullscreen) v.webkitRequestFullscreen();\n" +
                "        return true;\n" +
                "      }\n" +
                "      return false;\n" +
                "    }\n" +
                "  };\n" +
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
