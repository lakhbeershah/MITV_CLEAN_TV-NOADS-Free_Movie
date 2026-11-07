package com.gathin.tvhome;

import android.app.Activity;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Super-lightweight HTTP web server running on port 8080.
 * Allows controlling the TV from any mobile phone or browser over Wi-Fi without installing an app.
 */
public class WebRemoteServer {

    private static final String TAG = "WebRemoteServer";
    public static final int PORT = 8080;

    private static ServerSocket serverSocket;
    private static Thread serverThread;
    private static boolean isRunning = false;

    private static final String HTML_PAGE = "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "<meta charset=\"utf-8\">\n" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, user-scalable=no\">\n" +
            "<title>Xiaomi TV Remote</title>\n" +
            "<style>\n" +
            "* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; user-select: none; }\n" +
            "body { background: #0E1116; color: #FFFFFF; font-family: -apple-system, sans-serif; text-align: center; margin: 0; padding: 24px 16px; }\n" +
            "h2 { font-size: 20px; font-weight: 600; margin-bottom: 24px; color: #E0E0E0; letter-spacing: 0.5px; }\n" +
            ".dpad { width: 260px; height: 260px; margin: 0 auto; position: relative; background: #1B2028; border-radius: 50%; box-shadow: 0 8px 24px rgba(0,0,0,0.5); }\n" +
            ".btn { background: #262D38; color: #FFFFFF; border: none; outline: none; border-radius: 14px; font-size: 20px; font-weight: bold; cursor: pointer; transition: background 0.1s; }\n" +
            ".btn:active { background: #00E676; color: #000; transform: scale(0.95); }\n" +
            ".d-up { position: absolute; top: 12px; left: 85px; width: 90px; height: 70px; border-radius: 40px 40px 10px 10px; }\n" +
            ".d-down { position: absolute; bottom: 12px; left: 85px; width: 90px; height: 70px; border-radius: 10px 10px 40px 40px; }\n" +
            ".d-left { position: absolute; top: 85px; left: 12px; width: 70px; height: 90px; border-radius: 40px 10px 10px 40px; }\n" +
            ".d-right { position: absolute; top: 85px; right: 12px; width: 70px; height: 90px; border-radius: 10px 40px 40px 10px; }\n" +
            ".d-ok { position: absolute; top: 85px; left: 85px; width: 90px; height: 90px; border-radius: 50%; background: #00E676; color: #000; font-size: 18px; box-shadow: 0 0 16px rgba(0,230,118,0.4); }\n" +
            ".d-ok:active { background: #00C853; transform: scale(0.92); }\n" +
            ".nav-grid { display: flex; justify-content: center; gap: 14px; margin-top: 32px; }\n" +
            ".nav-btn { width: 78px; height: 54px; font-size: 14px; background: #1B2028; border: 1px solid #2B3340; border-radius: 14px; }\n" +
            ".status { font-size: 12px; color: #00E676; margin-top: 24px; }\n" +
            "</style>\n" +
            "</head>\n" +
            "<body>\n" +
            "<h2>📺 Xiaomi TV Remote</h2>\n" +
            "<div class=\"dpad\">\n" +
            "  <button class=\"btn d-up\" onclick=\"k(19)\">▲</button>\n" +
            "  <button class=\"btn d-left\" onclick=\"k(21)\">◀</button>\n" +
            "  <button class=\"btn d-ok\" onclick=\"k(23)\">OK</button>\n" +
            "  <button class=\"btn d-right\" onclick=\"k(22)\">▶</button>\n" +
            "  <button class=\"btn d-down\" onclick=\"k(20)\">▼</button>\n" +
            "</div>\n" +
            "<div class=\"nav-grid\">\n" +
            "  <button class=\"btn nav-btn\" onclick=\"k(4)\">↩ Back</button>\n" +
            "  <button class=\"btn nav-btn\" onclick=\"k(3)\">🏠 Home</button>\n" +
            "</div>\n" +
            "<div class=\"nav-grid\">\n" +
            "  <button class=\"btn nav-btn\" onclick=\"k(25)\">🔉 Vol −</button>\n" +
            "  <button class=\"btn nav-btn\" onclick=\"k(24)\">🔊 Vol +</button>\n" +
            "</div>\n" +
            "<p class=\"status\">● Connected to TV</p>\n" +
            "<script>\n" +
            "function k(c) {\n" +
            "  fetch('/key?code=' + c);\n" +
            "  if (navigator.vibrate) navigator.vibrate(35);\n" +
            "}\n" +
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
                serverSocket = new ServerSocket(PORT);
                isRunning = true;
                Log.i(TAG, "Web Remote Server started on port " + PORT);

                while (isRunning && !serverSocket.isClosed()) {
                    Socket client = serverSocket.accept();
                    handleClient(activity, client);
                }
            } catch (Exception e) {
                if (isRunning) {
                    Log.e(TAG, "Server error", e);
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

                if (requestLine.startsWith("GET /key?code=")) {
                    int idx = requestLine.indexOf("code=");
                    int end = requestLine.indexOf(" ", idx);
                    if (end == -1) end = requestLine.length();
                    String codeStr = requestLine.substring(idx + 5, end);
                    try {
                        int code = Integer.parseInt(codeStr);
                        sendKeyEvent(activity, code);
                    } catch (Exception ignored) {}

                    String resp = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK";
                    os.write(resp.getBytes(StandardCharsets.UTF_8));
                } else {
                    // Serve HTML page
                    byte[] bytes = HTML_PAGE.getBytes(StandardCharsets.UTF_8);
                    String header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: "
                            + bytes.length + "\r\nConnection: close\r\n\r\n";
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
        new Thread(() -> {
            try {
                Runtime.getRuntime().exec("input keyevent " + code);
            } catch (Exception e) {
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        activity.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code));
                        activity.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code));
                    });
                }
            }
        }).start();
    }
}
