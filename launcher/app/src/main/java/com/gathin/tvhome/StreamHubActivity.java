package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Leanback launcher entry point for the TV Stream & Play Deck.
 * When clicked from the App Drawer or Home Dock, directly launches TV Bro
 * pointing to the local Stream Hub.
 */
public class StreamHubActivity extends Activity {

    public static final String STREAM_HUB_URL = "http://127.0.0.1:8080/stream-hub";
    public static final String TV_BRO_PACKAGE = "com.phlox.tvwebbrowser";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ensure WebRemoteServer is active to serve the Stream Hub HTML locally
        if (!WebRemoteServer.isRunning()) {
            WebRemoteServer.startServer(this);
        }

        Uri uri = Uri.parse(STREAM_HUB_URL);

        // Attempt 1: Direct launch into TV Bro
        Intent tvBroIntent = new Intent(Intent.ACTION_VIEW, uri);
        tvBroIntent.setPackage(TV_BRO_PACKAGE);
        tvBroIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(tvBroIntent);
            finish();
            return;
        } catch (Exception ignored) {}

        // Attempt 2: Fallback to any installed browser
        try {
            Intent genericBrowser = new Intent(Intent.ACTION_VIEW, uri);
            genericBrowser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(genericBrowser);
        } catch (Exception e) {
            Toast.makeText(this, "TV Bro browser is not installed", Toast.LENGTH_SHORT).show();
        }

        finish();
    }
}
