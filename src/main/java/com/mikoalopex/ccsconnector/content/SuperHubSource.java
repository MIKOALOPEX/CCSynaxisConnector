package com.mikoalopex.ccsconnector.content;

import java.util.Locale;

public enum SuperHubSource {
    CC("cc", "CC"),
    SYNAXIS("synaxis", "Synaxis");

    private final String serializedName;
    private final String displayName;

    SuperHubSource(String serializedName, String displayName) {
        this.serializedName = serializedName;
        this.displayName = displayName;
    }

    public String serializedName() {
        return serializedName;
    }

    public String displayName() {
        return displayName;
    }

    public static SuperHubSource parse(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (SuperHubSource source : values()) {
            if (source.serializedName.equals(normalized)) {
                return source;
            }
        }
        return CC;
    }
}
