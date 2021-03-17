package pl.qprogramming.magicmirror.utils;

import android.view.View;

public abstract class DoubleClickListener implements View.OnClickListener {
    private static final long DOUBLE_CLICK_TIME_DELTA = 500;//milliseconds

    long lastClickTime = 0;

    @Override
    public void onClick(View v) {
        long clickTime = System.currentTimeMillis();
        if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
            onDoubleClick(v);
        }
        lastClickTime = clickTime;
    }

    public abstract void onDoubleClick(View v);
}
