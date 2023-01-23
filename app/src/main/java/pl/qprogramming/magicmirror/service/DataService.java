package pl.qprogramming.magicmirror.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import pl.qprogramming.magicmirror.R;
import pl.qprogramming.magicmirror.data.DataContainer;
import pl.qprogramming.magicmirror.data.air.Air;
import pl.qprogramming.magicmirror.data.bus.Bus;
import pl.qprogramming.magicmirror.data.events.Events;
import pl.qprogramming.magicmirror.data.weather.Weather;
import pl.qprogramming.magicmirror.settings.Property;

@Getter
@Setter
public class DataService extends Service {
    private static final String TAG = DataService.class.getSimpleName();

    public static final String CONTAINER = "container";
    public static final String ARGS = "args";
    private Context context;

    private Weather weather;
    private Events events;
    private Air air;
    private Bus bus;
    private final ScheduledExecutorService scheduledBackgroundExecutor =
            Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> toggleTask;

    private final IBinder mBinder = new LocalBinder();
    private Set<String> calendars;


    @Override
    public void onCreate() {
        super.onCreate();
        String CHANNEL_ID = "data_service";
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Mirror app is running in background",
                NotificationManager.IMPORTANCE_DEFAULT);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("")
                .setContentText("").build();
        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (events != null) {
            events.stop();
        }
        if (weather != null) {
            weather.stop();
        }
        if (air != null) {
            air.stop();
        }
        if (bus != null) {
            bus.stop();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        val context = getApplicationContext();
        val pm = PreferenceManager.getDefaultSharedPreferences(context);
        val busLine = pm.getString(Property.BUS_LINE, "133");
        val toggleInterval = pm.getString(Property.TOGGLE_INTERVAL, "1");
        val weatherKey = pm.getString(Property.WEATHER_KEY, context.getString(R.string.accu_weather_api_key));
        val calendars = pm.getStringSet(Property.CALENDARS, new HashSet<>());
        initEvents(context, calendars);
        initAir(context);
        initWeather(context, weatherKey);
        initBus(busLine);
        registerReceiver(receiver, new IntentFilter(EventType.NEED_UPDATE.getCode()));
        registerReceiver(receiver, new IntentFilter(EventType.NEED_FORCE_RELOAD.getCode()));
        toggleTask = scheduledBackgroundExecutor.scheduleAtFixedRate(() -> {
            Intent toggleIntent = new Intent(EventType.SWITCH_TIME.getCode());
            sendBroadcast(toggleIntent);
        }, 0, Integer.parseInt(toggleInterval), TimeUnit.MINUTES);
        return mBinder;
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            val action = EventType.getType(intent.getAction());
            if (action == EventType.NEED_FORCE_RELOAD) {
                val pm = PreferenceManager.getDefaultSharedPreferences(context);
                val busLine = pm.getString(Property.BUS_LINE, "133");
                val toggleInterval = pm.getString(Property.TOGGLE_INTERVAL, "1");
                val weatherKey = pm.getString(Property.WEATHER_KEY, context.getString(R.string.accu_weather_api_key));
                val calendars = pm.getStringSet(Property.CALENDARS, new HashSet<>());
                Log.w(TAG, "Request to restart all data was requested");
                air.updateNow();
                events.updateNow(calendars);
                bus.updateNow(busLine);
                weather.updateNow(weatherKey);
                if (toggleTask != null) {
                    toggleTask.cancel(true);
                    toggleTask = scheduledBackgroundExecutor.scheduleAtFixedRate(() -> {
                        Intent toggleIntent = new Intent(EventType.SWITCH_TIME.getCode());
                        sendBroadcast(toggleIntent);
                    }, 0, Integer.parseInt(toggleInterval), TimeUnit.MINUTES);
                }
            } else {
                Log.d(TAG, "Somebody requested update");
                populateAndSend(EventType.AIR_NOTIFICATION, air.getLastData());
                populateAndSend(EventType.EVENTS_NOTIFICATION, events.getLastData());
                populateAndSend(EventType.BUS_NOTIFICATION, bus.getLastData());
                populateAndSend(EventType.WEATHER_NOTIFICATION, weather.getLastData());
            }
        }
    };


    private void initEvents(Context context, Set<String> calendars) {
        this.calendars = calendars;
        if (events == null) {
            long UPDATE_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(120);
            events = new Events(context, calendarUpdateListener, calendars);
            events.start();
        }
    }

    private void initAir(Context context) {
        if (air == null) {
            air = new Air(context, airQualityUpdateListener);
            air.start();
        }
    }

    private void initBus(String busLine) {
        if (bus == null) {
            bus = new Bus(busScheduleUpdateListener, busLine);
            bus.start();
        }
    }

    private void initWeather(Context context, String weatherKey) {
        if (weather == null) {
            weather = new Weather(context, weatherUpdateListener, weatherKey);
            weather.start();
        }
    }

    public class LocalBinder extends Binder {
        public DataService getService() {
            return DataService.this;
        }
    }


    private final DataUpdater.UpdateListener<Air.AirData> airQualityUpdateListener =
            airData -> populateAndSend(EventType.AIR_NOTIFICATION, airData);

    private final DataUpdater.UpdateListener<List<Events.CalendarEvent>> calendarUpdateListener =
            calendarEvents -> populateAndSend(EventType.EVENTS_NOTIFICATION, calendarEvents);

    private final DataUpdater.UpdateListener<Weather.WeatherData> weatherUpdateListener =
            weatherData -> populateAndSend(EventType.WEATHER_NOTIFICATION, weatherData);

    private final DataUpdater.UpdateListener<Bus.BusData> busScheduleUpdateListener =
            busData -> populateAndSend(EventType.BUS_NOTIFICATION, busData);


    private void populateAndSend(EventType type, Object data) {
        Intent intent = new Intent(type.getCode());
        val args = new Bundle();
        args.putSerializable(CONTAINER, new DataContainer(data));
        intent.putExtra(ARGS, args);
        sendBroadcast(intent);
    }
}

