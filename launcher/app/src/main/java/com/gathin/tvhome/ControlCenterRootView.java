package com.gathin.tvhome;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.LinearLayout;

/**
 * Custom root container for Control Center popup that intercepts BACK key events
 * at the window dispatch level before Android's PopupDecorView can dismiss the popup.
 */
public class ControlCenterRootView extends LinearLayout {

    public interface OnBackPressedListener {
        boolean onBackPressed();
    }

    private OnBackPressedListener onBackPressedListener;

    public ControlCenterRootView(Context context) {
        super(context);
    }

    public ControlCenterRootView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ControlCenterRootView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnBackPressedListener(OnBackPressedListener listener) {
        this.onBackPressedListener = listener;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            // Consume ACTION_DOWN so PopupDecorView does not start tracking back dismiss
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (onBackPressedListener != null && onBackPressedListener.onBackPressed()) {
                    return true;
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
