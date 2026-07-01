package com.mikoalopex.ccsconnector.content;

import com.verr1.synaxis.foundation.cimulink.core.signal.SignalType;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalValue;
import dan200.computercraft.api.lua.LuaException;

import java.util.Locale;

public enum BridgeSignalType {
    REAL("real", SignalType.REAL),
    BOOLEAN("boolean", SignalType.BOOLEAN);

    private final String serializedName;
    private final SignalType signalType;

    BridgeSignalType(String serializedName, SignalType signalType) {
        this.serializedName = serializedName;
        this.signalType = signalType;
    }

    public String serializedName() {
        return serializedName;
    }

    public SignalType signalType() {
        return signalType;
    }

    public SignalValue defaultValue() {
        return signalType.defaultValue();
    }

    public boolean accepts(SignalValue value) {
        return signalType.accepts(value);
    }

    public static BridgeSignalType parse(String value) throws LuaException {
        if (value == null) {
            throw new LuaException("Bridge type is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (BridgeSignalType type : values()) {
            if (type.serializedName.equals(normalized)) {
                return type;
            }
        }
        throw new LuaException("Unknown bridge type '" + value + "'");
    }
}
