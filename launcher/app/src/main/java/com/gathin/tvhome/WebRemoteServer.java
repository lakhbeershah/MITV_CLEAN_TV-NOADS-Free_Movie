package com.gathin.tvhome;

import android.app.Activity;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Super-lightweight HTTP web server running on port 8080.
 * Allows controlling the TV from any mobile phone or browser over Wi-Fi without installing an app.
 */
public class WebRemoteServer {

    private static final String TAG = "WebRemoteServer";
    public static final int PORT = 8080;

    private static ServerSocket serverSocket;
    private static Thread serverThread;
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
            "body { background: #0E1116; color: #FFFFFF; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; text-align: center; padding: 20px 16px; min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: space-between; }\n" +
            ".header { width: 100%; max-width: 320px; display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }\n" +
            "h2 { font-size: 19px; font-weight: 600; color: #FFFFFF; letter-spacing: 0.5px; }\n" +
            ".btn-pwr { width: 44px; height: 44px; border-radius: 50%; background: #261719; border: 1px solid #FF5252; color: #FF5252; font-size: 18px; cursor: pointer; display: flex; align-items: center; justify-content: center; }\n" +
            ".btn-pwr:active { background: #FF5252; color: #FFF; transform: scale(0.92); }\n" +
            ".dpad-wrap { margin: 12px auto; }\n" +
            ".dpad { width: 250px; height: 250px; position: relative; background: #1B2028; border-radius: 50%; box-shadow: 0 10px 30px rgba(0,0,0,0.6), inset 0 2px 4px rgba(255,255,255,0.05); }\n" +
            ".btn { background: #262D38; color: #FFFFFF; border: none; outline: none; border-radius: 14px; font-size: 18px; font-weight: bold; cursor: pointer; transition: background 0.1s, transform 0.08s; }\n" +
            ".btn:active { background: #00E676; color: #000; transform: scale(0.94); }\n" +
            ".d-up { position: absolute; top: 10px; left: 80px; width: 90px; height: 72px; border-radius: 40px 40px 12px 12px; }\n" +
            ".d-down { position: absolute; bottom: 10px; left: 80px; width: 90px; height: 72px; border-radius: 12px 12px 40px 40px; }\n" +
            ".d-left { position: absolute; top: 80px; left: 10px; width: 72px; height: 90px; border-radius: 40px 12px 12px 40px; }\n" +
            ".d-right { position: absolute; top: 80px; right: 10px; width: 72px; height: 90px; border-radius: 12px 40px 40px 12px; }\n" +
            ".d-ok { position: absolute; top: 80px; left: 80px; width: 90px; height: 90px; border-radius: 50%; background: #00E676; color: #000000; font-size: 18px; font-weight: 700; box-shadow: 0 0 20px rgba(0,230,118,0.35); }\n" +
            ".d-ok:active { background: #00C853; transform: scale(0.92); }\n" +
            ".nav-row { width: 100%; max-width: 300px; display: flex; justify-content: space-between; gap: 12px; margin-top: 16px; }\n" +
            ".nav-btn { flex: 1; height: 50px; font-size: 14px; font-weight: 600; background: #1B2028; border: 1px solid #2B3340; border-radius: 14px; display: flex; align-items: center; justify-content: center; }\n" +
            ".status { font-size: 12px; color: #00E676; margin-top: 20px; display: flex; align-items: center; justify-content: center; gap: 6px; }\n" +
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
            "</div>\n" +
            "<div class=\"nav-row\">\n" +
            "  <button class=\"btn nav-btn\" onclick=\"k(25)\">🔉 Vol −</button>\n" +
            "  <button class=\"btn nav-btn\" onclick=\"k(164)\">🔇 Mute</button>\n" +
            "  <button class=\"btn nav-btn\" onclick=\"k(24)\">🔊 Vol +</button>\n" +
            "</div>\n" +
            "<p id=\"stat\" class=\"status\"><span class=\"dot\"></span> Connected to TV</p>\n" +
            "<script>\n" +
            "function k(c) {\n" +
            "  fetch('/key?code=' + c, { method: 'GET', cache: 'no-store' })\n" +
            "    .catch(() => updateStat(false));\n" +
            "  if (navigator.vibrate) navigator.vibrate(35);\n" +
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

    public static synchronized boolean isRunning() {
        return isRunning;
    }

    public static synchronized void startServer(Activity activity) {
        if (isRunning) return;

        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                // Bind explicitly to 0.0.0.0 (all IPv4 and IPv6 interfaces)
                serverSocket.bind(new InetSocketAddress("0.0.0.0", PORT), 50);
                isRunning = true;
                Log.i(TAG, "Web Remote Server successfully listening on 0.0.0.0:" + PORT);

                while (isRunning && !serverSocket.isClosed()) {
                    Socket client = serverSocket.accept();
                    client.setSoTimeout(5000);
                    handleClient(activity, client);
                }
            } catch (Exception e) {
                if (isRunning) {
                    Log.e(TAG, "Server error on port " + PORT, e);
                }
            } finally {
                isRunning = false;
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public static synchronized void stopServer() {
        isRunning = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {}
            serverSocket = null;
        }
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread = null;
        }
        Log.i(TAG, "Web Remote Server stopped");
    }

    private static void handleClient(Activity activity, Socket client) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                 OutputStream os = client.getOutputStream()) {

                String requestLine = reader.readLine();
                if (requestLine == null) return;

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
                } else if (requestLine.startsWith("GET /ping")) {
                    String resp = "HTTP/1.1 200 OK\r\n" + corsHeaders + "Content-Type: text/plain\r\nContent-Length: 4\r\nConnection: close\r\n\r\nPONG";
                    os.write(resp.getBytes(StandardCharsets.UTF_8));
                } else if (requestLine.startsWith("GET /favicon.ico")) {
                    String resp = "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n";
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
        KEY_EXECUTOR.execute(() -> {
            try {
                Runtime.getRuntime().exec("input keyevent " + code).waitFor();
            } catch (Exception e) {
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        activity.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code));
                        activity.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code));
                    });
                }
            }
        });
    }
}
