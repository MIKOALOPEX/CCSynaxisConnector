package com.mikoalopex.ccsconnector.content;

import com.verr1.synaxis.foundation.cimulink.core.signal.SignalType;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalValue;

import java.util.Locale;
import java.util.Optional;

public enum SuperHubSignalType {
    REAL("real"),
    BOOLEAN("boolean"),
    TEXT("text");

    private final String serializedName;

    SuperHubSignalType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean hasSynaxisPort() {
        return this == REAL || this == BOOLEAN;
    }

    public SignalType synaxisType() {
        return switch (this) {
            case REAL -> SignalType.REAL;
            case BOOLEAN -> SignalType.BOOLEAN;
            case TEXT -> throw new IllegalStateException("Text is not a Synaxis signal type");
        };
    }

    public static Optional<SuperHubValue> fromLua(Object value) {
        return switch (value) {
            case Number number -> {
                double real = number.doubleValue();
                if (Double.isFinite(real)) {
                    yield Optional.of(new SuperHubValue(REAL, real, new SignalValue.Real(real)));
                }
                yield Optional.empty();
            }
            case Boolean bool -> Optional.of(new SuperHubValue(BOOLEAN, bool, new SignalValue.Bool(bool)));
            case String text -> Optional.of(new SuperHubValue(TEXT, text, null));
            default -> Optional.empty();
        };
    }

    public static SuperHubSignalType parse(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (SuperHubSignalType type : values()) {
            if (type.serializedName.equals(normalized)) {
                return type;
            }
        }
        return TEXT;
    }

    public record SuperHubValue(SuperHubSignalType type, Object luaValue, SignalValue signalValue) {
    }
}
