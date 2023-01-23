package pl.qprogramming.magicmirror.settings;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import lombok.val;
import lombok.var;
import pl.qprogramming.magicmirror.R;
import pl.qprogramming.magicmirror.service.EventType;
import pl.qprogramming.magicmirror.utils.GeoLocation;

import static pl.qprogramming.magicmirror.data.events.Events.CALENDAR_PROJECTION;
import static pl.qprogramming.magicmirror.data.events.Events.PROJECTION_ID_INDEX;
import static pl.qprogramming.magicmirror.data.events.Events.PROJECTION_NAME_INDEX;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        //verify permission are there
        Arrays.asList(Manifest.permission.READ_CALENDAR, Manifest.permission.ACCESS_FINE_LOCATION).forEach(this::checkPermission);
    }

    @Override
    protected void onDestroy() {
        //tell Data Service to reload all as keys, calendars etc. might have been changed
        Intent intent = new Intent(EventType.NEED_FORCE_RELOAD.getCode());
        sendBroadcast(intent);
        super.onDestroy();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            val interval = (EditTextPreference) findPreference(Property.TOGGLE_INTERVAL);
            intervalButtonValidation(interval);
            calendarEventsPicker();
        }

        private void calendarEventsPicker() {
            val calendars = getAllEventsForSelectedCalendars();
            MultiSelectListPreference multiSelectPref = new MultiSelectListPreference(requireContext());
            multiSelectPref.setKey(Property.CALENDARS);
            multiSelectPref.setTitle(R.string.settings_events_select);
            multiSelectPref.setEntries(calendars.keySet().toArray(new CharSequence[0]));
            multiSelectPref.setEntryValues(calendars.values().toArray(new CharSequence[0]));
            val eventsCategory = (PreferenceCategory) findPreference("events_category");
            if (eventsCategory != null) {
                eventsCategory.addPreference(multiSelectPref);
            }
        }

        public Map<String, String> getAllEventsForSelectedCalendars() {
            var calendarEntities = new HashMap<String, String>();
            try (val calendarCursor = requireContext().getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, CALENDAR_PROJECTION, null, null, null)) {
                // queries the calendars
                while (calendarCursor.moveToNext()) {
                    val calendarName = calendarCursor.getString(PROJECTION_NAME_INDEX);
                    val calendarId = calendarCursor.getString(PROJECTION_ID_INDEX);
                    calendarEntities.put(calendarName, calendarId);
                }
            }
            return calendarEntities;
        }

        private void intervalButtonValidation(EditTextPreference interval) {
            if (interval != null) {
                interval.setOnBindEditTextListener(editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    editText.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                        }

                        @Override
                        public void afterTextChanged(Editable editable) {
                            String validationError = null;
                            if (editable.length() == 0) {
                                validationError = requireContext().getString(R.string.settings_toggle_validation);
                            }
                            editText.setError(validationError);
                            editText.getRootView().findViewById(android.R.id.button1)
                                    .setEnabled(validationError == null);
                        }
                    });
                });
            }
        }
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
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == Manifest.permission.READ_CALENDAR.length()) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkPermission(Manifest.permission.ACCESS_FINE_LOCATION);
            } else {
                Toast.makeText(getApplicationContext(), R.string.calendar_permission_denied, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == Manifest.permission.ACCESS_FINE_LOCATION.length()) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                GeoLocation.clearCache();
                checkPermission(Manifest.permission.READ_CALENDAR);
            } else {
                Toast.makeText(getApplicationContext(), R.string.location_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}