package pl.qprogramming.magicmirror.bus;

import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.val;
import pl.qprogramming.magicmirror.R;
import pl.qprogramming.magicmirror.service.DataUpdater;

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

    public static final int[] BUS_DEPARTURE_IDS = new int[]{
            R.id.bus_next_1,
            R.id.bus_next_2,
            R.id.bus_next_3,
    };
    public static final int[] BUS_DEPARTURE_HOUR_IDS = new int[]{
            R.id.bus_next_1_h,
            R.id.bus_next_2_h,
            R.id.bus_next_3_h,
    };
    public static final int[] BUS_DEPARTURE_IDS_O = new int[]{
            R.id.bus_next_1_o,
            R.id.bus_next_2_o,
            R.id.bus_next_3_o,
    };
    public static final int[] BUS_DEPARTURE_HOUR_IDS_O = new int[]{
            R.id.bus_next_1_h_o,
            R.id.bus_next_2_h_o,
            R.id.bus_next_3_h_o,
    };

    public static class Schedule implements Serializable {
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

    @AllArgsConstructor
    @Getter
    public static class BusWrapper {
        private final TextView departure;
        private final TextView hour;

        public void hideAll() {
            departure.setVisibility(View.GONE);
            hour.setVisibility(View.GONE);
        }

        public void showAll() {
            hour.setVisibility(View.VISIBLE);
            if (!departure.getText().equals("")) {
                departure.setVisibility(View.VISIBLE);
            } else {
                departure.setVisibility(View.GONE);
            }
        }
    }


    /**
     * The data structure containing List of next schedules hours
     */
    @Getter
    public static class BusData implements Serializable {

        public final List<Schedule> schedules;

        public BusData(List<Schedule> schedules) {
            this.schedules = schedules;
        }
    }

    public Bus(UpdateListener<BusData> updateListener) {
        super(updateListener, UPDATE_INTERVAL_MILLIS);
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
            return new BusData(
                    schedule
                            .stream()
                            .filter(entry -> entry.next > 0).limit(3)
                            .collect(Collectors.toList()));
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
