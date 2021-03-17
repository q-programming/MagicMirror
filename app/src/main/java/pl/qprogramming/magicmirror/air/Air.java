package pl.qprogramming.magicmirror.air;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import pl.qprogramming.magicmirror.service.DataUpdater;
import pl.qprogramming.magicmirror.utils.GeoLocation;
import pl.qprogramming.magicmirror.utils.Network;
import pl.qprogramming.magicmirror.R;

/**
 * A helper class to regularly retrieve air quality information.
 */
public class Air extends DataUpdater<Air.AirData> implements Serializable{
    private static final String TAG = Air.class.getSimpleName();

    /**
     * The time in milliseconds between API calls to update the air quality.
     */
    private static final long UPDATE_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(15);

    /**
     * The base URL for all AirNow API requests.
     */
    private static final String AIR_NOW_BASE_URL = "https://www.airnowapi.org";
    private static final String AIR_BASE_URL = "https://api.airvisual.com/v2/nearest_city";

    /**
     * The context used to load string resources.
     */
    private final Context context;

    /**
     * A {@link Map} from the air quality index category the corresponding drawable resource ID.
     */
    private final Map<Integer, Integer> iconResources = new HashMap<Integer, Integer>() {{
        put(1, R.drawable.aqi_good);
        put(2, R.drawable.aqi_moderate);
        put(3, R.drawable.aqi_usg);
        put(4, R.drawable.aqi_unhealthy);
        put(5, R.drawable.aqi_very_unhealthy);
        put(6, R.drawable.aqi_hazardous);
    }};

    /**
     * The data structure containing the air quality information we are interested in.
     */
    public static class AirData implements Serializable {

        /**
         * The air quality index number.
         */
        public final int aqi;

        /**
         * The air quality index category name.
         */
        public final String category;
        public final int windDirection;
        public final Double windSpeed;
        public final AirQuality quality;

        /**
         * The air quality index category icon.
         */
        public final int icon;

        public AirData(int aqi, String category, int windDirection, Double windSpeed, AirQuality quality, int icon) {
            this.aqi = aqi;
            this.category = category;
            this.windDirection = windDirection;
            this.windSpeed = windSpeed;
            this.quality = quality;
            this.icon = icon;
        }


    }

    public enum AirQuality {
        GOOD, MEDIUM, UNHEALTHY_SENSITIVE, UNHEALTHY, VERY_UNHEALTHY
    }

    public Air(Context context, UpdateListener<AirData> updateListener) {
        super(updateListener, UPDATE_INTERVAL_MILLIS);
        this.context = context;
    }

    @Override
    protected AirData getData() {
        Location location = GeoLocation.getLocation(context);
        Log.d(TAG, "Using location for air quality: " + location);
        // Get the latest data from the AirNow API.
        try {
            String requestUrl = getRequestUrl(location);
            JSONObject response = Network.getJsonObject(requestUrl);
            if (response == null) {
                return null;
            }
            JSONObject current = response.getJSONObject("data").getJSONObject("current");
            JSONObject pollution = current.getJSONObject("pollution");
            JSONObject weather = current.getJSONObject("weather");
            int aqi = pollution.getInt("aqius");
            AirQuality airQuality = checkQuality(aqi);

            return new AirData(
                    aqi,
                    airQuality.toString(),
                    weather.getInt("wd"),
                    weather.getDouble("ws"),
                    airQuality,
                    iconResources.get(airQuality.ordinal() + 1)
            );
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse air quality JSON.", e);
            return null;
        }
    }


    private AirQuality checkQuality(int aqi) {
        if (aqi < 50) {
            return AirQuality.GOOD;
        } else if (50 < aqi && aqi <= 100) {
            return AirQuality.MEDIUM;
        } else if (100 < aqi && aqi < 151) {
            return AirQuality.UNHEALTHY_SENSITIVE;
        } else if (151 < aqi && aqi < 200) {
            return AirQuality.UNHEALTHY;
        } else {
            return AirQuality.VERY_UNHEALTHY;
        }
    }

    /**
     * Creates the URL for an AirNow API request based on the specified location or {@code null} if
     * the location is unknown.
     */
    private String getRequestUrl(Location location) {
        if (location == null) {
            return null;
        }
        //http://api.airvisual.com/v2/nearest_city?lat={{LATITUDE}}&lon={{LONGITUDE}}&key={{YOUR_API_KEY}}
        return String.format(
                Locale.FRANCE,
                "%s" +
                        "?lat=%f" +
                        "&lon=%f" +
                        "&key=%s",
                AIR_BASE_URL,
                location.getLatitude(),
                location.getLongitude(),
                context.getString(R.string.air_api_key));
    }

    @Override
    protected String getTag() {
        return TAG;
    }
}
