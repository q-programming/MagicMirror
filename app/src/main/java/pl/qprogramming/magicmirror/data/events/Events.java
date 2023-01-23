package pl.qprogramming.magicmirror.data.events;

import android.content.ContentUris;
import android.content.Context;
import android.provider.CalendarContract;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.val;
import lombok.var;
import pl.qprogramming.magicmirror.service.DataUpdater;
import pl.qprogramming.magicmirror.utils.MarqueTextView;

/**
 * A helper class to read calendar events
 */
public class Events extends DataUpdater<List<Events.CalendarEvent>> {

    // Projection array. Creating indices for this array instead of doing
// dynamic lookups improves performance.
    public static final String[] CALENDAR_PROJECTION = new String[]{
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.NAME
    };
    public static final String[] INSTANCE_PROJECTION = new String[]{
            CalendarContract.Instances._ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.START_DAY,
            CalendarContract.Instances.END_DAY,

    };

    // The indices for the projection array above.
    public static final int PROJECTION_ID_INDEX = 0;
    public static final int PROJECTION_NAME_INDEX = 2;

    private static final int INSTANCE_TITLE_INDEX = 1;
    private static final int INSTANCE_DESCRIPTION_INDEX = 2;
    private static final int INSTANCE_LOCATION_INDEX = 3;
    private static final int INSTANCE_START_INDEX = 4;
    private static final int INSTANCE_END_INDEX = 5;
    private static final int INSTANCE_ALL_DAY_INDEX = 6;

    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM");
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    //TODO this needs options
    private static final List<String> SHOWED_CALENDARS = Arrays.asList("Święta w Polsce", "Wspólny");
    private static final String TAG = Events.class.getSimpleName();

    public static final String CALENDAR_BY_ID_SELECTION = "(" + CalendarContract.Instances.CALENDAR_ID + " = ?)";

    /**
     * The time in milliseconds between refreshes of calendar
     */
    private static final long UPDATE_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(5);

    private final Context context;
    private Set<String> usedCalendars;

    @Data
    @Builder
    public static class CalendarEvent implements Serializable, Comparable<CalendarEvent> {
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;
        private final String title;
        private final String description;
        private final String location;
        private final boolean allDay;

        public String getStartDateString() {
            if (allDay) {
                val multi = !endDate.equals(startDate);
                return multi ? startDate.getDayOfMonth() + "-" + DATE_FORMATTER.format(endDate) : DATE_FORMATTER.format(startDate);
            }
            return DATE_FORMATTER.format(startTime);
        }

        public String getDayString() {
            val day = getDay();
            return day < 10 ? " " + day : "" + day;
        }

        public int getDay() {
            return allDay ? startDate.getDayOfMonth() : startTime.getDayOfMonth();
        }

        public String getTime() {
            return startTime != null && endTime != null ? TIME_FORMATTER.format(startTime)
                    + "-"
                    + TIME_FORMATTER.format(endTime) :
                    "";
        }

        @Override
        public String toString() {
            return allDay ? DATE_FORMATTER.format(startDate) + " : " + title :
                    DATE_FORMATTER.format(startTime)
                            + " "
                            + TIME_FORMATTER.format(startTime)
                            + "-"
                            + TIME_FORMATTER.format(endTime) + ' ' + title;
        }


        @Override
        public int compareTo(CalendarEvent obj) {
            return getStartDate().compareTo(obj.getStartDate());
        }
    }

    @AllArgsConstructor
    @Getter
    public static class EventsViewsWrapper {
        private final TextView day;
        private final TextView hour;
        private final MarqueTextView title;

        public void hideAll() {
            day.setVisibility(View.GONE);
            hour.setVisibility(View.GONE);
            title.setVisibility(View.GONE);
        }

        public void showAll() {
            day.setVisibility(View.VISIBLE);
            if (!hour.getText().equals("")) {
                hour.setVisibility(View.VISIBLE);
            } else {
                hour.setVisibility(View.GONE);
            }
            title.setVisibility(View.VISIBLE);
        }

    }

    public Events(Context context, UpdateListener<List<CalendarEvent>> updateListener, Long interval) {
        super(updateListener, interval);
        this.context = context;
    }

    public Events(Context context, UpdateListener<List<CalendarEvent>> updateListener, Set<String> calendars) {
        super(updateListener, UPDATE_INTERVAL_MILLIS);
        this.context = context;
        usedCalendars = calendars;
    }

    @Override
    protected List<CalendarEvent> getData() {
        return getAllEventsForSelectedCalendars();
    }

    public ArrayList<CalendarEvent> getAllEventsForSelectedCalendars() {
        var calendarEvents = new ArrayList<CalendarEvent>();
        try (val calendarCursor = context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, CALENDAR_PROJECTION, null, null, null)) {
            // queries the calendars
            while (calendarCursor.moveToNext()) {
//                val calendarName = calendarCursor.getString(PROJECTION_NAME_INDEX);
                val calendarId = calendarCursor.getString(PROJECTION_ID_INDEX);
                if (usedCalendars.contains(calendarId)) {
                    getCalendarEventsCount(context, calendarId, calendarEvents);
                }
            }
        }
        return calendarEvents;
    }

    public void updateNow(Set<String> calendars) {
        this.usedCalendars = calendars;
        updateNow();
    }

    // this method gets a count of the events in a given calendar
    private void getCalendarEventsCount(final Context context, final String calendarId, ArrayList<CalendarEvent> calendarEvents) {
        val beginTime = Calendar.getInstance();
        val endTime = Calendar.getInstance();

        // get events from the start of the day until the same evening.
        endTime.set(beginTime.get(Calendar.YEAR), beginTime.get(Calendar.MONTH), beginTime.get(Calendar.DAY_OF_MONTH) + 14);
        val startMillis = beginTime.getTimeInMillis();
        val endMillis = endTime.getTimeInMillis();

        // Construct the query with the desired date range.
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, startMillis);
        ContentUris.appendId(builder, endMillis);

        val selectionArgs = new String[]{calendarId};
        try (val cursor = context.getContentResolver().query(builder.build(), INSTANCE_PROJECTION, CALENDAR_BY_ID_SELECTION, selectionArgs, CalendarContract.Instances.BEGIN + " ASC")) {
            while (cursor.moveToNext()) {
                if (cursor.getCount() > 0) {
                    val title = cursor.getString(INSTANCE_TITLE_INDEX);
                    val description = cursor.getString(INSTANCE_DESCRIPTION_INDEX);
                    val location = cursor.getString(INSTANCE_LOCATION_INDEX);
                    val allDay = cursor.getString(INSTANCE_ALL_DAY_INDEX);
                    var eventBuilder = CalendarEvent.builder()
                            .title(title)
                            .description(description)
                            .location(location);
                    if (allDay.equals("1")) {
                        val start = convertToDate(cursor.getLong(INSTANCE_START_INDEX));
                        val end = convertToDate(cursor.getLong(INSTANCE_END_INDEX)).minusDays(1);
                        eventBuilder.startDate(start).endDate(end).allDay(true);
                    } else {
                        val start = convertToDateTime(cursor.getLong(INSTANCE_START_INDEX));
                        val end = convertToDateTime(cursor.getLong(INSTANCE_END_INDEX));
                        eventBuilder
                                .startDate(start.toLocalDate())
                                .startTime(start)
                                .endTime(end);

                    }
                    calendarEvents.add(eventBuilder.build());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to properly parse Event", e);
        }
    }

    private static LocalDateTime convertToDateTime(long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp),
                TimeZone.getDefault().toZoneId());
    }

    private LocalDate convertToDate(long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();
    }


    @Override
    protected String getTag() {
        return TAG;
    }
}
