package pl.qprogramming.magicmirror;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import lombok.SneakyThrows;
import pl.qprogramming.magicmirror.utils.GeoLocation;

/**
 * The main {@link Activity} class and entry point into the UI.
 */
public class HomeActivity extends Activity {
    private static final String TAG = Activity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Arrays.asList(Manifest.permission.READ_CALENDAR, Manifest.permission.ACCESS_FINE_LOCATION).forEach(this::checkPermission);
    }

    @SneakyThrows
    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "Dreamservice is starting from activity");
        Intent intentDream = new Intent(Intent.ACTION_MAIN);
        intentDream.setClassName("com.android.systemui", "com.android.systemui.Somnambulator");
        startActivity(intentDream);
        Log.d(TAG, "Killing launcher app, bye bye");
        finish();
    }

    private void checkPermission(String permission) {
        int permissionCheck = ContextCompat.checkSelfPermission(
                this, permission);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            requestPermission(permission, permission.length());
        }
    }

    private void requestPermission(String permissionName, int permissionRequestCode) {
        ActivityCompat.requestPermissions(this,
                new String[]{permissionName}, permissionRequestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        if (requestCode == Manifest.permission.READ_CALENDAR.length()) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkPermission(Manifest.permission.ACCESS_FINE_LOCATION);
            } else {
                Toast.makeText(getApplicationContext(), R.string.calendar_permission_denied, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == Manifest.permission.ACCESS_FINE_LOCATION.length()) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                GeoLocation.clearCache();
                checkPermission(Manifest.permission.READ_CALENDAR);
            } else {
                Toast.makeText(getApplicationContext(), R.string.location_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
