package com.mikoalopex.ccsconnector.content;

import java.util.Locale;

public enum LogOutputCreateMode {
    ANY_INPUT("any_input", "any input"),
    SPECIFIC_INPUT("specific_input", "specific input"),
    IMMEDIATE("immediate", "immediate");

    private final String serializedName;
    private final String displayName;

    LogOutputCreateMode(String serializedName, String displayName) {
        this.serializedName = serializedName;
        this.displayName = displayName;
    }

    public String serializedName() {
        return serializedName;
    }

    public String displayName() {
        return displayName;
    }

    public static LogOutputCreateMode parse(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (LogOutputCreateMode mode : values()) {
            if (mode.serializedName.equals(normalized)) {
                return mode;
            }
        }
        return ANY_INPUT;
    }
}
