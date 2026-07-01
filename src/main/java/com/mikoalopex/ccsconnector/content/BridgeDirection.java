package com.mikoalopex.ccsconnector.content;

import dan200.computercraft.api.lua.LuaException;

import java.util.Locale;

public enum BridgeDirection {
    SYNAXIS_TO_CC("synaxis_to_cc"),
    CC_TO_SYNAXIS("cc_to_synaxis");

    private final String serializedName;

    BridgeDirection(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean canCcRead() {
        return this == SYNAXIS_TO_CC;
    }

    public boolean canCcWrite() {
        return this == CC_TO_SYNAXIS;
    }

    public static BridgeDirection parse(String value) throws LuaException {
        if (value == null) {
            throw new LuaException("Bridge direction is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (BridgeDirection direction : values()) {
            if (direction.serializedName.equals(normalized)) {
                return direction;
            }
        }
        throw new LuaException("Unknown bridge direction '" + value + "'");
    }
}
