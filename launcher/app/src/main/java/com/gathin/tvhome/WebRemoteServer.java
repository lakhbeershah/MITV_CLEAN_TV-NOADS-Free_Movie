package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Super-lightweight HTTP web server running on port 8080 and 8000.
 * Allows controlling the TV from any mobile phone or browser over Wi-Fi without installing an app.
 */
public class WebRemoteServer {

    private static final String TAG = "WebRemoteServer";
    public static final int PORT = 8080;
    public static final int PORT_ALT = 8000;

    private static ServerSocket serverSocket;
    private static ServerSocket serverSocketAlt;
    private static Thread serverThread;
    private static Thread serverThreadAlt;
    private static volatile boolean isRunning = false;
    private static final ExecutorService KEY_EXECUTOR = Executors.newSingleThreadExecutor();

    private static final String HTML_PAGE = "<!DOCTYPE html>\n" +
            "<html lang=\"en\">\n" +
            "<head>\n" +
            "<meta charset=\"utf-8\">\n" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no\">\n" +
            "<meta name=\"apple-mobile-web-app-capable\" content=\"yes\">\n" +
            "<meta name=\"theme-color\" content=\"#0E1116\">\n" +
            "<title>Xiaomi TV Remote</title>\n" +
            "<style>\n" +
            "* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; user-select: none; margin: 0; padding: 0; }\n" +
            "body { background: #0E1116; color: #FFFFFF; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; text-align: center; padding: 16px 12px; min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: flex-start; }\n" +
            ".header { width: 100%; max-width: 320px; display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }\n" +
            "h2 { font-size: 19px; font-weight: 600; color: #FFFFFF; letter-spacing: 0.5px; }\n" +
            ".btn-pwr { width: 44px; height: 44px; border-radius: 50%; background: #261719; border: 1px solid #FF5252; color: #FF5252; font-size: 18px; cursor: pointer; display: flex; align-items: center; justify-content: center; }\n" +
            ".btn-pwr:active { background: #FF5252; color: #FFF; transform: scale(0.92); }\n" +
            ".dpad-wrap { margin: 8px auto; }\n" +
            ".dpad { width: 240px; height: 240px; position: relative; background: #1B2028; border-radius: 50%; box-shadow: 0 10px 30px rgba(0,0,0,0.6), inset 0 2px 4px rgba(255,255,255,0.05); }\n" +
            ".btn { background: #262D38; color: #FFFFFF; border: none; outline: none; border-radius: 14px; font-size: 18px; font-weight: bold; cursor: pointer; transition: background 0.1s, transform 0.08s; }\n" +
            ".btn:active { background: #00E676; color: #000; transform: scale(0.94); }\n" +
            ".d-up { position: absolute; top: 10px; left: 75px; width: 90px; height: 68px; border-radius: 40px 40px 12px 12px; }\n" +
            ".d-down { position: absolute; bottom: 10px; left: 75px; width: 90px; height: 68px; border-radius: 12px 12px 40px 40px; }\n" +
            ".d-left { position: absolute; top: 75px; left: 10px; width: 68px; height: 90px; border-radius: 40px 12px 12px 40px; }\n" +
            ".d-right { position: absolute; top: 75px; right: 10px; width: 68px; height: 90px; border-radius: 12px 40px 40px 12px; }\n" +
            ".d-ok { position: absolute; top: 75px; left: 75px; width: 90px; height: 90px; border-radius: 50%; background: #00E676; color: #000000; font-size: 18px; font-weight: 700; box-shadow: 0 0 20px rgba(0,230,118,0.35); }\n" +
            ".d-ok:active { background: #00C853; transform: scale(0.92); }\n" +
            ".nav-row { width: 100%; max-width: 320px; display: flex; justify-content: space-between; gap: 8px; margin-top: 10px; }\n" +
            ".nav-btn { flex: 1; height: 46px; font-size: 13px; font-weight: 600; background: #1B2028; border: 1px solid #2B3340; border-radius: 12px; display: flex; align-items: center; justify-content: center; }\n" +
            ".kb-card { width: 100%; max-width: 320px; background: #1B2028; border: 1px solid #2B3340; border-radius: 16px; padding: 12px; margin-top: 12px; box-shadow: 0 4px 16px rgba(0,0,0,0.4); }\n" +
            ".kb-title { font-size: 13px; font-weight: 600; color: #8A96AC; text-align: left; margin-bottom: 8px; display: flex; align-items: center; gap: 6px; }\n" +
            ".kb-row { display: flex; gap: 8px; margin-bottom: 8px; }\n" +
            "#kbtxt { flex: 1; padding: 10px 12px; border-radius: 10px; border: 1px solid #2B3340; background: #0E1116; color: #FFF; font-size: 14px; outline: none; }\n" +
            "#kbtxt:focus { border-color: #00E676; }\n" +
            ".btn-send { padding: 0 14px; height: 40px; border-radius: 10px; background: #00E676; color: #000; font-size: 13px; font-weight: 700; border: none; cursor: pointer; white-space: nowrap; }\n" +
            ".btn-send:active { transform: scale(0.95); background: #00C853; }\n" +
            ".kb-tools { display: flex; justify-content: space-between; gap: 6px; }\n" +
            ".kb-btn { flex: 1; height: 36px; font-size: 12px; font-weight: 600; background: #262D38; border: none; border-radius: 8px; color: #FFF; cursor: pointer; }\n" +
            ".kb-btn:active { background: #00E676; color: #000; transform: scale(0.95); }\n" +
            ".status { font-size: 12px; color: #00E676; margin-top: 14px; margin-bottom: 6px; display: flex; align-items: center; justify-content: center; gap: 6px; }\n" +
            ".dot { width: 8px; height: 8px; border-radius: 50%; background: #00E676; display: inline-block; box-shadow: 0 0 8px #00E676; }\n" +
            ".offline { color: #FF5252; }\n" +
            ".offline .dot { background: #FF5252; box-shadow: 0 0 8px #FF5252; }\n" +
            "</style>\n" +
            "</head>\n" +
            "<body>\n" +
            "<div class=\"header\">\n" +
            "  <h2>📺 Xiaomi Mi TV</h2>\n" +
            "  <button class=\"btn-pwr\" onclick=\"k(26)\">⏻</button>\n" +
            "</div>\n" +
            "<div class=\"dpad-wrap\">\n" +
            "  <div class=\"dpad\">\n" +
            "    <button class=\"btn d-up\" onclick=\"k(19)\">▲</button>\n" +
            "    <button class=\"btn d-left\" onclick=\"k(21)\">◀</button>\n" +
            "    <button class=\"btn d-ok\" onclick=\"k(23)\">OK</button>\n" +
            "    <button class=\"btn d-right\" onclick=\"k(22)\">▶</button>\n" +
            "    <button class=\"btn d-down\" onclick=\"k(20)\">▼</button>\n" +
            "  </div>\n" +
            "</div>\n" +
            "<div class=\"nav-row\">\n" +
            "  <button class=\"btn nav-btn\" onclick=\"k(4)\">↩ Back</button>\n" +
            "  <button class=\"btn nav-btn\" onclick=\"k(3)\">🏠 Home</button>\n" +
            "  <button class=\"btn nav-btn\" onclick=\"k(82)\">☰ Menu</button>\n" +
            "</div>\n" +
            "<div class=\"nav-row\">\n" +
            "  <button class=\"btn nav-btn\" onclick=\"k(25)\">🔉 Vol −</button>\n" +
            "  <button class=\"btn nav-btn\" onclick=\"k(164)\">🔇 Mute</button>\n" +
            "  <button class=\"btn nav-btn\" onclick=\"k(24)\">🔊 Vol +</button>\n" +
            "</div>\n" +
            "\n" +
            "<div class=\"kb-card\">\n" +
            "  <div class=\"kb-title\">⌨️ Type to TV (Search & Input)</div>\n" +
            "  <div class=\"kb-row\">\n" +
            "    <input id=\"kbtxt\" type=\"text\" placeholder=\"Type search / text here...\" autocomplete=\"off\">\n" +
            "    <button class=\"btn btn-send\" onclick=\"sendText()\">Send ↵</button>\n" +
            "  </div>\n" +
            "  <div class=\"kb-tools\">\n" +
            "    <button class=\"btn kb-btn\" onclick=\"k(67)\">⌫ Del</button>\n" +
            "    <button class=\"btn kb-btn\" onclick=\"k(62)\">␣ Space</button>\n" +
            "    <button class=\"btn kb-btn\" onclick=\"k(66)\">⏎ Enter</button>\n" +
            "    <button class=\"btn kb-btn\" onclick=\"clearKb()\">Clear</button>\n" +
            "  </div>\n" +
            "</div>\n" +
            "\n" +
            "<p id=\"stat\" class=\"status\"><span class=\"dot\"></span> Connected to TV</p>\n" +
            "<script>\n" +
            "function k(c) {\n" +
            "  fetch('/key?code=' + c, { method: 'GET', cache: 'no-store' })\n" +
            "    .catch(() => updateStat(false));\n" +
            "  if (navigator.vibrate) navigator.vibrate(35);\n" +
            "}\n" +
            "function sendText() {\n" +
            "  var el = document.getElementById('kbtxt');\n" +
            "  var t = el.value;\n" +
            "  if (!t) return;\n" +
            "  fetch('/type?text=' + encodeURIComponent(t), { cache: 'no-store' })\n" +
            "    .then(r => { if (r.ok) el.value = ''; })\n" +
            "    .catch(() => updateStat(false));\n" +
            "  if (navigator.vibrate) navigator.vibrate(35);\n" +
            "}\n" +
            "document.getElementById('kbtxt').addEventListener('keydown', function(e) {\n" +
            "  if (e.key === 'Enter') {\n" +
            "    e.preventDefault();\n" +
            "    sendText();\n" +
            "  }\n" +
            "});\n" +
            "function clearKb() {\n" +
            "  document.getElementById('kbtxt').value = '';\n" +
            "}\n" +
            "function updateStat(online) {\n" +
            "  var el = document.getElementById('stat');\n" +
            "  if (!el) return;\n" +
            "  if (online) {\n" +
            "    el.className = 'status';\n" +
            "    el.innerHTML = '<span class=\"dot\"></span> Connected to TV';\n" +
            "  } else {\n" +
            "    el.className = 'status offline';\n" +
            "    el.innerHTML = '<span class=\"dot\"></span> TV Offline (Check Wi-Fi)';\n" +
            "  }\n" +
            "}\n" +
            "setInterval(() => {\n" +
            "  fetch('/ping', { cache: 'no-store' })\n" +
            "    .then(r => r.ok ? updateStat(true) : updateStat(false))\n" +
            "    .catch(() => updateStat(false));\n" +
            "}, 4000);\n" +
            "</script>\n" +
            "</body>\n" +
            "</html>\n";

    private static final String PHOTO_LINK_PAGE = "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "<meta charset=\"utf-8\">\n" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no\">\n" +
            "<meta name=\"theme-color\" content=\"#0E1116\">\n" +
            "<title>Send Google Photos Link</title>\n" +
            "<style>\n" +
            "* { box-sizing: border-box; margin: 0; padding: 0; }\n" +
            "body { background: #0E1116; color: #FFF; font-family: -apple-system, sans-serif; text-align: center; padding: 32px 16px; }\n" +
            ".box { max-width: 380px; margin: 0 auto; background: #1B2028; border: 1px solid #2B3340; border-radius: 18px; padding: 24px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }\n" +
            "h2 { font-size: 20px; font-weight: 700; margin-bottom: 8px; color: #FFF; }\n" +
            "p { font-size: 13px; color: #8A96AC; line-height: 1.5; margin-bottom: 20px; }\n" +
            "input { width: 100%; padding: 14px 16px; border-radius: 12px; border: 1px solid #2B3340; background: #0E1116; color: #FFF; font-size: 14px; margin-bottom: 16px; outline: none; }\n" +
            "input:focus { border-color: #00E676; }\n" +
            "button { width: 100%; padding: 14px; border-radius: 12px; border: none; background: #00E676; color: #000; font-size: 16px; font-weight: 700; cursor: pointer; transition: transform 0.1s; }\n" +
            "button:active { transform: scale(0.97); }\n" +
            ".back { display: inline-block; margin-top: 20px; font-size: 14px; color: #80D8FF; text-decoration: none; }\n" +
            ".success { display: none; margin-top: 16px; color: #00E676; font-size: 15px; font-weight: 700; }\n" +
            "</style>\n" +
            "</head>\n" +
            "<body>\n" +
            "<div class=\"box\">\n" +
            "  <h2>📺 Send Google Photos Link</h2>\n" +
            "  <p>Paste your shared album link from Google Photos below and click send:</p>\n" +
            "  <input id=\"url\" type=\"url\" placeholder=\"https://photos.app.goo.gl/...\" autocomplete=\"off\">\n" +
            "  <button onclick=\"send()\">Send to TV 🚀</button>\n" +
            "  <div id=\"done\" class=\"success\">✓ Link updated on TV!</div>\n" +
            "</div>\n" +
            "<a href=\"/\" class=\"back\">← Open TV Web Remote</a>\n" +
            "<script>\n" +
            "function send() {\n" +
            "  var u = document.getElementById('url').value.trim();\n" +
            "  if (!u) return alert('Please paste an album link');\n" +
            "  fetch('/set-photo-link?url=' + encodeURIComponent(u))\n" +
            "    .then(r => r.text())\n" +
            "    .then(() => {\n" +
            "      document.getElementById('done').style.display = 'block';\n" +
            "    })\n" +
            "    .catch(e => alert('Error sending to TV: ' + e));\n" +
            "}\n" +
            "</script>\n" +
            "</body>\n" +
            "</html>\n";

    public static synchronized boolean isRunning() {
        return isRunning;
    }

    public static synchronized void startServer(Activity activity) {
        if (isRunning) return;
        isRunning = true;

        startListener(activity, PORT, true);
        startListener(activity, PORT_ALT, false);
    }

    private static void startListener(Activity activity, int port, boolean primary) {
        Thread t = new Thread(() -> {
            ServerSocket ss = null;
            try {
                ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress("0.0.0.0", port), 50);
                if (primary) serverSocket = ss;
                else serverSocketAlt = ss;

                Log.i(TAG, "Web Remote Server successfully listening on 0.0.0.0:" + port);

                while (isRunning && !ss.isClosed()) {
                    Socket client = ss.accept();
                    client.setSoTimeout(5000);
                    handleClient(activity, client);
                }
            } catch (Exception e) {
                if (isRunning) {
                    Log.e(TAG, "Server error on port " + port, e);
                }
            } finally {
                if (ss != null) {
                    try { ss.close(); } catch (Exception ignored) {}
                }
            }
        });
        t.setDaemon(true);
        if (primary) serverThread = t;
        else serverThreadAlt = t;
        t.start();
    }

    public static synchronized void stopServer() {
        isRunning = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (Exception ignored) {}
            serverSocket = null;
        }
        if (serverSocketAlt != null) {
            try { serverSocketAlt.close(); } catch (Exception ignored) {}
            serverSocketAlt = null;
        }
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread = null;
        }
        if (serverThreadAlt != null) {
            serverThreadAlt.interrupt();
            serverThreadAlt = null;
        }
        Log.i(TAG, "Web Remote Server stopped");
    }

    private static void handleClient(Activity activity, Socket client) {
        new Thread(() -> {
            try {
                InputStream rawIn = client.getInputStream();
                PushbackInputStream pbis = new PushbackInputStream(rawIn, 16);
                int firstByte = pbis.read();
                if (firstByte == 0x16) {
                    Log.w(TAG, "HTTPS/TLS handshake detected from " + client.getRemoteSocketAddress() + " - Web Remote only supports plain HTTP! Please use http:// not https://");
                    try { client.close(); } catch (Exception ignored) {}
                    return;
                }
                if (firstByte != -1) {
                    pbis.unread(firstByte);
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(pbis, StandardCharsets.UTF_8));
                OutputStream os = client.getOutputStream();

                String requestLine = reader.readLine();
                Log.i(TAG, "Request: " + requestLine + " from " + client.getRemoteSocketAddress());
                if (requestLine == null) {
                    try { client.close(); } catch (Exception ignored) {}
                    return;
                }

                String corsHeaders = "Access-Control-Allow-Origin: *\r\n" +
                        "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                        "Access-Control-Allow-Headers: *\r\n" +
                        "Cache-Control: no-cache, no-store, must-revalidate\r\n";

                if (requestLine.startsWith("OPTIONS")) {
                    String resp = "HTTP/1.1 204 No Content\r\n" + corsHeaders + "Connection: close\r\n\r\n";
                    os.write(resp.getBytes(StandardCharsets.UTF_8));
                } else if (requestLine.startsWith("GET /key?code=")) {
                    int idx = requestLine.indexOf("code=");
                    int end = requestLine.indexOf(" ", idx);
                    if (end == -1) end = requestLine.length();
                    String codeStr = requestLine.substring(idx + 5, end);
                    try {
                        int code = Integer.parseInt(codeStr);
                        sendKeyEvent(activity, code);
                    } catch (Exception ignored) {}

                    String resp = "HTTP/1.1 200 OK\r\n" + corsHeaders + "Content-Type: text/plain\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK";
                    os.write(resp.getBytes(StandardCharsets.UTF_8));
                } else if (requestLine.startsWith("GET /type?text=")) {
                    int idx = requestLine.indexOf("text=");
                    int end = requestLine.indexOf(" ", idx);
                    if (end == -1) end = requestLine.length();
                    String raw = requestLine.substring(idx + 5, end);
                    try {
                        String text = URLDecoder.decode(raw, "UTF-8");
                        sendText(text);
                    } catch (Exception ignored) {}

                    String resp = "HTTP/1.1 200 OK\r\n" + corsHeaders + "Content-Type: text/plain\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK";
                    os.write(resp.getBytes(StandardCharsets.UTF_8));
                } else if (requestLine.startsWith("GET /ping")) {
                    String resp = "HTTP/1.1 200 OK\r\n" + corsHeaders + "Content-Type: text/plain\r\nContent-Length: 4\r\nConnection: close\r\n\r\nPONG";
                    os.write(resp.getBytes(StandardCharsets.UTF_8));
                } else if (requestLine.startsWith("GET /favicon.ico")) {
                    String resp = "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n";
                    os.write(resp.getBytes(StandardCharsets.UTF_8));
                } else if (requestLine.startsWith("GET /photo-link")) {
                    byte[] bytes = PHOTO_LINK_PAGE.getBytes(StandardCharsets.UTF_8);
                    String header = "HTTP/1.1 200 OK\r\n" +
                            corsHeaders +
                            "Content-Type: text/html; charset=UTF-8\r\n" +
                            "Content-Length: " + bytes.length + "\r\n" +
                            "Connection: close\r\n\r\n";
                    os.write(header.getBytes(StandardCharsets.UTF_8));
                    os.write(bytes);
                } else if (requestLine.startsWith("GET /stream-hub")) {
                    byte[] bytes = loadStreamHub(activity);
                    String header = "HTTP/1.1 200 OK\r\n" +
                            corsHeaders +
                            "Content-Type: text/html; charset=UTF-8\r\n" +
                            "Content-Length: " + bytes.length + "\r\n" +
                            "Connection: close\r\n\r\n";
                    os.write(header.getBytes(StandardCharsets.UTF_8));
                    os.write(bytes);
                } else if (requestLine.startsWith("GET /set-photo-link")) {
                    int idx = requestLine.indexOf("url=");
                    if (idx != -1) {
                        int end = requestLine.indexOf(" ", idx);
                        if (end == -1) end = requestLine.length();
                        String raw = requestLine.substring(idx + 4, end);
                        try {
                            String decoded = URLDecoder.decode(raw, "UTF-8");
                            if (activity != null) {
                                activity.runOnUiThread(() -> {
                                    SharedPreferences sp = activity.getSharedPreferences(GooglePhotosSyncEngine.PREFS_NAME, Context.MODE_PRIVATE);
                                    sp.edit().putString(GooglePhotosSyncEngine.KEY_ALBUM_URL, decoded).apply();
                                    ControlCenterManager.notifyAlbumLinkUpdated(decoded);
                                    Toast.makeText(activity, "Google Photos link updated from phone!", Toast.LENGTH_LONG).show();
                                });
                            }
                        } catch (Exception ignored) {}
                    }
                    String resp = "HTTP/1.1 200 OK\r\n" + corsHeaders + "Content-Type: text/plain\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK";
                    os.write(resp.getBytes(StandardCharsets.UTF_8));
                } else {
                    // Serve HTML page
                    byte[] bytes = HTML_PAGE.getBytes(StandardCharsets.UTF_8);
                    String header = "HTTP/1.1 200 OK\r\n" +
                            corsHeaders +
                            "Content-Type: text/html; charset=UTF-8\r\n" +
                            "Content-Length: " + bytes.length + "\r\n" +
                            "Connection: close\r\n\r\n";
                    os.write(header.getBytes(StandardCharsets.UTF_8));
                    os.write(bytes);
                }
                os.flush();
            } catch (Exception ignored) {
            } finally {
                try { client.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    private static void sendKeyEvent(Activity activity, int code) {
        // Fast path 1: If MainActivity is currently focused, dispatch directly on UI thread (0ms latency, zero lag!)
        if (activity != null && activity.hasWindowFocus()) {
            activity.runOnUiThread(() -> {
                try {
                    activity.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code));
                    activity.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code));
                } catch (Throwable ignored) {}
            });
            return;
        }

        // Fast path 2: Ultra-fast shell bridge on 127.0.0.1:9099 (UID 2000 with INJECT_EVENTS permission)
        // Works across SmartTube, Netflix, YouTube, Browser, Settings in <2ms!
        if (sendViaBridge("input keyevent " + code + "\n")) {
            return;
        }

        // Fast path 3: If another app is in foreground and bridge is offline, use HomeKeyService Accessibility
        if (HomeKeyService.getInstance() != null) {
            boolean handled = HomeKeyService.navigate(code);
            if (handled) return;
        }

        // Fallback: system input keyevent in background executor
        KEY_EXECUTOR.execute(() -> {
            try {
                Runtime.getRuntime().exec("input keyevent " + code);
            } catch (Exception ignored) {}
        });
    }

    private static void sendText(String text) {
        if (text == null || text.isEmpty()) return;
        String encoded = text.replace(" ", "%s").replace("'", "\\'");
        // Fast path 1: Instant shell bridge on 127.0.0.1:9099
        if (sendViaBridge("input text " + encoded + "\n")) {
            return;
        }
        // Fast path 2: Use AccessibilityService ACTION_SET_TEXT to set text directly in focused input field (0ms!)
        if (HomeKeyService.typeText(text)) {
            return;
        }
        // Fallback: input text
        KEY_EXECUTOR.execute(() -> {
            try {
                Runtime.getRuntime().exec(new String[]{"input", "text", encoded});
            } catch (Exception ignored) {}
        });
    }

    private static boolean sendViaBridge(String cmd) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 9099), 80);
            OutputStream os = s.getOutputStream();
            os.write(cmd.getBytes(StandardCharsets.UTF_8));
            os.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] cachedStreamHub = null;

    private static synchronized byte[] loadStreamHub(Activity activity) {
        if (cachedStreamHub != null) return cachedStreamHub;
        try {
            if (activity != null) {
                InputStream is = activity.getAssets().open("stream_hub.html");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                is.close();
                cachedStreamHub = baos.toByteArray();
                return cachedStreamHub;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load stream_hub.html from assets", e);
        }
        return "<html><body><h2>Stream Hub</h2><p>Please reload.</p></body></html>".getBytes(StandardCharsets.UTF_8);
    }
}
