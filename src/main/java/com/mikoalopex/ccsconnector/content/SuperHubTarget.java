package com.mikoalopex.ccsconnector.content;

import java.util.Locale;

public enum SuperHubTarget {
    SYNAXIS("synaxis", "Synaxis"),
    CC("cc", "CC");

    private final String serializedName;
    private final String displayName;

    SuperHubTarget(String serializedName, String displayName) {
        this.serializedName = serializedName;
        this.displayName = displayName;
    }

    public String serializedName() {
        return serializedName;
    }

    public String displayName() {
        return displayName;
    }

    public SuperHubTarget opposite() {
        return this == SYNAXIS ? CC : SYNAXIS;
    }

    public static SuperHubTarget parse(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (SuperHubTarget target : values()) {
            if (target.serializedName.equals(normalized)) {
                return target;
            }
        }
        return SYNAXIS;
    }
}
