package pl.qprogramming.magicmirror.data.weather;

import android.content.Context;
import android.location.Location;
import android.util.Log;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.AllArgsConstructor;
import lombok.val;
import pl.qprogramming.magicmirror.R;
import pl.qprogramming.magicmirror.service.DataUpdater;
import pl.qprogramming.magicmirror.utils.GeoLocation;
import pl.qprogramming.magicmirror.utils.Network;

/**
 * A helper class to regularly retrieve weather information.
 */
public class Weather extends DataUpdater<Weather.WeatherData> {
    private static final String TAG = Weather.class.getSimpleName();

    /**
     * The time in milliseconds between API calls to update the weather.
     */
    private static final long UPDATE_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(1);

    /**
     * The base URL for all AccuWeather API requests.
     */
    private static final String ACCU_WEATHER_BASE_URL = "https://dataservice.accuweather.com";

    /**
     * The size of the location key cache. Should be big enough to cover all typical locations.
     */
    private static final int LOCATION_KEY_CACHE_SIZE = 10;

    /**
     * The context used to load string resources.
     */
    private final Context context;
    private String weatherKey;

    /**
     * A cache for the location key to avoid unnecessary API requests.
     */
    private final LruCache<Location, String> locationKeyCache = new LruCache<>(LOCATION_KEY_CACHE_SIZE);

    /**
     * A {@link Map} from AccuWeather's icon number to the corresponding drawable resource ID.
     * See: https://developer.accuweather.com/weather-icons
     */
    private final Map<Integer, Integer> iconResources = new HashMap<Integer, Integer>() {{
        put(1, R.drawable.ic_clear_day);  // Sunny
        put(2, R.drawable.ic_clear_day);  // Mostly Sunny
        put(3, R.drawable.ic_cloudy_1_day);  // Partly Sunny
        put(4, R.drawable.ic_cloudy_2_day);  // Intermittent Clouds
        put(5, R.drawable.ic_cloudy_2_day);  // Hazy Sunshine
        put(6, R.drawable.ic_cloudy);  // Mostly Cloudy
        put(7, R.drawable.ic_cloudy);  // Cloudy
        put(8, R.drawable.ic_cloudy);  // Dreary (Overcast)
        put(11, R.drawable.ic_fog);  // Fog
        put(12, R.drawable.ic_rain_and_sleet_mix);  // Showers
        put(13, R.drawable.ic_rainy_2_day);  // Mostly Cloudy w/ Showers
        put(14, R.drawable.ic_rainy_1_day);  // Partly Sunny w/ Showers
        put(15, R.drawable.ic_scattered_thunderstorms);  // T-Storms
        put(16, R.drawable.ic_scattered_thunderstorms_day);  // Mostly Cloudy w/ T-Storms
        put(17, R.drawable.ic_scattered_thunderstorms_day);  // Partly Sunny w/ T-Storms
        put(18, R.drawable.ic_rainy_3);  // Rain
        put(19, R.drawable.ic_snow_and_sleet_mix);  // Flurries
        put(20, R.drawable.ic_snowy_1_day);  // Mostly Cloudy w/ Flurries
        put(21, R.drawable.ic_snowy_1_day);  // Partly Sunny w/ Flurries
        put(22, R.drawable.ic_snowy_3);  // Snow
        put(23, R.drawable.ic_snowy_3);  // Mostly Cloudy w/ Snow
        put(24, R.drawable.ic_snowy_3);  // Ice
        put(25, R.drawable.ic_snowy_3);  // Sleet
        put(26, R.drawable.ic_snow_and_sleet_mix);  // Freezing Rain
        put(29, R.drawable.ic_snow_and_sleet_mix);  // Rain and Snow
        put(30, R.drawable.ic_hot);  // Hot
        put(31, R.drawable.ic_cold);  // Cold
        put(32, R.drawable.ic_wind);  // Windy
        put(33, R.drawable.ic_clear_night);  // Clear
        put(34, R.drawable.ic_clear_night);  // Mostly Clear
        put(35, R.drawable.ic_cloudy_1_night);  // Partly Cloudy
        put(36, R.drawable.ic_cloudy_2_night);  // Intermittent Clouds
        put(37, R.drawable.partly_cloudy_night);  // Hazy Moonlight
        put(38, R.drawable.ic_cloudy_3_night);  // Mostly Cloudy
        put(39, R.drawable.ic_rainy_3_night);  // Partly Cloudy w/ Showers
        put(40, R.drawable.ic_rainy_3_night);  // Mostly Cloudy w/ Showers
        put(41, R.drawable.ic_scattered_thunderstorms_night);  // Partly Cloudy w/ T-Storms
        put(42, R.drawable.ic_scattered_thunderstorms_night);  // Mostly Cloudy w/ T-Storms
        put(43, R.drawable.ic_snowy_1_night);  // Mostly Cloudy w/ Flurries
        put(44, R.drawable.ic_snowy_3_night);  // Mostly Cloudy w/ Snow
    }};

    public void updateNow(String weatherKey) {
        this.weatherKey = weatherKey;
        updateNow();
    }

    /**
     * The data structure containing the weather information we are interested in.
     */
    @AllArgsConstructor
    public static class WeatherData implements Serializable {

        /**
         * The current temperature in degrees Fahrenheit.
         */
        public final double currentTemperature;
        public final double minimumTemperature;
        public final double maximumTemperature;

        /**
         * A human-readable summary of the 24-hour forecast.
         */
        public final String forecastSummary;

        /**
         * The average precipitation probability during the 24-hour forecast as a value between 0 and 1.
         */
        public final double precipitationProbability;

        /**
         * The resource ID of the icon representing the current weather conditions.
         */
        public final int currentIcon;
        public final int day2Icon;
        public final int day3icon;
        public final int day4icon;


    }

    public Weather(Context context, UpdateListener<WeatherData> updateListener, String weatherKey) {
        super(updateListener, UPDATE_INTERVAL_MILLIS);
        this.context = context;
        this.weatherKey = weatherKey;
    }

    @Override
    protected WeatherData getData() {
        Location location = GeoLocation.getLocation(context);

        // Convert the location to a location key required by the API requests.
        String locationKey = getLocationKey(location);
        // Get the latest data from the AccuWeather API.
        try {
            String currentRequestUrl = getCurrentRequestUrl(locationKey);
            String fiveDayForecastRequestUrl = get5DayForecastRequestUrl(locationKey);
            JSONArray currentResponse = Network.getJsonArray(currentRequestUrl);
            if (currentResponse == null) {
                return null;
            }
            //uncomment for sample data
            //val inputStream = context.getResources().openRawResource(R.raw.weather_data);
            //byte[] buffer = new byte[inputStream.available()];
            //inputStream.read(buffer);
            //inputStream.close();
            //String json = new String(buffer, "UTF-8");
            //JSONObject fiveDayForecastResponse = new JSONObject(json);
            JSONObject fiveDayForecastResponse = Network.getJsonObject(fiveDayForecastRequestUrl);
            if (fiveDayForecastResponse == null) {
                return null;
            }
            Log.d(TAG,fiveDayForecastResponse.toString());

            // Parse the data we are interested in from the response JSON.
            double currentTemperature = currentResponse
                    .getJSONObject(0)
                    .getJSONObject("Temperature")
                    .getJSONObject("Metric")
                    .getDouble("Value");
//            double currentTemperature = 20;
            String forecastSummary = fiveDayForecastResponse
                    .getJSONObject("Headline")
                    .getString("Text");
            double dayPrecipitationProbability = fiveDayForecastResponse
                    .getJSONArray("DailyForecasts")
                    .getJSONObject(0)
                    .getJSONObject("Day")
                    .getInt("PrecipitationProbability");
            double nightPrecipitationProbability = fiveDayForecastResponse
                    .getJSONArray("DailyForecasts")
                    .getJSONObject(0)
                    .getJSONObject("Night")
                    .getInt("PrecipitationProbability");
            double precipitationProbability =
                    (dayPrecipitationProbability + nightPrecipitationProbability) / 2;
            int currentIcon = currentResponse
                    .getJSONObject(0)
                    .getInt("WeatherIcon");
//            int currentIcon = 3;
            double minimum = fiveDayForecastResponse
                    .getJSONArray("DailyForecasts")
                    .getJSONObject(0)
                    .getJSONObject("Temperature")
                    .getJSONObject("Minimum")
                    .getInt("Value");
            double maximum = fiveDayForecastResponse
                    .getJSONArray("DailyForecasts")
                    .getJSONObject(0)
                    .getJSONObject("Temperature")
                    .getJSONObject("Maximum")
                    .getInt("Value");
            int day2Icon = getDayIcon(fiveDayForecastResponse, 1);
            int day3Icon = getDayIcon(fiveDayForecastResponse, 2);
            int day4Icon = getDayIcon(fiveDayForecastResponse, 3);
            return new WeatherData(
                    currentTemperature,
                    minimum,
                    maximum,
                    forecastSummary,
                    precipitationProbability,
                    iconResources.get(currentIcon),
                    iconResources.get(day2Icon),
                    iconResources.get(day3Icon),
                    iconResources.get(day4Icon)
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse weather JSON.", e);
            return null;
        }
    }

    private int getDayIcon(JSONObject fiveDayForecastResponse, int day) throws JSONException {
        val dayForecast = fiveDayForecastResponse.getJSONArray("DailyForecasts").getJSONObject(day).getJSONObject("Day");
        val icon = dayForecast.getInt("Icon");
        val rainChance = dayForecast.getInt("RainProbability");
        return rainChance > 50 ? 12 : icon;
    }


    /**
     * Retrieves the location key for a particular latitude and longitude or uses a cached version or
     * returns {@code null} if the request fails.
     */
    private String getLocationKey(Location location) {
        if (location == null) {
            return null;
        }
        // Try the cache first.
        String cachedLocationKey = locationKeyCache.get(location);
        if (cachedLocationKey != null) {
            Log.d(TAG, String.format("Using cached location key: %s -> %s", location, cachedLocationKey));
            return cachedLocationKey;
        }
        Log.d(TAG, "Requesting location key.");
        String requestUrl = String.format(
                Locale.FRANCE,
                "%s/locations/v1/cities/geoposition/search?apikey=%s&q=%f,%f",
                ACCU_WEATHER_BASE_URL,
                weatherKey,
                location.getLatitude(),
                location.getLongitude());
        try {
            JSONObject response = Network.getJsonObject(requestUrl);
            if (response == null) {
                return null;
            }
            String locationKey = response.getString("Key");
            Log.d(TAG, "Using location key: " + locationKey);
            locationKeyCache.put(location, locationKey);
            return locationKey;
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse location key JSON.", e);
            return null;
        }
    }

    /**
     * Creates the URL for an AccuWeather API request for the current conditions based on the
     * specified location key or {@code null} if the location is unknown.
     */
    private String getCurrentRequestUrl(String locationKey) {
        if (locationKey == null) {
            return null;
        }

        return String.format(
                Locale.US,
                "%s/currentconditions/v1/%s?apikey=%s",
                ACCU_WEATHER_BASE_URL,
                locationKey,
                weatherKey);
    }

    /**
     * Creates the URL for an AccuWeather API request for the daily forecast based on the specified
     * location key or {@code null} if the location is unknown.
     */
    private String getForecastRequestUrl(String locationKey) {
        if (locationKey == null) {
            return null;
        }

        return String.format(
                Locale.US,
                "%s/forecasts/v1/daily/1day/%s?apikey=%s&details=true&language=pl&metric=true",
                ACCU_WEATHER_BASE_URL,
                locationKey,
                weatherKey);
    }

    /**
     * Creates the URL for an AccuWeather API request for the 5 days forecast based on the specified
     * location key or {@code null} if the location is unknown.
     */
    private String get5DayForecastRequestUrl(String locationKey) {
        if (locationKey == null) {
            return null;
        }

        return String.format(
                Locale.US,
                "%s/forecasts/v1/daily/5day/%s?apikey=%s&details=true&language=pl&details=true&metric=true",
                ACCU_WEATHER_BASE_URL,
                locationKey,
                weatherKey);
    }

    @Override
    protected String getTag() {
        return TAG;
    }
}
