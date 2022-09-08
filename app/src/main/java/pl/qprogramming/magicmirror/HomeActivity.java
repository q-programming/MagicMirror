package pl.qprogramming.magicmirror;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import lombok.val;
import pl.qprogramming.magicmirror.air.Air;
import pl.qprogramming.magicmirror.bus.Bus;
import pl.qprogramming.magicmirror.events.Events;
import pl.qprogramming.magicmirror.service.DataContainer;
import pl.qprogramming.magicmirror.service.DataService;
import pl.qprogramming.magicmirror.service.EventType;
import pl.qprogramming.magicmirror.utils.DoubleClickListener;
import pl.qprogramming.magicmirror.utils.GeoLocation;
import pl.qprogramming.magicmirror.utils.Util;
import pl.qprogramming.magicmirror.weather.Weather;

import static pl.qprogramming.magicmirror.events.EventsId.EVENT_DAY_VIEW_IDS;
import static pl.qprogramming.magicmirror.events.EventsId.EVENT_DAY_VIEW_IDS_O;
import static pl.qprogramming.magicmirror.events.EventsId.EVENT_TIME_VIEW_IDS;
import static pl.qprogramming.magicmirror.events.EventsId.EVENT_TIME_VIEW_IDS_O;
import static pl.qprogramming.magicmirror.events.EventsId.EVENT_TITLE_VIEW_IDS;
import static pl.qprogramming.magicmirror.events.EventsId.EVENT_TITLE_VIEW_IDS_O;

/**
 * The main {@link Activity} class and entry point into the UI.
 */
public class HomeActivity extends Activity {

    public static final int TOGGLE_PERIOD = 1;

    private final ScheduledExecutorService scheduledBackgroundExecutor =
            Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> toggleTask;
    private boolean toggleView;

    private TextView temperatureView;
    private TextView weatherSummaryView;
    private TextView precipitationView;
    private TextView airQualityView;
    private ImageView iconView;
    private TextView windSpeedView;
    private ImageView windDirectionView;
    private final Events.EventsViewsWrapper[] eventViews = new Events.EventsViewsWrapper[EVENT_DAY_VIEW_IDS.length];

    private Weather weather;
    private Events events;
    private Air air;
    private Bus bus;
    private Util util;
    private DataService dataService;
    private boolean serviceIsBound;

    private final View.OnClickListener doubleClickListener = new DoubleClickListener() {
        @Override
        public void onDoubleClick(View v) {
            Toast.makeText(getApplicationContext(), R.string.hiding, Toast.LENGTH_SHORT).show();
            moveTaskToBack(true);
        }
    };

    private void updateCalendarEvents(Events events) {
        if (events != null) {
            updateCalendarEvents(events.getLastData());
        }
    }

    private void updateCalendarEvents(List<Events.CalendarEvent> calendarEvents) {
        if (calendarEvents != null) {
            //take only first EVENT.length and reverse it's copy to fill in from bottom
            Collections.sort(calendarEvents);
            val size = calendarEvents.size() - 1;
            val data = Util.reverseList(calendarEvents.subList(0, size > 6 ? EVENT_DAY_VIEW_IDS.length : size));
            for (int i = 0; i < EVENT_DAY_VIEW_IDS.length; i++) {
                if (i < data.size()) {
                    val event = eventViews[i];
                    val calendarEvent = data.get(i);
                    val eventDay = event.getDay();
                    val drawable = util.writeOnDrawable(R.drawable.calendar_icon, calendarEvent.getDayString());
                    eventDay.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
                    val now = LocalDate.now();
                    val tomorrow = now.plusDays(1);
                    if (now.equals(calendarEvent.getStartDate()) ||
                            (calendarEvent.getStartTime() != null && now.equals(calendarEvent.getStartTime().toLocalDate()))) {
                        eventDay.setText(getString(R.string.today));
                    } else if (tomorrow.equals(calendarEvent.getStartDate())
                            || (calendarEvent.getStartTime() != null && tomorrow.equals(calendarEvent.getStartTime().toLocalDate()))) {
                        eventDay.setText(getString(R.string.tomorrow));
                    } else {
                        eventDay.setText(calendarEvent.getStartDateString());
                    }
                    event.getHour().setText(calendarEvent.getTime());
                    val title = "- " + calendarEvent.getTitle();
                    event.getTitle().setText(title);
                    event.showAll();
                } else {
                    eventViews[i].hideAll();
                }
            }
        }
    }

    private void updateAirData(Air air) {
        if (air != null) {
            updateAirData(air.getLastData());
        }
    }

    private void updateAirData(Air.AirData airData) {
        if (airData != null) {
            // Populate the air quality index number and icon.
            airQualityView.setText(Integer.toString(airData.aqi));
            airQualityView.setCompoundDrawablesWithIntrinsicBounds(airData.icon, 0, 0, 0);
            airQualityView.setVisibility(View.VISIBLE);
            windDirectionView.setRotation(airData.windDirection);
            String speed = (int) Math.round(airData.windSpeed * 3.6) + " km/h";
            windSpeedView.setText(speed);
        } else {
            windDirectionView.setVisibility(View.GONE);
            windSpeedView.setVisibility(View.GONE);
            airQualityView.setVisibility(View.GONE);
        }
    }

    private void updateWeatherData(Weather data) {
        if (data != null) {
            updateWeatherData(data.getLastData());
        }
    }

    private void updateWeatherData(Weather.WeatherData data) {
        if (data != null) {
            // Populate the current temperature rounded to a whole number.
            String temperature = String.format(Locale.FRANCE, "%d°",
                    Math.round(data.currentTemperature));
            temperatureView.setText(temperature);
            // Populate the 24-hour forecast summary, but strip any period at the end.
            String summary = util.stripPeriod(data.forecastSummary);
            weatherSummaryView.setText(summary);
            // Populate the precipitation probability as a percentage rounded to a whole number.
            String precipitation =
                    String.format(Locale.FRANCE, "%d%%", Math.round(data.precipitationProbability));
            precipitationView.setText(precipitation);
            // Populate the icon for the current weather.
            iconView.setImageResource(data.currentIcon);
            // Show all the views.
            temperatureView.setVisibility(View.VISIBLE);
            weatherSummaryView.setVisibility(View.VISIBLE);
            precipitationView.setVisibility(View.VISIBLE);
            iconView.setVisibility(View.VISIBLE);
            windDirectionView.setVisibility(View.VISIBLE);
        } else {
            // Hide everything if there is no data.
            temperatureView.setVisibility(View.GONE);
            weatherSummaryView.setVisibility(View.GONE);
            precipitationView.setVisibility(View.GONE);
            iconView.setVisibility(View.GONE);
        }
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            dataService = ((DataService.LocalBinder) service).getService();
            events = dataService.getEvents();
            air = dataService.getAir();
            weather = dataService.getWeather();
            bus = dataService.getBus();
            serviceIsBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            dataService = null;
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            val action = EventType.getType(intent.getAction());
            Bundle args = intent.getBundleExtra(DataService.ARGS);
            DataContainer container = (DataContainer) args.getSerializable(DataService.CONTAINER);
            switch (action) {
                case EVENTS_NOTIFICATION:
                    updateCalendarEvents((List<Events.CalendarEvent>) container.getData());
                    break;
                case AIR_NOTIFICATION:
                    updateAirData((Air.AirData) container.getData());
                    break;
                case WEATHER_NOTIFICATION:
                    updateWeatherData((Weather.WeatherData) container.getData());
                    break;
                case BUS_NOTIFICATION:
                    updateBusData((Bus.BusData) container.getData());
                    break;
            }
        }
    };

    private void updateBusData(Bus.BusData data) {
        Log.d("MAIN","Got bus data" + data);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Arrays.asList(Manifest.permission.READ_CALENDAR, Manifest.permission.ACCESS_FINE_LOCATION).forEach(this::checkPermission);
        util = new Util(this);
        setActivityHome();
        val serviceIntent = new Intent(this, DataService.class);
        startForegroundService(serviceIntent);
        bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);


    }

    @Override
    protected void onStart() {
        if (toggleTask == null) {
            toggleTask = scheduledBackgroundExecutor.scheduleAtFixedRate(this::toggleView, 0, TOGGLE_PERIOD, TimeUnit.MINUTES);
        }
        super.onStart();
        hideNavigation();
    }

    @Override
    protected void onStop() {
        doUnbindService();
        unregisterReceiver(receiver);
        if (toggleTask != null) {
            toggleTask.cancel(true);
            toggleTask = null;
        }
        super.onStop();
    }

    void doUnbindService() {
        if (serviceIsBound) {
            unbindService(mConnection);
            serviceIsBound = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, new IntentFilter(EventType.EVENTS_NOTIFICATION.getCode()));
        registerReceiver(receiver, new IntentFilter(EventType.AIR_NOTIFICATION.getCode()));
        registerReceiver(receiver, new IntentFilter(EventType.WEATHER_NOTIFICATION.getCode()));
        hideNavigation();
    }

    private void toggleView() {
        toggleView = !toggleView;
        Log.d(HomeActivity.class.getName(), "Home screen switched to " + (toggleView ? "even" : "odd"));
        runOnUiThread(() -> {
            if (toggleView) {
                setActivityHome();
                findViewById(R.id.activity_home).setAlpha(0f);
                updateCalendarEvents(events);
                updateAirData(air);
                updateWeatherData(weather);
                findViewById(R.id.activity_home).animate().alpha(1f).setDuration(3000);
            } else {
                setActivityHomeOdd();
                findViewById(R.id.activity_home_o).setAlpha(0f);
                updateCalendarEvents(events);
                updateAirData(air);
                updateWeatherData(weather);
                findViewById(R.id.activity_home_o).animate().alpha(1f).setDuration(3000);
            }
        });
    }

    private void hideNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            val controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            // All below using to hide navigation bar
            val currentApiVersion = Build.VERSION.SDK_INT;
            val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

            // This work only for android 4.4+
            if (currentApiVersion >= Build.VERSION_CODES.KITKAT) {
                val decorView = getWindow().getDecorView();
                decorView.setSystemUiVisibility(flags);
            }
        }
    }

    private void setActivityHome() {
        setContentView(R.layout.activity_home);
        findViewById(R.id.activity_home).setOnClickListener(doubleClickListener);
        temperatureView = findViewById(R.id.temperature);
        weatherSummaryView = findViewById(R.id.weather_summary);
        precipitationView = findViewById(R.id.precipitation);
        airQualityView = findViewById(R.id.air_quality);
        iconView = findViewById(R.id.icon);
        windDirectionView = findViewById(R.id.windDirection);
        windSpeedView = findViewById(R.id.windSpeed);
        for (int i = 0; i < EVENT_DAY_VIEW_IDS.length; i++) {
            eventViews[i] = new Events.EventsViewsWrapper(
                    findViewById(EVENT_DAY_VIEW_IDS[i]),
                    findViewById(EVENT_TIME_VIEW_IDS[i]),
                    findViewById(EVENT_TITLE_VIEW_IDS[i])
            );
        }
    }

    private void setActivityHomeOdd() {
        setContentView(R.layout.activity_home_o);
        findViewById(R.id.activity_home_o).setOnClickListener(doubleClickListener);
        temperatureView = findViewById(R.id.temperature_o);
        weatherSummaryView = findViewById(R.id.weather_summary_o);
        precipitationView = findViewById(R.id.precipitation_o);
        airQualityView = findViewById(R.id.air_quality_o);
        iconView = findViewById(R.id.icon_o);
        windDirectionView = findViewById(R.id.windDirection_o);
        windSpeedView = findViewById(R.id.windSpeed_o);
        for (int i = 0; i < EVENT_DAY_VIEW_IDS.length; i++) {
            eventViews[i] = new Events.EventsViewsWrapper(
                    findViewById(EVENT_DAY_VIEW_IDS_O[i]),
                    findViewById(EVENT_TIME_VIEW_IDS_O[i]),
                    findViewById(EVENT_TITLE_VIEW_IDS_O[i])
            );
        }
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return util.onKeyUp(keyCode, event);
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
                events.stop();
                events.start();
                checkPermission(Manifest.permission.ACCESS_FINE_LOCATION);
            } else {
                Toast.makeText(getApplicationContext(), R.string.calendar_permission_denied, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == Manifest.permission.ACCESS_FINE_LOCATION.length()) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                GeoLocation.clearCache();
                air.stop();
                air.start();
                weather.stop();
                weather.start();
                checkPermission(Manifest.permission.READ_CALENDAR);
            } else {
                Toast.makeText(getApplicationContext(), R.string.location_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
