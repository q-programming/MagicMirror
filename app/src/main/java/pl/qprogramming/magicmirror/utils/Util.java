package pl.qprogramming.magicmirror.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import androidx.core.content.res.ResourcesCompat;
import lombok.val;
import pl.qprogramming.magicmirror.R;

/**
 * Utility methods.
 */
public class Util {
    private final Context context;

    public Util(Context context) {
        this.context = context;
    }

    /**
     * Ensures that the navigation bar is hidden.
     */
    public void hideNavigationBar(View view) {
//        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    /**
     * Launches the system's default settings activity.
     */
    public void launchSettings() {
        Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
        context.startActivity(settingsIntent);
    }

    /**
     * Shows a {@link Toast} with the IPv4 address of the Wifi connection. Useful for debugging,
     * especially when using adb over Wifi.
     */
    public void showIpAddress() {
        Context appContext = context.getApplicationContext();
        WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String ipAddress = null;
        if (wifiInfo != null) {
            ipAddress = Formatter.formatIpAddress(wifiInfo.getIpAddress());
        }
        if (ipAddress == null) {
            ipAddress = context.getString(R.string.unknown_ip_address);
        }
        Toast.makeText(context, ipAddress, Toast.LENGTH_LONG).show();
    }

    /**
     * Uses some standard button presses for easy debugging.
     */
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
                launchSettings();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                showIpAddress();
                return true;
            default:
                return false;
        }
    }

    /**
     * Removes the period from the end of a sentence, if there is one.
     */
    public String stripPeriod(String sentence) {
        if (sentence == null) {
            return null;
        }
        if ((sentence.length() > 0) && (sentence.charAt(sentence.length() - 1) == '.')) {
            return sentence.substring(0, sentence.length() - 1);
        } else {
            return sentence;
        }
    }

    public BitmapDrawable writeOnDrawable(int drawableId, String text) {
        val drawable = ResourcesCompat.getDrawable(context.getResources(), drawableId, null);
        val bm = drawableToBitmap(drawable);
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(14);
        Canvas canvas = new Canvas(bm);
        canvas.drawText(text, 11, (bm.getHeight() / 2) + 12, paint);
        return new BitmapDrawable(context.getResources(), bm);
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public static<T> List<T> reverseList(List<T> list)
    {
        return IntStream.range(0, list.size())
                .map(i -> (list.size() - 1 - i))    // IntStream
                .mapToObj(list::get)                // Stream<T>
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
