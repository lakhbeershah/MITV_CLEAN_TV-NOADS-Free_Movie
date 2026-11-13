package com.clean.browser;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.WebView;

/**
 * Custom WebView that ensures media decoding and audio rendering continue
 * seamlessly in the background when the user presses HOME or switches apps.
 */
public class CleanTvWebView extends WebView {

    private boolean keepBackgroundMediaActive = true;

    public CleanTvWebView(Context context) {
        super(context);
    }

    public CleanTvWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CleanTvWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setKeepBackgroundMediaActive(boolean keep) {
        this.keepBackgroundMediaActive = keep;
    }

    public boolean isKeepBackgroundMediaActive() {
        return keepBackgroundMediaActive;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        // Prevent Chromium's native media engine from receiving View.GONE when HOME is pressed.
        // Passing View.VISIBLE keeps the WebContents media renderer active in the background.
        if (visibility != View.VISIBLE && keepBackgroundMediaActive) {
            super.onWindowVisibilityChanged(View.VISIBLE);
            return;
        }
        super.onWindowVisibilityChanged(visibility);
    }

    @Override
    public void dispatchWindowVisibilityChanged(int visibility) {
        if (visibility != View.VISIBLE && keepBackgroundMediaActive) {
            super.dispatchWindowVisibilityChanged(View.VISIBLE);
            return;
        }
        super.dispatchWindowVisibilityChanged(visibility);
    }
}
