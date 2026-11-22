package pl.qprogramming.magicmirror.data.weather;

import android.content.Context;
import android.location.Location;
import android.util.Log;
import android.util.LruCache;

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
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
    private final Map<Integer, Integer> accuWeatherIconMap = new HashMap<>() {{
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
    /**
     * A {@link Map} from Tomorrow.io's weather code to the corresponding drawable resource ID.
     */
    private final Map<Integer, Integer> tomorrowIoDayIconMapping = new HashMap<>() {{
        put(1000, R.drawable.ic_clear_day);  // Clear or Mostly Clear (Day)
        put(1100, R.drawable.ic_clear_day);  // Mostly Clear (Day)
        put(1101, R.drawable.ic_cloudy_1_day);  // Partly Cloudy (Day)
        put(1102, R.drawable.ic_cloudy_2_day);  // Mostly Cloudy (Day)

        put(2000, R.drawable.ic_fog);  // Fog (Day)
        put(2100, R.drawable.ic_fog);  // Light Fog (Day)

        put(4000, R.drawable.ic_rain_and_sleet_mix);  // Drizzle (Day)
        put(4001, R.drawable.ic_rainy_3);  // Rain (Day)
        put(4200, R.drawable.ic_rainy_1_day);  // Light Rain (Day)
        put(4201, R.drawable.ic_rainy_3);  // Heavy Rain (Day)

        put(5000, R.drawable.ic_snowy_3);  // Snow (Day)
        put(5001, R.drawable.ic_snowy_1_day);  // Flurries (Day)
        put(5100, R.drawable.ic_snowy_1_day);  // Light Snow (Day)
        put(5101, R.drawable.ic_snowy_3);  // Heavy Snow (Day)

        put(6000, R.drawable.ic_snow_and_sleet_mix);  // Freezing Drizzle (Day)
        put(6001, R.drawable.ic_snow_and_sleet_mix);  // Freezing Rain (Day)
        put(6200, R.drawable.ic_snow_and_sleet_mix);  // Light Freezing Rain (Day)
        put(6201, R.drawable.ic_snow_and_sleet_mix);  // Heavy Freezing Rain (Day)

        put(7000, R.drawable.ic_snowy_3);  // Ice Pellets (Day)
        put(7101, R.drawable.ic_snowy_3);  // Heavy Ice Pellets (Day)

        put(8000, R.drawable.ic_scattered_thunderstorms_day);  // Thunderstorm (Day)
    }};
    /**
     * A {@link Map} from Tomorrow.io's weather night code to the corresponding drawable resource ID.
     */
    private final Map<Integer, Integer> tomorrowIoNightIconMapping = new HashMap<>() {{
        put(1000, R.drawable.ic_clear_night);  // Clear or Mostly Clear (Night)
        put(1100, R.drawable.ic_clear_night);  // Mostly Clear (Night)
        put(1101, R.drawable.ic_cloudy_1_night);  // Partly Cloudy (Night)
        put(1102, R.drawable.ic_cloudy_2_night);  // Mostly Cloudy (Night)

        put(2000, R.drawable.ic_fog);  // Fog (Night) - use same fog icon
        put(2100, R.drawable.ic_fog);  // Light Fog (Night)

        put(4000, R.drawable.ic_rain_and_sleet_mix);  // Drizzle (Night)
        put(4001, R.drawable.ic_rainy_3_night);  // Rain (Night)
        put(4200, R.drawable.ic_rainy_3_night);  // Light Rain (Night)
        put(4201, R.drawable.ic_rainy_3_night);  // Heavy Rain (Night)

        put(5000, R.drawable.ic_snowy_3_night);  // Snow (Night)
        put(5001, R.drawable.ic_snowy_1_night);  // Flurries (Night)
        put(5100, R.drawable.ic_snowy_1_night);  // Light Snow (Night)
        put(5101, R.drawable.ic_snowy_3_night);  // Heavy Snow (Night)

        put(6000, R.drawable.ic_snow_and_sleet_mix);  // Freezing Drizzle (Night) - same as day
        put(6001, R.drawable.ic_snow_and_sleet_mix);  // Freezing Rain (Night)
        put(6200, R.drawable.ic_snow_and_sleet_mix);  // Light Freezing Rain (Night)
        put(6201, R.drawable.ic_snow_and_sleet_mix);  // Heavy Freezing Rain (Night)

        put(7000, R.drawable.ic_snowy_3_night);  // Ice Pellets (Night)
        put(7101, R.drawable.ic_snowy_3_night);  // Heavy Ice Pellets (Night)

        put(8000, R.drawable.ic_scattered_thunderstorms_night);  // Thunderstorm (Night)
    }};

    /**
     * Get the icon resource ID for the given Tomorrow.io weather code based on day or night.
     *
     * @param weatherCode The Tomorrow.io weather code (int).
     * @param isNight True if night icon should be used, false for day.
     * @return Drawable resource ID.
     */
    public int getTomorrowIoIcon(int weatherCode, boolean isNight) {
        if (isNight) {
            Integer icon = tomorrowIoNightIconMapping.get(weatherCode);
            if (icon != null) {
                return icon;
            }
        } else {
            Integer icon = tomorrowIoDayIconMapping.get(weatherCode);
            if (icon != null) {
                return icon;
            }
        }

        // Fallback icon if code not found
        return isNight ? R.drawable.ic_clear_night : R.drawable.ic_clear_day;
    }


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

    /**
     * Leaving for potential future use
     * @return Weather data from AccuWeather
     */
    @Deprecated
    protected WeatherData getAccuWeatherData() {
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
                    accuWeatherIconMap.get(currentIcon),
                    accuWeatherIconMap.get(day2Icon),
                    accuWeatherIconMap.get(day3Icon),
                    accuWeatherIconMap.get(day4Icon)
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse weather JSON.", e);
            return null;
        }
    }

    // Constructs the Tomorrow.io realtime weather API URL
    private String getTomorrowIoRealtimeUrl(double lat, double lon) {
        return String.format(Locale.US,
                "https://api.tomorrow.io/v4/timelines?location=%f,%f&fields=temperature,precipitationProbability,weatherCode&timesteps=current&units=metric&apikey=%s",
                lat, lon, weatherKey);
    }

    // Constructs the Tomorrow.io 5-day forecast API URL
    private String getTomorrowIo5DayForecastUrl(double lat, double lon) {
        return String.format(Locale.US,
                "https://api.tomorrow.io/v4/timelines?location=%f,%f&fields=temperatureMin,temperatureMax,weatherCode&timesteps=1d&startTime=now&endTime=nowPlus5d&units=metric&apikey=%s",
                lat, lon, weatherKey);
    }

    // Helper: gets icon for forecast day (0-based index) from forecast data
    private int getIconForForecastDay(JSONArray dailyIntervals, int dayIndex) throws JSONException {
        if (dailyIntervals.length() <= dayIndex) {
            // Fallback icon
            return R.drawable.ic_clear_day;
        }
        JSONObject values = dailyIntervals.getJSONObject(dayIndex).getJSONObject("values");
        int weatherCode = values.getInt("weatherCode");
        // Default to day icons for forecast
        return tomorrowIoDayIconMapping.getOrDefault(weatherCode, R.drawable.ic_clear_day);
    }

    private boolean isNightTime(double lat, double lon) {
        val libraryLocation =
                new com.luckycatlabs.sunrisesunset.dto.Location(
                        String.valueOf(lat),
                        String.valueOf(lon)
                );
        Calendar today = Calendar.getInstance();
        val calculator = new SunriseSunsetCalculator(libraryLocation, TimeZone.getDefault().getID());
        val officialSunrise = calculator.getOfficialSunriseCalendarForDate(today);
        val officialSunset = calculator.getOfficialSunsetCalendarForDate(today);
        if (officialSunrise == null || officialSunset == null) {
            Log.w(TAG, "Could not calculate sunrise/sunset, falling back to hour-based check.");
            Calendar cal = Calendar.getInstance();
            int hourOfDay = cal.get(Calendar.HOUR_OF_DAY);
            return (hourOfDay >= 19 || hourOfDay < 6);
        }
        return today.after(officialSunset) || today.before(officialSunrise);
    }

    @Override
    protected WeatherData getData() {
        Location location = GeoLocation.getLocation(context);
        if (location == null) {
            Log.e(TAG, "Location not available");
            return null;
        }

        double lat = location.getLatitude();
        double lon = location.getLongitude();

        try {
            // Build request URLs for Tomorrow.io
            String realtimeUrl = getTomorrowIoRealtimeUrl(lat, lon);
            String forecastUrl = getTomorrowIo5DayForecastUrl(lat, lon);

            JSONObject realtimeResponse = Network.getJsonObject(realtimeUrl);
            if (realtimeResponse == null || !realtimeResponse.has("data")) {
                Log.e(TAG, "Realtime weather data not available");
                return null;
            }

            JSONObject forecastResponse = Network.getJsonObject(forecastUrl);
            if (forecastResponse == null || !forecastResponse.has("data")) {
                Log.e(TAG, "Forecast weather data not available");
                return null;
            }

            // Parse current data
            JSONObject currentValues = realtimeResponse
                    .getJSONObject("data")
                    .getJSONArray("timelines")
                    .getJSONObject(0)
                    .getJSONArray("intervals")
                    .getJSONObject(0)
                    .getJSONObject("values");

            double currentTemp = currentValues.getDouble("temperature");
            double precipitationProbability = currentValues.has("precipitationProbability")
                    ? currentValues.getDouble("precipitationProbability")
                    : 0.0;

            int currentWeatherCode = currentValues.getInt("weatherCode");
            boolean isNight = isNightTime(lat,lon);
            int currentIcon = getTomorrowIoIcon(currentWeatherCode, isNight);

            // Parse 5-day daily forecast data
            JSONArray dailyIntervals = forecastResponse
                    .getJSONObject("data")
                    .getJSONArray("timelines")
                    .getJSONObject(0)
                    .getJSONArray("intervals");

            // For minimum and maximum temperatures of the day 1 (today)
            JSONObject day1Values = dailyIntervals.getJSONObject(0).getJSONObject("values");
            double minTemp = day1Values.getDouble("temperatureMin");
            double maxTemp = day1Values.getDouble("temperatureMax");

            // For example, you can create a summary from weather codes or descriptions manually.
            // Alternatively, use the first day's weatherCode to determine a simple summary.
            // Here we keep a placeholder.

            // Icons for the next 3 days (day 2 to day 4)
            int day2Icon = getIconForForecastDay(dailyIntervals, 1);
            int day3Icon = getIconForForecastDay(dailyIntervals, 2);
            int day4Icon = getIconForForecastDay(dailyIntervals, 3);

            return new WeatherData(
                    currentTemp,
                    minTemp,
                    maxTemp,
                    null,
                    precipitationProbability,
                    currentIcon,
                    day2Icon,
                    day3Icon,
                    day4Icon
            );

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse Tomorrow.io weather JSON.", e);
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
