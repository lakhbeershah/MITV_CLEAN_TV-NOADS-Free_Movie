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
                "      var vids = document.querySelectorAll('video');\n" +
                "      for (var i = 0; i < vids.length; i++) {\n" +
                "        var v = vids[i];\n" +
                "        if (!v.paused && v.currentTime > 0 && !v.ended && v.readyState >= 2) { active = true; break; }\n" +
                "      }\n" +
                "    }\n" +
                "    if (!active) {\n" +
                "      var frames = document.querySelectorAll('iframe');\n" +
                "      for (var j = 0; j < frames.length; j++) {\n" +
                "        try {\n" +
                "          var fv = frames[j].contentDocument.querySelector('video');\n" +
                "          if (fv && (!fv.paused && fv.currentTime > 0 && !fv.ended && fv.readyState >= 2)) { active = true; break; }\n" +
                "        } catch(e) {}\n" +
                "      }\n" +
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
                "\n" +
                "  // 7. TV Spatial Focus Selector Ring (High-visibility for D-Pad navigation)\n" +
                "  try {\n" +
                "    var focusTarget = document.head || document.body || document.documentElement;\n" +
                "    if (focusTarget && !document.getElementById('clean_tv_focus_css')) {\n" +
                "      var fStyle = document.createElement('style');\n" +
                "      fStyle.id = 'clean_tv_focus_css';\n" +
                "      fStyle.innerHTML = ':focus, :focus-visible, a:focus, button:focus, [tabindex]:focus { outline: 3px solid #00E5FF !important; outline-offset: 3px !important; box-shadow: 0 0 14px rgba(0, 229, 255, 0.9) !important; }';\n" +
                "      focusTarget.appendChild(fStyle);\n" +
                "    }\n" +
                "  } catch(e) {}\n" +
                "\n" +
                "  // 8. Universal 2D Spatial Navigator Selector & Left/Right Scroll Engine\n" +
                "  if (window._cleanTvDpadMode === undefined) window._cleanTvDpadMode = false;\n" +
                "  window._cleanTvStepFocus = function(dir) {\n" +
                "    var vw = window.innerWidth || 1280;\n" +
                "    var vh = window.innerHeight || 720;\n" +
                "    var cur = document.activeElement;\n" +
                "    var selectors = '.site-card, a[href], button, [role=\"button\"], input:not([type=\"hidden\"]), select, textarea, summary, [tabindex]:not([tabindex=\"-1\"]), [onclick]';\n" +
                "    var nodes = document.querySelectorAll(selectors);\n" +
                "    var candidates = [];\n" +
                "    for (var i = 0; i < nodes.length; i++) {\n" +
                "      var el = nodes[i];\n" +
                "      var r = el.getBoundingClientRect();\n" +
                "      if (r.width < 4 || r.height < 4) continue;\n" +
                "      if (r.bottom < -300 || r.top > vh + 300 || r.right < -300 || r.left > vw + 300) continue;\n" +
                "      var cs = window.getComputedStyle(el);\n" +
                "      if (cs.visibility === 'hidden' || cs.display === 'none' || parseFloat(cs.opacity) === 0) continue;\n" +
                "      candidates.push({ el: el, r: r, cx: r.left + r.width / 2, cy: r.top + r.height / 2 });\n" +
                "    }\n" +
                "    var hasCur = cur && cur !== document.body && cur !== document.documentElement;\n" +
                "    var cr = hasCur ? cur.getBoundingClientRect() : null;\n" +
                "    if (cr && (cr.width < 2 || cr.height < 2)) hasCur = false;\n" +
                "    if (!hasCur) {\n" +
                "      var bestInit = null;\n" +
                "      var bestInitScore = Infinity;\n" +
                "      for (var k = 0; k < candidates.length; k++) {\n" +
                "        var c0 = candidates[k];\n" +
                "        if (c0.r.top >= 0 && c0.r.bottom <= vh && c0.r.left >= 0 && c0.r.right <= vw) {\n" +
                "          var sc = c0.r.top * 2 + c0.r.left;\n" +
                "          if (sc < bestInitScore) { bestInitScore = sc; bestInit = c0.el; }\n" +
                "        }\n" +
                "      }\n" +
                "      if (bestInit) {\n" +
                "        bestInit.focus({ preventScroll: true });\n" +
                "        return true;\n" +
                "      }\n" +
                "    }\n" +
                "    var best = null;\n" +
                "    var bestScore = Infinity;\n" +
                "    if (hasCur) {\n" +
                "      var curCx = cr.left + cr.width / 2;\n" +
                "      var curCy = cr.top + cr.height / 2;\n" +
                "      for (var j = 0; j < candidates.length; j++) {\n" +
                "        var c = candidates[j];\n" +
                "        if (c.el === cur) continue;\n" +
                "        var dx = c.cx - curCx;\n" +
                "        var dy = c.cy - curCy;\n" +
                "        var valid = false, pri = 0, perp = 0;\n" +
                "        if (dir === 'ArrowRight' && dx > 10) { valid = true; pri = dx; perp = Math.abs(dy); }\n" +
                "        else if (dir === 'ArrowLeft' && dx < -10) { valid = true; pri = -dx; perp = Math.abs(dy); }\n" +
                "        else if (dir === 'ArrowDown' && dy > 10) { valid = true; pri = dy; perp = Math.abs(dx); }\n" +
                "        else if (dir === 'ArrowUp' && dy < -10) { valid = true; pri = -dy; perp = Math.abs(dx); }\n" +
                "        if (valid && pri <= 450) {\n" +
                "          var w = (dir === 'ArrowLeft' || dir === 'ArrowRight') ? 3.8 : 2.2;\n" +
                "          var overlapBonus = 0;\n" +
                "          if ((dir === 'ArrowLeft' || dir === 'ArrowRight') && c.r.bottom > cr.top && c.r.top < cr.bottom) overlapBonus = -120;\n" +
                "          if ((dir === 'ArrowUp' || dir === 'ArrowDown') && c.r.right > cr.left && c.r.left < cr.right) overlapBonus = -120;\n" +
                "          var score = pri + perp * w + overlapBonus;\n" +
                "          if (score < bestScore) { bestScore = score; best = c.el; }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "    if (best) {\n" +
                "      best.focus({ preventScroll: true });\n" +
                "      best.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'center' });\n" +
                "      return true;\n" +
                "    }\n" +
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
                "  document.addEventListener('keydown', function(e) {\n" +
                "    if (!window._cleanTvDpadMode) return;\n" +
                "    if (e.defaultPrevented) return;\n" +
                "    var ae = document.activeElement;\n" +
                "    if (e.key === 'Enter') {\n" +
                "      if (ae && ae !== document.body && ae.tagName !== 'A' && ae.tagName !== 'BUTTON' && ae.tagName !== 'INPUT' && ae.tagName !== 'SELECT' && ae.tagName !== 'TEXTAREA' && ae.tagName !== 'SUMMARY') {\n" +
                "        if (typeof ae.click === 'function') { e.preventDefault(); ae.click(); }\n" +
                "      }\n" +
                "      return;\n" +
                "    }\n" +
                "    if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown' && e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;\n" +
                "    if (ae) {\n" +
                "      var tag = ae.tagName;\n" +
                "      if (tag === 'CANVAS') return;\n" +
                "      if ((tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) return;\n" +
                "    }\n" +
                "    if (window._cleanTvStepFocus(e.key)) e.preventDefault();\n" +
                "  }, false);\n" +
                "  window._cleanTvShieldInjected = true;\n" +
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
