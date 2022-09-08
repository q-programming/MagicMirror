package pl.qprogramming.magicmirror.bus;

import android.content.Context;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.val;
import pl.qprogramming.magicmirror.service.DataUpdater;
import pl.qprogramming.magicmirror.utils.Network;

/**
 * A helper class to regularly retrieve bus schedules for dedicated line and bus stop
 */
public class Bus extends DataUpdater<Bus.BusData> implements Serializable {
    private static final String TAG = Bus.class.getSimpleName();
    /**
     * The time in milliseconds between API calls to update bus schedule
     */
    private static final long UPDATE_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(24);
    private static final String SCHEDULE_BASE_URL = "https://www.m.rozkladzik.pl/wroclaw/rozklad_jazdy.html?l=133&d=1&b=5&dt=-1";
    public static final String IN_MINUTES_BIT_PATTERN = "(\\d?\\d)";
    private final Context context;

    public static class Schedule {
        public long next;
        public String nextHour;

        public Schedule(String hour, String minutes) {
            val bitsMatcher = Pattern.compile(IN_MINUTES_BIT_PATTERN).matcher(minutes);
            if (bitsMatcher.find()) {
                val cleanMinutes = bitsMatcher.group(1);
                this.nextHour = hour + ":" + cleanMinutes;
                assert cleanMinutes != null;
                val directTime = LocalDateTime.now().withHour(Integer.parseInt(hour)).withMinute(Integer.parseInt(cleanMinutes));
                this.next = LocalDateTime.now().until(directTime, ChronoUnit.MINUTES);
            } else {
                Log.e(TAG, "Failed to parse minutes! : " + minutes);
            }
        }

        @Override
        public String toString() {
            return "Schedule{" +
                    "next='" + next + '\'' +
                    ", nextHour='" + nextHour + '\'' +
                    '}';
        }
    }

    /**
     * The data structure containing List of next schedules hours
     */
    public static class BusData implements Serializable {

        public final List<Schedule> schedules;

        public BusData(List<Schedule> schedules) {
            this.schedules = schedules;
        }
    }

    public Bus(Context context, UpdateListener<BusData> updateListener) {
        super(updateListener, UPDATE_INTERVAL_MILLIS);
        this.context = context;
    }

    @Override
    protected BusData getData() {
        Log.d(TAG, "Fetching bus schedule");
        val schedule = new ArrayList<Schedule>();
        try {
            Document document = Jsoup.connect(SCHEDULE_BASE_URL).get();
            Element time_table = document.getElementById("time_table");
            for (Element row : time_table.select("tr")) {
                String hourStr = row.select(".h").text();
                for (Element minute : row.select(".m")) {
                    String minuteStr = minute.text();
                    schedule.add(new Bus.Schedule(hourStr, minuteStr));
                }
            }
            //filter out all negative ( already gone ) buses, and take only next 3
            BusData busData = new BusData(
                    schedule
                            .stream()
                            .filter(entry -> entry.next < 0)
                            .limit(3)
                            .collect(Collectors.toList()));
            return busData;
        } catch (IOException e) {
            Log.e(TAG, "Failed to parse bus schedules.", e);
            return null;
        }
    }

    @Override
    protected String getTag() {
        return TAG;
    }
}
