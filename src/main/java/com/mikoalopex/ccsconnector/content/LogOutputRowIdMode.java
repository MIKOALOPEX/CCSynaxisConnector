package com.mikoalopex.ccsconnector.content;

import java.util.Locale;

public enum LogOutputRowIdMode {
    TICK("tick", "tick"),
    ROW_INDEX("row_index", "row"),
    NONE("none", "none");

    private final String serializedName;
    private final String displayName;

    LogOutputRowIdMode(String serializedName, String displayName) {
        this.serializedName = serializedName;
        this.displayName = displayName;
    }

    public String serializedName() {
        return serializedName;
    }

    public String displayName() {
        return displayName;
    }

    public static LogOutputRowIdMode parse(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (LogOutputRowIdMode mode : values()) {
            if (mode.serializedName.equals(normalized)) {
                return mode;
            }
        }
        return TICK;
    }
}
