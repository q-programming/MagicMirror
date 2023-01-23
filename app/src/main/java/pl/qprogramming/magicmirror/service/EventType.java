package pl.qprogramming.magicmirror.service;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;


@Getter
public enum EventType {
    EVENTS_NOTIFICATION("q-programming.mirror.events"),
    AIR_NOTIFICATION("q-programming.mirror.air"),
    BUS_NOTIFICATION("q-programming.mirror.bus"),
    WEATHER_NOTIFICATION("q-programming.mirror.weather"),
    NEED_UPDATE("q-programming.mirror.update"),
    NEED_FORCE_RELOAD("q-programming.mirror.reload"),
    SWITCH_TIME("q-programming.mirror.switch"),
    UNKNOWN("q-programming.mirror.n/a");

    private static final Map<String, EventType> BY_CODE = new HashMap<>();

    static {
        for (EventType eType : values()) {
            BY_CODE.put(eType.code, eType);
        }
    }

    private final String code;

    EventType(String code) {
        this.code = code;
    }

    public static EventType getType(String type) {
        return BY_CODE.computeIfAbsent(type, s -> {
            Log.e("EventType", "Unknown type of Event " + type);
            return UNKNOWN;
        });
    }
}
