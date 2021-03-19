package pl.qprogramming.magicmirror.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import pl.qprogramming.magicmirror.air.Air;
import pl.qprogramming.magicmirror.events.Events;
import pl.qprogramming.magicmirror.weather.Weather;

@Getter
@Setter
public class DataService extends Service {

    public static final String CONTAINER = "container";
    public static final String ARGS = "args";
    private Context context;

    private Weather weather;
    private Events events;
    private Air air;

    private final IBinder mBinder = new LocalBinder();


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
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        val context = getApplicationContext();
        initEvents(context);
        initAir(context);
        initWeather(context);
        return mBinder;
    }

    private void initEvents(Context context) {
        if (events == null) {
            long UPDATE_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(120);
            events = new Events(context, calendarUpdateListener);
            events.start();
        }
    }

    private void initAir(Context context) {
        if (air == null) {
            air = new Air(context, airQualityUpdateListener);
            air.start();
        }
    }

    private void initWeather(Context context) {
        if (weather == null) {
            weather = new Weather(context, weatherUpdateListener);
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


    private void populateAndSend(EventType type, Object data) {
        Intent intent = new Intent(type.getCode());
        val args = new Bundle();
        args.putSerializable(CONTAINER, new DataContainer(data));
        intent.putExtra(ARGS, args);
        sendBroadcast(intent);
    }
}
