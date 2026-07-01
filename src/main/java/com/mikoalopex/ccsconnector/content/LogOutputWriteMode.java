package com.mikoalopex.ccsconnector.content;

import java.util.Locale;

public enum LogOutputWriteMode {
    EVERY_TICK("every_tick", "every tick"),
    ON_CHANGE("on_change", "on change"),
    INTERVAL("interval", "interval");

    private final String serializedName;
    private final String displayName;

    LogOutputWriteMode(String serializedName, String displayName) {
        this.serializedName = serializedName;
        this.displayName = displayName;
    }

    public String serializedName() {
        return serializedName;
    }

    public String displayName() {
        return displayName;
    }

    public static LogOutputWriteMode parse(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (LogOutputWriteMode mode : values()) {
            if (mode.serializedName.equals(normalized)) {
                return mode;
            }
        }
        return EVERY_TICK;
    }
}
