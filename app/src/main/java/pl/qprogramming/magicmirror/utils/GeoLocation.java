package pl.qprogramming.magicmirror.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import androidx.core.app.ActivityCompat;

/**
 * A helper class to look up location by IP.
 */
public abstract class GeoLocation {
    private static final String TAG = GeoLocation.class.getSimpleName();

    /**
     * The URL of the geo location API endpoint.
     */
    private static final String GEO_IP_URL = "https://ipwhois.app/json/";

    /**
     * The location cached at the last request. Assumed to be static.
     */
    private static Location cachedLocation;


    /**
     * Makes a request to the geo location API and returns the current location or {@code null} on
     * error. Uses an in memory cache after the first request.
     *
     * @param context
     */
    public static Location getLocation(Context context) {
        Location gps_loc;
        Location network_loc;
        Location location;
        // Always use the cache, if possible.
        if (cachedLocation != null) {
            return cachedLocation;
        }
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Fine location is disabled!");
            return guessBasedOnIp();
        }
        try {
            gps_loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            network_loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            location = gps_loc == null ? network_loc : gps_loc;
            location = location == null ? guessBasedOnIp() : location;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get location", e);
            return guessBasedOnIp();
        }
        double longitude = Objects.requireNonNull(location).getLongitude();
        double latitude = location.getLatitude();
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        Log.d(TAG, "Using location: " + location);

        // Populate the cache.
        cachedLocation = location;
        return location;
    }

    private static Location guessBasedOnIp() {
        String response = Network.get(GEO_IP_URL);
        if (response == null) {
            Log.e(TAG, "Empty response.");
            return null;
        }
        // Parse the latitude and longitude from the response JSON.
        try {
            JSONObject responseJson = new JSONObject(response);
            double latitude = responseJson.getDouble("latitude");
            double longitude = responseJson.getDouble("longitude");
            Location location = new Location("");
            location.setLatitude(latitude);
            location.setLongitude(longitude);
            Log.d(TAG, "Using location: " + location);

            // Populate the cache.
            cachedLocation = location;
            return location;
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse geo location JSON.");
            return null;
        }
    }

    public static void clearCache() {
        cachedLocation = null;
    }

}
