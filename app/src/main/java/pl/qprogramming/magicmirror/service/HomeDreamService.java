package pl.qprogramming.magicmirror.service;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.service.dreams.DreamService;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import lombok.val;
import pl.qprogramming.magicmirror.HomeActivity;
import pl.qprogramming.magicmirror.R;
import pl.qprogramming.magicmirror.data.DataContainer;
import pl.qprogramming.magicmirror.data.air.Air;
import pl.qprogramming.magicmirror.data.bus.Bus;
import pl.qprogramming.magicmirror.data.events.Events;
import pl.qprogramming.magicmirror.data.weather.Weather;
import pl.qprogramming.magicmirror.utils.Util;

import static pl.qprogramming.magicmirror.data.bus.Bus.BUS_DEPARTURE_HOUR_IDS;
import static pl.qprogramming.magicmirror.data.bus.Bus.BUS_DEPARTURE_HOUR_IDS_O;
import static pl.qprogramming.magicmirror.data.bus.Bus.BUS_DEPARTURE_IDS;
import static pl.qprogramming.magicmirror.data.bus.Bus.BUS_DEPARTURE_IDS_O;
import static pl.qprogramming.magicmirror.data.events.EventsId.EVENT_DAY_VIEW_IDS;
import static pl.qprogramming.magicmirror.data.events.EventsId.EVENT_DAY_VIEW_IDS_O;
import static pl.qprogramming.magicmirror.data.events.EventsId.EVENT_TIME_VIEW_IDS;
import static pl.qprogramming.magicmirror.data.events.EventsId.EVENT_TIME_VIEW_IDS_O;
import static pl.qprogramming.magicmirror.data.events.EventsId.EVENT_TITLE_VIEW_IDS;
import static pl.qprogramming.magicmirror.data.events.EventsId.EVENT_TITLE_VIEW_IDS_O;

/**
 * Dream Service ( screen saver )  which only goal is to launch main activity and then "wake" from screen saving
 */
public class HomeDreamService extends DreamService {
    private static final String TAG = HomeDreamService.class.getSimpleName();
    public static final int TOGGLE_PERIOD = 1;

    public static Locale PolishLocale = new Locale("pl,PL");
    private SimpleDateFormat sdf = new SimpleDateFormat("EEEE");
    private boolean toggleView;
    private TextView temperatureView;
    private TextView maxTemperatureView;
    private TextView minTemperatureView;
    private TextView weatherSummaryView;
    private TextView precipitationView;
    private TextView airQualityView;
    private ImageView iconView;
    private ImageView day2iconView;
    private ImageView day3iconView;
    private ImageView day4iconView;
    private TextView day2text;
    private TextView day3text;
    private TextView day4text;
    private TextView windSpeedView;
    private ImageView windDirectionView;
    private TextView busHeader;
    private final Events.EventsViewsWrapper[] eventViews = new Events.EventsViewsWrapper[EVENT_DAY_VIEW_IDS.length];
    private final Bus.BusWrapper[] busViews = new Bus.BusWrapper[BUS_DEPARTURE_IDS.length];


    private Weather weather;
    private Events events;
    private Air air;
    private Bus bus;
    private Util util;
    private DataService dataService;
    private boolean serviceIsBound;

    private void updateCalendarEvents(Events events) {
        if (events != null) {
            updateCalendarEvents(events.getLastData());
        }
    }

    private void updateCalendarEvents(List<Events.CalendarEvent> calendarEvents) {
        if (calendarEvents != null && calendarEvents.size() > 0) {
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
        } else {
            for (Events.EventsViewsWrapper eventView : eventViews) {
                eventView.hideAll();
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
            airQualityView.setText(String.format(PolishLocale, "%d", airData.aqi));
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
            String temperature = formatTemperature(data.currentTemperature);
            String minTemperature = formatTemperature(data.minimumTemperature);
            String maxTemperature = formatTemperature(data.maximumTemperature) + " /";
            temperatureView.setText(temperature);
            minTemperatureView.setText(minTemperature);
            maxTemperatureView.setText(maxTemperature);
            //populate days
            val date = LocalDate.now();
            val dow = date.getDayOfWeek();
            day2text.setText(dow.plus(1).getDisplayName(TextStyle.FULL,PolishLocale));
            day3text.setText(dow.plus(2).getDisplayName(TextStyle.FULL,PolishLocale));
            day4text.setText(dow.plus(3).getDisplayName(TextStyle.FULL,PolishLocale));
            // Populate the 24-hour forecast summary, but strip any period at the end.
            String summary = util.stripPeriod(data.forecastSummary);
            weatherSummaryView.setText(summary);
            // Populate the precipitation probability as a percentage rounded to a whole number.
            String precipitation =
                    String.format(PolishLocale, "%d%%", Math.round(data.precipitationProbability));
            precipitationView.setText(precipitation);
            // Populate the icon for weathers
            iconView.setImageResource(data.currentIcon);
            day2iconView.setImageResource(data.day2Icon);
            day3iconView.setImageResource(data.day3icon);
            day4iconView.setImageResource(data.day4icon);
            // Show all the views.
            temperatureView.setVisibility(View.VISIBLE);
            minTemperatureView.setVisibility(View.VISIBLE);
            maxTemperatureView.setVisibility(View.VISIBLE);
            weatherSummaryView.setVisibility(View.VISIBLE);
            precipitationView.setVisibility(View.VISIBLE);
            iconView.setVisibility(View.VISIBLE);
            day2iconView.setVisibility(View.VISIBLE);
            day3iconView.setVisibility(View.VISIBLE);
            day4iconView.setVisibility(View.VISIBLE);
            day2text.setVisibility(View.VISIBLE);
            day3text.setVisibility(View.VISIBLE);
            day4text.setVisibility(View.VISIBLE);
            windDirectionView.setVisibility(View.VISIBLE);
        } else {
            // Hide everything if there is no data.
            temperatureView.setVisibility(View.GONE);
            minTemperatureView.setVisibility(View.GONE);
            maxTemperatureView.setVisibility(View.GONE);
            weatherSummaryView.setVisibility(View.GONE);
            precipitationView.setVisibility(View.GONE);
            day2iconView.setVisibility(View.GONE);
            day3iconView.setVisibility(View.GONE);
            day4iconView.setVisibility(View.GONE);
            day2text.setVisibility(View.GONE);
            day3text.setVisibility(View.GONE);
            day4text.setVisibility(View.GONE);
            iconView.setVisibility(View.GONE);
        }
    }

    private void updateBusData(Bus data) {
        if (data != null) {
            updateBusData(data.getLastData());
        }
    }

    private void updateBusData(Bus.BusData data) {
        if (data != null) {
            for (int i = 0; i < BUS_DEPARTURE_IDS.length; i++) {
                if (i < data.schedules.size()) {
                    val busView = busViews[i];
                    val schedule = data.schedules.get(i);
                    if (schedule.next > 60) {
                        busView.getDeparture().setText(" ");
                    } else {
                        busView.getDeparture().setText(String.format(PolishLocale, "%d min", schedule.next));
                    }
                    busView.getHour().setText(schedule.nextHour);
                    busView.showAll();
                } else {
                    busViews[i].hideAll();
                }
            }
            if (data.schedules.size() == 0) {
                busHeader.setVisibility(View.GONE);
            } else {
                busHeader.setVisibility(View.VISIBLE);
            }
        }
    }

    @NonNull
    private String formatTemperature(double temperature) {
        return String.format(PolishLocale, "%d°",
                Math.round(temperature));
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
            DataContainer container = null;
            if (args != null) {
                container = (DataContainer) args.getSerializable(DataService.CONTAINER);
            }
            switch (action) {
                case EVENTS_NOTIFICATION:
                    updateCalendarEvents((List<Events.CalendarEvent>) container.getData());
                    break;
                case AIR_NOTIFICATION:
                    assert container != null;
                    updateAirData((Air.AirData) container.getData());
                    break;
                case WEATHER_NOTIFICATION:
                    assert container != null;
                    updateWeatherData((Weather.WeatherData) container.getData());
                    break;
                case BUS_NOTIFICATION:
                    assert container != null;
                    updateBusData((Bus.BusData) container.getData());
                    break;
                case SWITCH_TIME:
                    Log.d(TAG, "received switch signal!");
                    toggleView();
            }
        }
    };

    @Override
    public void onDreamingStarted() {
        registerReceiver(receiver, new IntentFilter(EventType.EVENTS_NOTIFICATION.getCode()));
        registerReceiver(receiver, new IntentFilter(EventType.AIR_NOTIFICATION.getCode()));
        registerReceiver(receiver, new IntentFilter(EventType.WEATHER_NOTIFICATION.getCode()));
        registerReceiver(receiver, new IntentFilter(EventType.BUS_NOTIFICATION.getCode()));
        registerReceiver(receiver, new IntentFilter(EventType.SWITCH_TIME.getCode()));
        setActivityHome();
        util = new Util(this);
        val serviceIntent = new Intent(this, DataService.class);
        startForegroundService(serviceIntent);
        bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
        Intent intent = new Intent(EventType.NEED_UPDATE.getCode());
        sendBroadcast(intent);
        hideNavigation();
        super.onDreamingStarted();
    }


    @Override
    public void onDreamingStopped() {
        Log.d(TAG, "killing all as dreaming stopped");
        doUnbindService();
        unregisterReceiver(receiver);
        super.onDreamingStopped();
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

    void doUnbindService() {
        if (serviceIsBound) {
            unbindService(mConnection);
            serviceIsBound = false;
        }
    }

    private void toggleView() {
        toggleView = !toggleView;
        Log.d(HomeActivity.class.getName(), "Home screen switched to " + (toggleView ? "even" : "odd"));
        if (toggleView) {
            setActivityHome();
            findViewById(R.id.activity_home).setAlpha(0f);
            updateCalendarEvents(events);
            updateAirData(air);
            updateWeatherData(weather);
            updateBusData(bus);
            findViewById(R.id.activity_home).animate().alpha(1f).setDuration(3000);
        } else {
            setActivityHomeOdd();
            findViewById(R.id.activity_home_o).setAlpha(0f);
            updateCalendarEvents(events);
            updateAirData(air);
            updateWeatherData(weather);
            updateBusData(bus);
            findViewById(R.id.activity_home_o).animate().alpha(1f).setDuration(3000);
        }
    }

    private void setActivityHome() {
        setContentView(R.layout.activity_home);
        temperatureView = findViewById(R.id.temperature);
        minTemperatureView = findViewById(R.id.min_temperature);
        maxTemperatureView = findViewById(R.id.max_temperature);
        weatherSummaryView = findViewById(R.id.weather_summary);
        precipitationView = findViewById(R.id.precipitation);
        airQualityView = findViewById(R.id.air_quality);
        iconView = findViewById(R.id.icon);
        day2iconView = findViewById(R.id.day2icon);
        day3iconView = findViewById(R.id.day3icon);
        day4iconView = findViewById(R.id.day4icon);
        day2text = findViewById(R.id.day2text);
        day3text = findViewById(R.id.day3text);
        day4text = findViewById(R.id.day4text);
        windDirectionView = findViewById(R.id.windDirection);
        windSpeedView = findViewById(R.id.windSpeed);
        for (int i = 0; i < EVENT_DAY_VIEW_IDS.length; i++) {
            eventViews[i] = new Events.EventsViewsWrapper(
                    findViewById(EVENT_DAY_VIEW_IDS[i]),
                    findViewById(EVENT_TIME_VIEW_IDS[i]),
                    findViewById(EVENT_TITLE_VIEW_IDS[i])
            );
        }
        busHeader = findViewById(R.id.bus_header);
        for (int i = 0; i < BUS_DEPARTURE_IDS.length; i++) {
            busViews[i] = new Bus.BusWrapper(
                    findViewById(BUS_DEPARTURE_IDS[i]),
                    findViewById(BUS_DEPARTURE_HOUR_IDS[i])
            );
        }
    }

    private void setActivityHomeOdd() {
        setContentView(R.layout.activity_home_o);
        temperatureView = findViewById(R.id.temperature_o);
        minTemperatureView = findViewById(R.id.min_temperature_o);
        maxTemperatureView = findViewById(R.id.max_temperature_o);
        weatherSummaryView = findViewById(R.id.weather_summary_o);
        precipitationView = findViewById(R.id.precipitation_o);
        airQualityView = findViewById(R.id.air_quality_o);
        iconView = findViewById(R.id.icon_o);
        day2iconView = findViewById(R.id.day2icon_o);
        day3iconView = findViewById(R.id.day3icon_o);
        day4iconView = findViewById(R.id.day4icon_o);
        day2text = findViewById(R.id.day2text_o);
        day3text = findViewById(R.id.day3text_o);
        day4text = findViewById(R.id.day4text_o);
        windDirectionView = findViewById(R.id.windDirection_o);
        windSpeedView = findViewById(R.id.windSpeed_o);
        for (int i = 0; i < EVENT_DAY_VIEW_IDS.length; i++) {
            eventViews[i] = new Events.EventsViewsWrapper(
                    findViewById(EVENT_DAY_VIEW_IDS_O[i]),
                    findViewById(EVENT_TIME_VIEW_IDS_O[i]),
                    findViewById(EVENT_TITLE_VIEW_IDS_O[i])
            );
        }
        busHeader = findViewById(R.id.bus_header_o);
        for (int i = 0; i < BUS_DEPARTURE_IDS_O.length; i++) {
            busViews[i] = new Bus.BusWrapper(
                    findViewById(BUS_DEPARTURE_IDS_O[i]),
                    findViewById(BUS_DEPARTURE_HOUR_IDS_O[i])
            );
        }
    }
}
