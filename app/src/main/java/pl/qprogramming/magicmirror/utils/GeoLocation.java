package pl.qprogramming.magicmirror.utils;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import org.json.JSONObject;

import androidx.core.app.ActivityCompat;

/**
 * A helper class to look up location by IP.
 */
public abstract class GeoLocation {
    private static final String TAG = GeoLocation.class.getSimpleName();

    /**
     * Free geo IP endpoints, tried in order until one succeeds.
     */
    private static final String[] GEO_IP_URLS = {
            "https://get.geojs.io/v1/ip/geo.json",
            "https://ipinfo.io/json",
            "https://ipapi.co/json/"
    };
    private static final String PREFS = "geo_location";
    private static final String PREF_LAT = "lat";
    private static final String PREF_LON = "lon";
    private static final String PREF_TIME = "time";
    private static final long IP_CACHE_MILLIS = 24L * 60 * 60 * 1000;

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
            return guessBasedOnIp(context);
        }
        try {
            gps_loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            network_loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            location = gps_loc == null ? network_loc : gps_loc;
            location = location == null ? guessBasedOnIp(context) : location;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get location", e);
            return guessBasedOnIp(context);
        }
        if (location == null) {
            Log.e(TAG, "Unable to determine location");
            return null;
        }
        Log.d(TAG, "Using location: " + location);

        // Populate the cache.
        cachedLocation = location;
        return location;
    }

    private static Location guessBasedOnIp(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long savedAt = prefs.getLong(PREF_TIME, 0);
        boolean hasSaved = savedAt > 0;
        if (hasSaved && System.currentTimeMillis() - savedAt < IP_CACHE_MILLIS) {
            return cache(fromPrefs(prefs));
        }
        for (String url : GEO_IP_URLS) {
            Location location = lookup(url);
            if (location != null) {
                prefs.edit()
                        .putLong(PREF_TIME, System.currentTimeMillis())
                        .putString(PREF_LAT, String.valueOf(location.getLatitude()))
                        .putString(PREF_LON, String.valueOf(location.getLongitude()))
                        .apply();
                return cache(location);
            }
        }
        // All providers failed, a stale saved location is better than none.
        return hasSaved ? cache(fromPrefs(prefs)) : null;
    }

    private static Location cache(Location location) {
        cachedLocation = location;
        return location;
    }

    private static Location fromPrefs(SharedPreferences prefs) {
        Location location = new Location("");
        location.setLatitude(Double.parseDouble(prefs.getString(PREF_LAT, "0")));
        location.setLongitude(Double.parseDouble(prefs.getString(PREF_LON, "0")));
        return location;
    }

    private static Location lookup(String url) {
        try {
            String response = Network.get(url);
            if (response == null) {
                Log.w(TAG, "Empty response from " + url);
                return null;
            }
            JSONObject json = new JSONObject(response);
            double latitude;
            double longitude;
            if (json.has("latitude")) {
                latitude = json.getDouble("latitude");
                longitude = json.getDouble("longitude");
            } else {
                // ipinfo.io format: "lat,lon"
                String[] loc = json.getString("loc").split(",");
                latitude = Double.parseDouble(loc[0]);
                longitude = Double.parseDouble(loc[1]);
            }
            Location location = new Location("");
            location.setLatitude(latitude);
            location.setLongitude(longitude);
            Log.d(TAG, "Using location from " + url + ": " + location);
            return location;
        } catch (Exception e) {
            Log.w(TAG, "Geo IP lookup failed for " + url, e);
            return null;
        }
    }

    public static void clearCache() {
        cachedLocation = null;
    }

}
