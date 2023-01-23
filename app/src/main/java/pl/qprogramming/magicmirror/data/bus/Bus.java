package pl.qprogramming.magicmirror.data.bus;

import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.val;
import pl.qprogramming.magicmirror.R;
import pl.qprogramming.magicmirror.service.DataUpdater;

/**
 * A helper class to regularly retrieve bus schedules for dedicated line and bus stop
 */
@Getter
public class Bus extends DataUpdater<Bus.BusData> implements Serializable {
    private static final String TAG = Bus.class.getSimpleName();
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private List<BusSchedule> schedules;
    private LocalDate lastUpdate;
    private String busLine;
    /**
     * The time in milliseconds between API calls to update bus schedule
     */
    private static final long UPDATE_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1);
    private static final String SCHEDULE_BASE_URL = "https://www.m.rozkladzik.pl/wroclaw/rozklad_jazdy.html?l=%s&d=1&b=5&dt=";
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

    public void updateNow(String busLine) {
        this.busLine=busLine;
        updateNow();
    }

    @Getter
    @Setter
    @ToString
    public static class BusSchedule implements Serializable {
        private LocalDateTime departure;

        public BusSchedule(String hour, String minute, LocalDate day) {
            this.departure = day.atTime(Integer.parseInt(hour), Integer.parseInt(minute));
        }
    }

    public static class Schedule implements Serializable {
        public long next;
        public String nextHour;

        public Schedule(LocalDateTime datetime) {
            this.nextHour = TIME_FORMATTER.format(datetime);
            this.next = LocalDateTime.now().until(datetime, ChronoUnit.MINUTES);

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

    public Bus(UpdateListener<BusData> updateListener, String busLine) {
        super(updateListener, UPDATE_INTERVAL_MILLIS);
        this.busLine = busLine;
    }

    private List<BusSchedule> retrieveBusSchedule(LocalDate day) {
        val result = new ArrayList<BusSchedule>();
        try {
            val dayOfWeek = day.getDayOfWeek().getValue();
            val url = String.format(SCHEDULE_BASE_URL, busLine) + dayOfWeek;
            Log.d(TAG, "Requesting URL: " + url);
            Document document = Jsoup.connect(url).get();
            Element time_table = document.getElementById("time_table");
            for (Element row : time_table.select("tr")) {
                String hourStr = row.select(".h").text();
                for (Element minute : row.select(".m")) {
                    String minuteStr = minute.text();
                    val bitsMatcher = Pattern.compile(IN_MINUTES_BIT_PATTERN).matcher(minuteStr);
                    if (bitsMatcher.find()) {
                        val cleanMinutes = bitsMatcher.group(1);
                        result.add(new BusSchedule(hourStr, cleanMinutes, day));
                    } else {
                        Log.e(TAG, "Failed to parse minutes! : " + minuteStr);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to parse bus schedules.", e);
        }
        return result;
    }

    @Override
    protected BusData getData() {
        //init list if empty
        if ((schedules == null || schedules.size() == 0) || lastUpdate.isBefore(LocalDate.now())) {
            Log.i(TAG, "Fetching bus schedule for today and tomorrow");
            schedules = retrieveBusSchedule(LocalDate.now());
            schedules.addAll(retrieveBusSchedule(LocalDate.now().plusDays(1)));
            lastUpdate = LocalDate.now();
        }
        return new BusData(schedules.stream()
                .filter(busSchedule -> busSchedule.getDeparture().isAfter(LocalDateTime.now().plusMinutes(9)))
                .limit(3)
                .map(busSchedule -> new Bus.Schedule(busSchedule.getDeparture()))
                .collect(Collectors.toList()));
    }

    @Override
    protected String getTag() {
        return TAG;
    }
}
