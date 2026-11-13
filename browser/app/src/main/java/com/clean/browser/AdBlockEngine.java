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
        // Fast-path zero-overhead bypass for video streams, DASH/HLS segments and audio chunks
        if (url.contains("videoplayback") || url.contains(".m4s") || url.contains(".ts")
                || url.contains(".m3u8") || url.contains(".mpd") || url.contains(".mp4")
                || url.contains(".webm") || url.contains(".mp3") || url.contains(".m4a")
                || url.contains(".aac") || url.contains(".ogg") || url.contains(".opus")
                || url.contains("range=") || url.contains("mime=video") || url.contains("mime=audio")
                || url.contains("audio/") || url.contains("audio=")) {
            return false;
        }
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
                "  var curHref = location.href;\n" +
                "  if (window._cleanTvShieldInjected) {\n" +
                "    if (window._cleanTvShieldUrl !== curHref) {\n" +
                "      window._cleanTvShieldUrl = curHref;\n" +
                "      if (typeof window._cleanTvReportMedia === 'function') window._cleanTvReportMedia();\n" +
                "    }\n" +
                "    return;\n" +
                "  }\n" +
                "  if (!document.head && !document.body && !document.documentElement) return;\n" +
                "  window._cleanTvShieldUrl = curHref;\n" +
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
                "  try {\n" +
                "    var adTarget = document.head || document.body || document.documentElement;\n" +
                "    if (adTarget && !document.getElementById('clean_tv_ad_css')) {\n" +
                "      var style = document.createElement('style');\n" +
                "      style.id = 'clean_tv_ad_css';\n" +
                "      style.innerHTML = '.ad, .ads, .ad-banner, .popunder, [class*=\"sponsored\"], .ytp-ad-overlay-container, #player-ads { display: none !important; opacity: 0 !important; visibility: hidden !important; }';\n" +
                "      adTarget.appendChild(style);\n" +
                "    }\n" +
                "  } catch(e) {}\n" +
                "\n" +
                "  // 5. Fail-Safe TV Video Remote Controller API\n" +
                "  window._cleanTvVideo = {\n" +
                "    seek: function(seconds) {\n" +
                "      // A. YouTube movie_player native seek & mobile seekTo\n" +
                "      try {\n" +
                "        var yt = document.getElementById('movie_player') || document.querySelector('.html5-video-player');\n" +
                "        if (yt) {\n" +
                "          if (typeof yt.seekBy === 'function') {\n" +
                "            yt.seekBy(seconds);\n" +
                "            return 'yt';\n" +
                "          }\n" +
                "          if (typeof yt.seekTo === 'function') {\n" +
                "            var ct = typeof yt.getCurrentTime === 'function' ? yt.getCurrentTime() : 0;\n" +
                "            yt.seekTo(ct + seconds, true);\n" +
                "            return 'yt-seekto';\n" +
                "          }\n" +
                "        }\n" +
                "      } catch(e) {}\n" +
                "      // B. Standard HTML5 Video element with instant keyframe fastSeek & preloading\n" +
                "      var v = document.querySelector('video');\n" +
                "      if (!v) {\n" +
                "        var iframes = document.querySelectorAll('iframe');\n" +
                "        for (var i = 0; i < iframes.length; i++) {\n" +
                "          try { var iv = iframes[i].contentDocument.querySelector('video'); if (iv) { v = iv; break; } } catch(e) {}\n" +
                "        }\n" +
                "      }\n" +
                "      if (v) {\n" +
                "        var target = Math.max(0, Math.min(v.duration || 999999, v.currentTime + seconds));\n" +
                "        if (typeof v.fastSeek === 'function') {\n" +
                "          try { v.fastSeek(target); } catch(e) { v.currentTime = target; }\n" +
                "        } else {\n" +
                "          v.currentTime = target;\n" +
                "        }\n" +
                "        if (v.preload !== 'auto') { try { v.preload = 'auto'; } catch(e) {} }\n" +
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
                "\n" +
                "  // 6. Real-time media playback detector\n" +
                "  function reportMedia() {\n" +
                "    if (location.href.indexOf('file://') !== -1 || location.href.indexOf('stream_hub.html') !== -1) {\n" +
                "      if (window.CleanTvBridge) window.CleanTvBridge.setVideoPlaying(false);\n" +
                "      return;\n" +
                "    }\n" +
                "    var active = !!document.fullscreenElement;\n" +
                "    try {\n" +
                "      var yt = document.getElementById('movie_player') || document.querySelector('.html5-video-player');\n" +
                "      if (yt && typeof yt.getPlayerState === 'function') {\n" +
                "        var s = yt.getPlayerState();\n" +
                "        if (s === 1 || s === 3) active = true;\n" +
                "      }\n" +
                "    } catch(e) {}\n" +
                "    if (!active) {\n" +
                "      var mediaEls = document.querySelectorAll('video, audio');\n" +
                "      for (var i = 0; i < mediaEls.length; i++) {\n" +
                "        var m = mediaEls[i];\n" +
                "        if (!m.paused && (m.currentTime > 0 || m.readyState >= 1) && !m.ended) { active = true; break; }\n" +
                "      }\n" +
                "    }\n" +
                "    if (!active && window._cleanTvSubframeMediaPlaying) {\n" +
                "      active = true;\n" +
                "    }\n" +
                "    if (window.CleanTvBridge && typeof window.CleanTvBridge.setVideoPlaying === 'function') {\n" +
                "      window.CleanTvBridge.setVideoPlaying(active);\n" +
                "    }\n" +
                "  }\n" +
                "  window._cleanTvReportMedia = reportMedia;\n" +
                "  reportMedia();\n" +
                "  setInterval(reportMedia, 1000);\n" +
                "  document.addEventListener('play', function() { if (window.CleanTvBridge && location.href.indexOf('stream_hub.html') === -1) window.CleanTvBridge.setVideoPlaying(true); }, true);\n" +
                "  document.addEventListener('playing', function() { if (window.CleanTvBridge && location.href.indexOf('stream_hub.html') === -1) window.CleanTvBridge.setVideoPlaying(true); }, true);\n" +
                "  document.addEventListener('pause', function() { setTimeout(reportMedia, 400); }, true);\n" +
                "  document.addEventListener('ended', function() { setTimeout(reportMedia, 400); }, true);\n" +
                "  window.addEventListener('message', function(e) {\n" +
                "    if (e && e.data && e.data.type === 'cleanTvMediaState') {\n" +
                "      window._cleanTvSubframeMediaPlaying = !!e.data.playing;\n" +
                "      reportMedia();\n" +
                "    }\n" +
                "  }, false);\n" +
                "\n" +
                "  window._cleanTvShieldInjected = true;\n" +
                "})();\n" +
                getSubframeNavScript();

        view.evaluateJavascript(js, null);
    }

    /**
     * Universal 2D Spatial Navigator, Focus Ring CSS & Fullscreen/Iframe Control Bridge.
     * Injected into every frame (including cross-origin iframes) via DOCUMENT_START_SCRIPT.
     */
    public static String getSubframeNavScript() {
        return "(function() {\n" +
                "  if (window._cleanTvNavInjected) return;\n" +
                "  window._cleanTvNavInjected = true;\n" +
                "  if (window._cleanTvDpadMode === undefined) window._cleanTvDpadMode = false;\n" +
                "  window._cleanTvFocusEl = null;\n" +
                "  window._cleanTvWakeTick = false;\n" +
                "\n" +
                "  // 1. Background Audio/Video Playback: Prevent pause on HOME or minimize\n" +
                "  try {\n" +
                "    Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });\n" +
                "    Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });\n" +
                "    Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });\n" +
                "    document.hasFocus = function() { return true; };\n" +
                "    var stopVis = function(e) { e.stopImmediatePropagation(); };\n" +
                "    window.addEventListener('visibilitychange', stopVis, true);\n" +
                "    document.addEventListener('visibilitychange', stopVis, true);\n" +
                "    window.addEventListener('webkitvisibilitychange', stopVis, true);\n" +
                "    document.addEventListener('webkitvisibilitychange', stopVis, true);\n" +
                "    window.addEventListener('blur', stopVis, true);\n" +
                "    window.addEventListener('focusout', stopVis, true);\n" +
                "    window.addEventListener('pagehide', stopVis, true);\n" +
                "  } catch(e) {}\n" +
                "\n" +
                "  // 1b. Web Audio Background Clock & Timer Throttle Exemption\n" +
                "  try {\n" +
                "    var AudioCtx = window.AudioContext || window.webkitAudioContext;\n" +
                "    if (AudioCtx) {\n" +
                "      var bgAudioCtx = null;\n" +
                "      var ensureAudioClock = function() {\n" +
                "        try {\n" +
                "          if (!bgAudioCtx) {\n" +
                "            bgAudioCtx = new AudioCtx();\n" +
                "            var silentBuf = bgAudioCtx.createBuffer(1, 2205, 22050);\n" +
                "            var src = bgAudioCtx.createBufferSource();\n" +
                "            src.buffer = silentBuf;\n" +
                "            src.loop = true;\n" +
                "            var gain = bgAudioCtx.createGain();\n" +
                "            gain.gain.value = 0.00001;\n" +
                "            src.connect(gain);\n" +
                "            gain.connect(bgAudioCtx.destination);\n" +
                "            src.start(0);\n" +
                "          }\n" +
                "          if (bgAudioCtx.state === 'suspended') {\n" +
                "            bgAudioCtx.resume();\n" +
                "          }\n" +
                "        } catch(e) {}\n" +
                "      };\n" +
                "      document.addEventListener('play', ensureAudioClock, true);\n" +
                "      document.addEventListener('playing', ensureAudioClock, true);\n" +
                "      document.addEventListener('pointerdown', ensureAudioClock, true);\n" +
                "      document.addEventListener('keydown', ensureAudioClock, true);\n" +
                "    }\n" +
                "  } catch(e) {}\n" +
                "\n" +
                "  // 1c. Subframe Media State Forwarder to Top Frame\n" +
                "  if (window.top !== window) {\n" +
                "    var postMedia = function(playing) {\n" +
                "      try { window.top.postMessage({ type: 'cleanTvMediaState', playing: playing }, '*'); } catch(e) {}\n" +
                "    };\n" +
                "    document.addEventListener('play', function() { postMedia(true); }, true);\n" +
                "    document.addEventListener('playing', function() { postMedia(true); }, true);\n" +
                "    document.addEventListener('pause', function() { postMedia(false); }, true);\n" +
                "    document.addEventListener('ended', function() { postMedia(false); }, true);\n" +
                "  }\n" +
                "\n" +
                "  // 2. Hardware 1080p H.264 Codec Preference: Fixes green/pink line VP9/AV1 stride bug\n" +
                "  try {\n" +
                "    if (window.MediaSource && typeof window.MediaSource.isTypeSupported === 'function') {\n" +
                "      var origIsType = window.MediaSource.isTypeSupported;\n" +
                "      window.MediaSource.isTypeSupported = function(type) {\n" +
                "        if (type) {\n" +
                "          var lt = type.toLowerCase();\n" +
                "          if (lt.indexOf('vp9') !== -1 || lt.indexOf('vp09') !== -1 || lt.indexOf('av01') !== -1 || lt.indexOf('av1') !== -1) {\n" +
                "            return false;\n" +
                "          }\n" +
                "        }\n" +
                "        return origIsType.call(window.MediaSource, type);\n" +
                "      };\n" +
                "    }\n" +
                "    if (window.HTMLMediaElement && window.HTMLMediaElement.prototype && typeof window.HTMLMediaElement.prototype.canPlayType === 'function') {\n" +
                "      var origCanPlay = window.HTMLMediaElement.prototype.canPlayType;\n" +
                "      window.HTMLMediaElement.prototype.canPlayType = function(type) {\n" +
                "        if (type) {\n" +
                "          var lt = type.toLowerCase();\n" +
                "          if (lt.indexOf('vp9') !== -1 || lt.indexOf('vp09') !== -1 || lt.indexOf('av01') !== -1 || lt.indexOf('av1') !== -1) {\n" +
                "            return '';\n" +
                "          }\n" +
                "        }\n" +
                "        return origCanPlay.call(this, type);\n" +
                "      };\n" +
                "    }\n" +
                "  } catch(e) {}\n" +
                "\n" +
                "  function ensureFocusCss() {\n" +
                "    try {\n" +
                "      var focusTarget = document.head || document.body || document.documentElement;\n" +
                "      if (focusTarget && !document.getElementById('clean_tv_focus_css')) {\n" +
                "        var fStyle = document.createElement('style');\n" +
                "        fStyle.id = 'clean_tv_focus_css';\n" +
                "        fStyle.innerHTML = '[data-clean-focused=\"1\"] { outline: 3px solid #00E5FF !important; outline-offset: -2px !important; box-shadow: inset 0 0 0 2px #00E5FF, 0 0 14px rgba(0, 229, 255, 0.95) !important; } ' +\n" +
                "          'body.clean-tv-dpad video::-webkit-media-controls-panel { opacity: 1 !important; pointer-events: auto !important; } ' +\n" +
                "          'video { object-fit: contain !important; overflow: hidden !important; }';\n" +
                "        focusTarget.appendChild(fStyle);\n" +
                "      }\n" +
                "    } catch(e) {}\n" +
                "  }\n" +
                "  ensureFocusCss();\n" +
                "  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ensureFocusCss);\n" +
                "\n" +
                "  function emitFocusRectArr(arr) {\n" +
                "    try {\n" +
                "      if (window !== window.top) {\n" +
                "        window.parent.postMessage({ type: 'cleanTvFocusRect', rect: arr }, '*');\n" +
                "      } else if (window.CleanTvBridge && typeof window.CleanTvBridge.setFocusRect === 'function') {\n" +
                "        if (arr && arr.length === 4) window.CleanTvBridge.setFocusRect(arr[0], arr[1], arr[2], arr[3]);\n" +
                "        else window.CleanTvBridge.setFocusRect(-1, -1, 0, 0);\n" +
                "      }\n" +
                "    } catch(e) {}\n" +
                "  }\n" +
                "\n" +
                "  function reportFocusRect(el) {\n" +
                "    if (!el || !el.isConnected) { emitFocusRectArr(null); return; }\n" +
                "    var r = el.getBoundingClientRect();\n" +
                "    var vw = window.innerWidth || 1280;\n" +
                "    var vh = window.innerHeight || 720;\n" +
                "    if (r.width < 4 || r.height < 4 || r.bottom <= 0 || r.right <= 0 || r.top >= vh || r.left >= vw) {\n" +
                "      emitFocusRectArr(null); return;\n" +
                "    }\n" +
                "    emitFocusRectArr([\n" +
                "      Math.max(0, r.left / vw),\n" +
                "      Math.max(0, r.top / vh),\n" +
                "      Math.min(1, r.width / vw),\n" +
                "      Math.min(1, r.height / vh)\n" +
                "    ]);\n" +
                "  }\n" +
                "\n" +
                "  function clearFocusAttr() {\n" +
                "    try {\n" +
                "      var prev = document.querySelectorAll('[data-clean-focused]');\n" +
                "      for (var i = 0; i < prev.length; i++) prev[i].removeAttribute('data-clean-focused');\n" +
                "    } catch(e) {}\n" +
                "  }\n" +
                "\n" +
                "  function applyFocus(el, shouldScroll) {\n" +
                "    if (!el) return;\n" +
                "    clearFocusAttr();\n" +
                "    window._cleanTvEnsureFocusable(el);\n" +
                "    el.setAttribute('data-clean-focused', '1');\n" +
                "    window._cleanTvFocusEl = el;\n" +
                "    try { el.focus({ preventScroll: true }); } catch(e) {}\n" +
                "    if (shouldScroll) {\n" +
                "      try { el.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'center' }); } catch(e) {}\n" +
                "    }\n" +
                "    reportFocusRect(el);\n" +
                "    setTimeout(function() { if (window._cleanTvDpadMode && window._cleanTvFocusEl === el) reportFocusRect(el); }, 80);\n" +
                "  }\n" +
                "\n" +
                "  window._cleanTvSetDpadFlag = function(on) {\n" +
                "    window._cleanTvDpadMode = !!on;\n" +
                "    ensureFocusCss();\n" +
                "    if (document.body) {\n" +
                "      if (on) document.body.classList.add('clean-tv-dpad');\n" +
                "      else document.body.classList.remove('clean-tv-dpad');\n" +
                "    }\n" +
                "    if (!on) {\n" +
                "      clearFocusAttr();\n" +
                "      window._cleanTvFocusEl = null;\n" +
                "      emitFocusRectArr(null);\n" +
                "    }\n" +
                "    try {\n" +
                "      var iframes = document.querySelectorAll('iframe');\n" +
                "      for (var i = 0; i < iframes.length; i++) {\n" +
                "        if (iframes[i].contentWindow) iframes[i].contentWindow.postMessage({ type: 'cleanTvCmd', cmd: 'flag', on: !!on }, '*');\n" +
                "      }\n" +
                "    } catch(e) {}\n" +
                "  };\n" +
                "\n" +
                "  window._cleanTvWakeControls = function() {\n" +
                "    try {\n" +
                "      window._cleanTvWakeTick = !window._cleanTvWakeTick;\n" +
                "      var bump = window._cleanTvWakeTick ? 4 : -4;\n" +
                "      var fsEl = document.fullscreenElement || document.webkitFullscreenElement || null;\n" +
                "      var targets = document.querySelectorAll('.vjs-control-bar, .video-js, .plyr, .jwplayer, .html5-video-player, ytm-custom-control, #player-control-overlay, video');\n" +
                "      var list = fsEl ? [fsEl] : [];\n" +
                "      for (var i = 0; i < targets.length; i++) list.push(targets[i]);\n" +
                "      if (list.length === 0 && document.body) list.push(document.body);\n" +
                "      for (var j = 0; j < list.length; j++) {\n" +
                "        var tgt = list[j];\n" +
                "        if (!tgt) continue;\n" +
                "        var r = tgt.getBoundingClientRect ? tgt.getBoundingClientRect() : { left: 100, top: 100, width: 400, height: 300 };\n" +
                "        var px = r.left + r.width * 0.5 + bump;\n" +
                "        var py = r.top + r.height * 0.7 + bump;\n" +
                "        tgt.dispatchEvent(new MouseEvent('mousemove', { bubbles: true, cancelable: true, clientX: px, clientY: py }));\n" +
                "        tgt.dispatchEvent(new MouseEvent('mouseover', { bubbles: true, cancelable: true, clientX: px, clientY: py }));\n" +
                "        if (typeof PointerEvent === 'function') {\n" +
                "          tgt.dispatchEvent(new PointerEvent('pointermove', { bubbles: true, cancelable: true, pointerType: 'mouse', clientX: px, clientY: py }));\n" +
                "        }\n" +
                "        if (tgt.classList) {\n" +
                "          tgt.classList.remove('vjs-user-inactive', 'plyr--hide-controls', 'jw-flag-user-inactive', 'ytp-autohide');\n" +
                "          if (tgt.classList.contains('video-js')) tgt.classList.add('vjs-user-active');\n" +
                "        }\n" +
                "      }\n" +
                "    } catch(e) {}\n" +
                "  };\n" +
                "\n" +
                "  var BTN_SELECTORS = '.site-card, a[href], button, [role=\"button\"], [role=\"slider\"], [role=\"menuitem\"], ' +\n" +
                "    'input:not([type=\"hidden\"]), select, textarea, summary, [tabindex]:not([tabindex=\"-1\"]), [onclick], ' +\n" +
                "    '.vjs-control, .vjs-big-play-button, .plyr__control, .jw-icon, .ytp-button, ' +\n" +
                "    'button[class*=\"play\"], [role=\"button\"][class*=\"play\"], [class*=\"play-btn\"], [class*=\"play-button\"], [class*=\"playBtn\"], [class*=\"playButton\"], [class*=\"btn-play\"], [class*=\"icon-play\"], ' +\n" +
                "    '[aria-label*=\"Play\"], [aria-label*=\"play\"], [aria-label*=\"Pause\"], [aria-label*=\"pause\"], ' +\n" +
                "    '[aria-label*=\"Fullscreen\"], [aria-label*=\"fullscreen\"], [aria-label*=\"Exit\"], [aria-label*=\"exit\"], [aria-label*=\"Mute\"], [aria-label*=\"mute\"]';\n" +
                "\n" +
                "  window._cleanTvEnsureFocusable = function(el) {\n" +
                "    if (!el) return;\n" +
                "    var tag = el.tagName;\n" +
                "    if (tag !== 'A' && tag !== 'BUTTON' && tag !== 'INPUT' && tag !== 'SELECT' && tag !== 'TEXTAREA' && tag !== 'SUMMARY' && !el.hasAttribute('tabindex')) {\n" +
                "      el.setAttribute('tabindex', '-1');\n" +
                "    }\n" +
                "  };\n" +
                "\n" +
                "  function getFullscreenRoot() {\n" +
                "    return document.fullscreenElement || document.webkitFullscreenElement || null;\n" +
                "  }\n" +
                "\n" +
                "  function collectCandidates(fsEl, vw, vh) {\n" +
                "    var root = fsEl || document;\n" +
                "    var nodes = root.querySelectorAll(BTN_SELECTORS);\n" +
                "    var out = [];\n" +
                "    for (var i = 0; i < nodes.length; i++) {\n" +
                "      var el = nodes[i];\n" +
                "      if (fsEl && el === fsEl) continue;\n" +
                "      if (el.disabled || el.getAttribute('aria-hidden') === 'true') continue;\n" +
                "      var r = el.getBoundingClientRect();\n" +
                "      if (r.width < 14 || r.height < 14) continue;\n" +
                "      if (fsEl) {\n" +
                "        if (r.right <= 2 || r.bottom <= 2 || r.left >= vw - 2 || r.top >= vh - 2) continue;\n" +
                "      } else {\n" +
                "        if (r.bottom < -300 || r.top > vh + 300 || r.right < -300 || r.left > vw + 300) continue;\n" +
                "      }\n" +
                "      if (!fsEl && r.left <= 2 && r.top <= 2 && (r.width < 28 || r.height < 28)) continue;\n" +
                "      var cs = window.getComputedStyle(el);\n" +
                "      if (cs.visibility === 'hidden' || cs.display === 'none' || cs.pointerEvents === 'none') continue;\n" +
                "      if (cs.clip === 'rect(0px, 0px, 0px, 0px)' || cs.clipPath === 'inset(50%)') continue;\n" +
                "      if (parseFloat(cs.opacity) < 0.05) continue;\n" +
                "      var ancHidden = false, anc = el.parentElement, hop = 0;\n" +
                "      while (anc && anc !== document.body && anc !== document.documentElement && hop < 6) {\n" +
                "        var acs = window.getComputedStyle(anc);\n" +
                "        if (acs.display === 'none' || acs.visibility === 'hidden' || parseFloat(acs.opacity) < 0.05) { ancHidden = true; break; }\n" +
                "        anc = anc.parentElement; hop++;\n" +
                "      }\n" +
                "      if (ancHidden) continue;\n" +
                "      if (r.width > vw * 0.55 && r.height > vh * 0.45 && el.tagName !== 'IFRAME' && el.tagName !== 'VIDEO') {\n" +
                "        if (el.querySelector('button, [role=\"button\"], a[href], .vjs-control, .plyr__control, .jw-icon')) continue;\n" +
                "      }\n" +
                "      if (el.id === 'searchInput') continue;\n" +
                "      out.push({ el: el, r: r, cx: r.left + r.width / 2, cy: r.top + r.height / 2 });\n" +
                "    }\n" +
                "    if (out.length === 0 && !fsEl) {\n" +
                "      var mediaNodes = document.querySelectorAll('video, iframe');\n" +
                "      for (var m = 0; m < mediaNodes.length; m++) {\n" +
                "        var mel = mediaNodes[m];\n" +
                "        var mr = mel.getBoundingClientRect();\n" +
                "        if (mr.width < 60 || mr.height < 40 || mr.bottom < -100 || mr.top > vh + 100) continue;\n" +
                "        var mcs = window.getComputedStyle(mel);\n" +
                "        if (mcs.visibility === 'hidden' || mcs.display === 'none') continue;\n" +
                "        out.push({ el: mel, r: mr, cx: mr.left + mr.width / 2, cy: mr.top + mr.height / 2 });\n" +
                "      }\n" +
                "    }\n" +
                "    return out;\n" +
                "  }\n" +
                "\n" +
                "  function isPlayControl(el) {\n" +
                "    var aria = el.getAttribute('aria-label') || '';\n" +
                "    var title = el.getAttribute('title') || '';\n" +
                "    var cls = (typeof el.className === 'string' ? el.className : '').toLowerCase();\n" +
                "    return /\\b(play|pause)\\b/i.test(aria) || /\\b(play|pause)\\b/i.test(title) ||\n" +
                "      cls.indexOf('play-control') !== -1 || cls.indexOf('big-play') !== -1 || cls.indexOf('ytp-play') !== -1 || cls.indexOf('plyr__control--overlaid') !== -1 ||\n" +
                "      cls.indexOf('icon-play') !== -1 || cls.indexOf('play-btn') !== -1 || cls.indexOf('play-button') !== -1;\n" +
                "  }\n" +
                "\n" +
                "  function pickInitialTarget(candidates, fsEl, vw, vh) {\n" +
                "    if (!candidates || candidates.length === 0) return null;\n" +
                "    if (fsEl) {\n" +
                "      for (var i = 0; i < candidates.length; i++) {\n" +
                "        if (isPlayControl(candidates[i].el)) return candidates[i].el;\n" +
                "      }\n" +
                "      var bestCenter = null, bestDist = Infinity;\n" +
                "      for (var j = 0; j < candidates.length; j++) {\n" +
                "        var c = candidates[j];\n" +
                "        var d = Math.abs(c.cx - vw * 0.5) + Math.abs(c.cy - vh * 0.5);\n" +
                "        if (d < bestDist) { bestDist = d; bestCenter = c.el; }\n" +
                "      }\n" +
                "      return bestCenter;\n" +
                "    }\n" +
                "    var homeCard = document.querySelector('.site-card.focusable');\n" +
                "    if (homeCard) return homeCard;\n" +
                "    for (var p = 0; p < candidates.length; p++) {\n" +
                "      var cp = candidates[p];\n" +
                "      if (cp.r.top >= 0 && cp.r.bottom <= vh && isPlayControl(cp.el)) return cp.el;\n" +
                "    }\n" +
                "    var bestInit = null, bestScore = Infinity;\n" +
                "    for (var k = 0; k < candidates.length; k++) {\n" +
                "      var c0 = candidates[k];\n" +
                "      if (c0.r.top >= 0 && c0.r.bottom <= vh && c0.r.left >= 0 && c0.r.right <= vw) {\n" +
                "        if (c0.r.left <= 2 && c0.r.top <= 2 && c0.r.width < 48) continue;\n" +
                "        var sc = c0.r.top * 2 + c0.r.left;\n" +
                "        if (sc < bestScore) { bestScore = sc; bestInit = c0.el; }\n" +
                "      }\n" +
                "    }\n" +
                "    return bestInit || candidates[0].el;\n" +
                "  }\n" +
                "\n" +
                "  function wakeMobileOverlayIfHidden(fsEl) {\n" +
                "    if (!fsEl) return false;\n" +
                "    var ytBg = fsEl.querySelector('#player-control-overlay, ytm-custom-control, .player-controls-background-container, .player-controls-background');\n" +
                "    if (ytBg) {\n" +
                "      try { ytBg.click(); } catch(e) {}\n" +
                "      return true;\n" +
                "    }\n" +
                "    return false;\n" +
                "  }\n" +
                "\n" +
                "  window._cleanTvInitFocus = function() {\n" +
                "    ensureFocusCss();\n" +
                "    window._cleanTvWakeControls();\n" +
                "    var fsEl = getFullscreenRoot();\n" +
                "    if (fsEl && fsEl.tagName === 'IFRAME' && fsEl.contentWindow) {\n" +
                "      fsEl.contentWindow.postMessage({ type: 'cleanTvCmd', cmd: 'init' }, '*');\n" +
                "      return 'postmsg';\n" +
                "    }\n" +
                "    var vw = window.innerWidth || 1280;\n" +
                "    var vh = window.innerHeight || 720;\n" +
                "    var candidates = collectCandidates(fsEl, vw, vh);\n" +
                "    if (fsEl && candidates.length === 0) {\n" +
                "      if (wakeMobileOverlayIfHidden(fsEl)) {\n" +
                "        setTimeout(function() {\n" +
                "          var c2 = collectCandidates(fsEl, window.innerWidth || 1280, window.innerHeight || 720);\n" +
                "          var t2 = pickInitialTarget(c2, fsEl, window.innerWidth || 1280, window.innerHeight || 720);\n" +
                "          if (t2) applyFocus(t2, false);\n" +
                "        }, 80);\n" +
                "        return 'waking';\n" +
                "      }\n" +
                "      return 'cross-fs';\n" +
                "    }\n" +
                "    var target = pickInitialTarget(candidates, fsEl, vw, vh);\n" +
                "    if (target) { applyFocus(target, false); return true; }\n" +
                "    return fsEl ? 'cross-fs' : false;\n" +
                "  };\n" +
                "\n" +
                "  window._cleanTvStepFocus = function(dir) {\n" +
                "    ensureFocusCss();\n" +
                "    window._cleanTvWakeControls();\n" +
                "    var fsEl = getFullscreenRoot();\n" +
                "    if (fsEl && fsEl.tagName === 'IFRAME' && fsEl.contentWindow) {\n" +
                "      fsEl.contentWindow.postMessage({ type: 'cleanTvCmd', cmd: 'step', dir: dir }, '*');\n" +
                "      return 'postmsg';\n" +
                "    }\n" +
                "    var vw = window.innerWidth || 1280;\n" +
                "    var vh = window.innerHeight || 720;\n" +
                "    var candidates = collectCandidates(fsEl, vw, vh);\n" +
                "    if (fsEl && candidates.length === 0) {\n" +
                "      if (wakeMobileOverlayIfHidden(fsEl)) {\n" +
                "        setTimeout(function() {\n" +
                "          var c2 = collectCandidates(fsEl, window.innerWidth || 1280, window.innerHeight || 720);\n" +
                "          var t2 = pickInitialTarget(c2, fsEl, window.innerWidth || 1280, window.innerHeight || 720);\n" +
                "          if (t2) applyFocus(t2, false);\n" +
                "        }, 80);\n" +
                "        return 'waking';\n" +
                "      }\n" +
                "      return 'cross-fs';\n" +
                "    }\n" +
                "    var cur = window._cleanTvFocusEl;\n" +
                "    if (!cur || !cur.isConnected || !document.contains(cur) || (fsEl && !fsEl.contains(cur))) {\n" +
                "      cur = document.activeElement;\n" +
                "    }\n" +
                "    var cr = null, curInCandidates = false;\n" +
                "    if (cur && cur !== document.body && cur !== document.documentElement && (!fsEl || fsEl.contains(cur))) {\n" +
                "      for (var ci = 0; ci < candidates.length; ci++) {\n" +
                "        if (candidates[ci].el === cur) { cr = candidates[ci].r; curInCandidates = true; break; }\n" +
                "      }\n" +
                "    }\n" +
                "    if (!curInCandidates || !cr) {\n" +
                "      var initTarget = pickInitialTarget(candidates, fsEl, vw, vh);\n" +
                "      if (initTarget) { applyFocus(initTarget, !fsEl); return true; }\n" +
                "      return fsEl ? 'cross-fs' : false;\n" +
                "    }\n" +
                "    var curCx = cr.left + cr.width / 2;\n" +
                "    var curCy = cr.top + cr.height / 2;\n" +
                "    var best = null, bestScore = Infinity;\n" +
                "    for (var j = 0; j < candidates.length; j++) {\n" +
                "      var c = candidates[j];\n" +
                "      if (c.el === cur) continue;\n" +
                "      var dx = c.cx - curCx;\n" +
                "      var dy = c.cy - curCy;\n" +
                "      var valid = false, pri = 0, perp = 0;\n" +
                "      if (dir === 'ArrowRight' && dx > 6) { valid = true; pri = dx; perp = Math.abs(dy); }\n" +
                "      else if (dir === 'ArrowLeft' && dx < -6) { valid = true; pri = -dx; perp = Math.abs(dy); }\n" +
                "      else if (dir === 'ArrowDown' && dy > 6) { valid = true; pri = dy; perp = Math.abs(dx); }\n" +
                "      else if (dir === 'ArrowUp' && dy < -6) { valid = true; pri = -dy; perp = Math.abs(dx); }\n" +
                "      if (valid && pri <= (fsEl ? 1200 : 520)) {\n" +
                "        var w = (dir === 'ArrowLeft' || dir === 'ArrowRight') ? 3.5 : 2.2;\n" +
                "        var overlapBonus = 0;\n" +
                "        if ((dir === 'ArrowLeft' || dir === 'ArrowRight') && c.r.bottom > cr.top && c.r.top < cr.bottom) overlapBonus = -120;\n" +
                "        if ((dir === 'ArrowUp' || dir === 'ArrowDown') && c.r.right > cr.left && c.r.left < cr.right) overlapBonus = -120;\n" +
                "        var score = pri + perp * w + overlapBonus;\n" +
                "        if (score < bestScore) { bestScore = score; best = c.el; }\n" +
                "      }\n" +
                "    }\n" +
                "    if (best) {\n" +
                "      applyFocus(best, !fsEl);\n" +
                "      return true;\n" +
                "    }\n" +
                "    if (fsEl) return true;\n" +
                "    var sx = dir === 'ArrowRight' ? 220 : (dir === 'ArrowLeft' ? -220 : 0);\n" +
                "    var sy = dir === 'ArrowDown' ? 220 : (dir === 'ArrowUp' ? -220 : 0);\n" +
                "    var ox = window.scrollX, oy = window.scrollY;\n" +
                "    window.scrollBy({ left: sx, top: sy, behavior: 'smooth' });\n" +
                "    var p = cur;\n" +
                "    while (p && p !== document.body && p !== document.documentElement) {\n" +
                "      if ((sx !== 0 && p.scrollWidth > p.clientWidth) || (sy !== 0 && p.scrollHeight > p.clientHeight)) {\n" +
                "        try { p.scrollBy({ left: sx, top: sy, behavior: 'smooth' }); } catch(e) { p.scrollLeft += sx; p.scrollTop += sy; }\n" +
                "      }\n" +
                "      p = p.parentElement;\n" +
                "    }\n" +
                "    setTimeout(function() {\n" +
                "      if (window.scrollX === ox && window.scrollY === oy && window.CleanTvBridge && window.CleanTvBridge.nativeScrollFallback) {\n" +
                "        window.CleanTvBridge.nativeScrollFallback(sx, sy);\n" +
                "      }\n" +
                "    }, 60);\n" +
                "    return true;\n" +
                "  };\n" +
                "\n" +
                "  function tapElement(ae, fsEl) {\n" +
                "    var vw = window.innerWidth || 1280;\n" +
                "    var vh = window.innerHeight || 720;\n" +
                "    var r = ae.getBoundingClientRect();\n" +
                "    if (r.width > 2 && r.height > 2) {\n" +
                "      var nx = Math.max(0.01, Math.min(0.99, (r.left + r.width * 0.5) / vw));\n" +
                "      var ny = Math.max(0.01, Math.min(0.99, (r.top + r.height * 0.5) / vh));\n" +
                "      if (window.top === window && window.CleanTvBridge && window.CleanTvBridge.nativeTapNormalized) {\n" +
                "        window.CleanTvBridge.nativeTapNormalized(nx, ny);\n" +
                "        return 'native-tap';\n" +
                "      } else if (window.top !== window) {\n" +
                "        window.top.postMessage({ type: 'cleanTvCmd', cmd: 'tapNorm', nx: nx, ny: ny, inFs: !!fsEl }, '*');\n" +
                "        return 'post-tap';\n" +
                "      }\n" +
                "    }\n" +
                "    if (typeof ae.click === 'function') { ae.click(); return 'clicked'; }\n" +
                "    return 'none';\n" +
                "  }\n" +
                "\n" +
                "  window._cleanTvActivateFocused = function() {\n" +
                "    window._cleanTvWakeControls();\n" +
                "    var fsEl = getFullscreenRoot();\n" +
                "    if (fsEl && fsEl.tagName === 'IFRAME' && fsEl.contentWindow) {\n" +
                "      fsEl.contentWindow.postMessage({ type: 'cleanTvCmd', cmd: 'activate' }, '*');\n" +
                "      return 'postmsg';\n" +
                "    }\n" +
                "    var ae = window._cleanTvFocusEl;\n" +
                "    if (!ae || !ae.isConnected || (fsEl && !fsEl.contains(ae))) ae = document.activeElement;\n" +
                "    if (ae && (ae.tagName === 'INPUT' || ae.tagName === 'TEXTAREA')) {\n" +
                "      var tp = (ae.type || '').toLowerCase();\n" +
                "      if (tp !== 'button' && tp !== 'submit' && tp !== 'checkbox' && tp !== 'radio') {\n" +
                "        ae.removeAttribute('readonly');\n" +
                "        ae.focus();\n" +
                "        if (window.top === window && window.CleanTvBridge && window.CleanTvBridge.showSoftKeyboard) window.CleanTvBridge.showSoftKeyboard();\n" +
                "        else if (window.top !== window) window.top.postMessage({ type: 'cleanTvCmd', cmd: 'keyboard' }, '*');\n" +
                "        return 'text-input';\n" +
                "      }\n" +
                "    }\n" +
                "    if (fsEl && (!ae || ae === document.body || ae === document.documentElement || ae === fsEl)) {\n" +
                "      var vw = window.innerWidth || 1280, vh = window.innerHeight || 720;\n" +
                "      var cands = collectCandidates(fsEl, vw, vh);\n" +
                "      ae = pickInitialTarget(cands, fsEl, vw, vh);\n" +
                "      if (ae) applyFocus(ae, false);\n" +
                "    }\n" +
                "    if (ae && ae !== document.body && ae !== document.documentElement) {\n" +
                "      var tag = ae.tagName;\n" +
                "      if (fsEl || tag === 'IFRAME' || tag === 'VIDEO' || (tag !== 'A' && tag !== 'BUTTON' && tag !== 'SELECT' && tag !== 'INPUT')) {\n" +
                "        var tRes = tapElement(ae, fsEl);\n" +
                "        if (tRes !== 'none') return tRes;\n" +
                "      }\n" +
                "      if (typeof ae.click === 'function') { ae.click(); return 'clicked'; }\n" +
                "    }\n" +
                "    return fsEl ? 'cross-fs' : 'none';\n" +
                "  };\n" +
                "\n" +
                "  window.addEventListener('message', function(e) {\n" +
                "    var d = e.data;\n" +
                "    if (!d) return;\n" +
                "    if (d.type === 'cleanTvFocusRect') {\n" +
                "      var mapped = d.rect;\n" +
                "      if (Array.isArray(mapped) && mapped.length === 4) {\n" +
                "        var fsR = getFullscreenRoot();\n" +
                "        var fList = document.querySelectorAll('iframe');\n" +
                "        for (var fi = 0; fi < fList.length; fi++) {\n" +
                "          if (fList[fi].contentWindow === e.source) {\n" +
                "            if (fsR !== fList[fi]) {\n" +
                "              var fir = fList[fi].getBoundingClientRect();\n" +
                "              var fvw = window.innerWidth || 1280, fvh = window.innerHeight || 720;\n" +
                "              mapped = [\n" +
                "                (fir.left + mapped[0] * fir.width) / fvw,\n" +
                "                (fir.top + mapped[1] * fir.height) / fvh,\n" +
                "                (mapped[2] * fir.width) / fvw,\n" +
                "                (mapped[3] * fir.height) / fvh\n" +
                "              ];\n" +
                "            }\n" +
                "            break;\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "      emitFocusRectArr(mapped || null);\n" +
                "      return;\n" +
                "    }\n" +
                "    if (d.type !== 'cleanTvCmd') return;\n" +
                "    if (d.cmd === 'flag') {\n" +
                "      window._cleanTvSetDpadFlag(!!d.on);\n" +
                "    } else if (d.cmd === 'init') {\n" +
                "      window._cleanTvInitFocus();\n" +
                "    } else if (d.cmd === 'step') {\n" +
                "      window._cleanTvStepFocus(d.dir);\n" +
                "    } else if (d.cmd === 'activate') {\n" +
                "      window._cleanTvActivateFocused();\n" +
                "    } else if (d.cmd === 'keyboard' && window.top === window && window.CleanTvBridge && window.CleanTvBridge.showSoftKeyboard) {\n" +
                "      window.CleanTvBridge.showSoftKeyboard();\n" +
                "    } else if (d.cmd === 'tapNorm' && window.top === window && window.CleanTvBridge && window.CleanTvBridge.nativeTapNormalized) {\n" +
                "      var tx = d.nx, ty = d.ny;\n" +
                "      if (!d.inFs) {\n" +
                "        var vw = window.innerWidth || 1280, vh = window.innerHeight || 720;\n" +
                "        var ifrs = document.querySelectorAll('iframe');\n" +
                "        for (var i = 0; i < ifrs.length; i++) {\n" +
                "          if (ifrs[i].contentWindow === e.source) {\n" +
                "            var ir = ifrs[i].getBoundingClientRect();\n" +
                "            tx = (ir.left + ir.width * d.nx) / vw;\n" +
                "            ty = (ir.top + ir.height * d.ny) / vh;\n" +
                "            break;\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "      window.CleanTvBridge.nativeTapNormalized(tx, ty);\n" +
                "    }\n" +
                "  });\n" +
                "\n" +
                "  document.addEventListener('keydown', function(e) {\n" +
                "    if (!window._cleanTvDpadMode) return;\n" +
                "    if (e.defaultPrevented) return;\n" +
                "    var ae = window._cleanTvFocusEl || document.activeElement;\n" +
                "    if (e.key === 'Enter') {\n" +
                "      var res = window._cleanTvActivateFocused();\n" +
                "      if (res === 'native-tap' || res === 'post-tap' || res === 'clicked' || res === 'postmsg') e.preventDefault();\n" +
                "      return;\n" +
                "    }\n" +
                "    if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown' && e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;\n" +
                "    if (ae) {\n" +
                "      var tag = ae.tagName;\n" +
                "      if (tag === 'CANVAS') return;\n" +
                "      if ((tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) return;\n" +
                "    }\n" +
                "    var moved = window._cleanTvStepFocus(e.key);\n" +
                "    if (moved === true || moved === 'postmsg' || moved === 'waking') e.preventDefault();\n" +
                "  }, false);\n" +
                "})();";
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
