package com.mikoalopex.ccsconnector.content;

import com.mikoalopex.ccsconnector.synaxis.SynaxisSignalValues;
import com.verr1.synaxis.foundation.cimulink.core.signal.PortDef;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record SuperHubSignal(
        String name,
        String side,
        String peripheralType,
        String method,
        SuperHubSignalType type,
        Object luaValue,
        SignalValue signalValue,
        long updateTick) {

    public PortDef portDef() {
        if (!type.hasSynaxisPort()) {
            throw new IllegalStateException("Signal " + name + " has no Synaxis port");
        }
        return PortDef.output(name, type.synaxisType());
    }

    public Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", name);
        info.put("type", type.serializedName());
        info.put("side", side);
        info.put("peripheralType", peripheralType);
        info.put("method", method);
        info.put("value", luaValue);
        info.put("synaxisPort", type.hasSynaxisPort());
        info.put("lastUpdateTick", updateTick);
        return info;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putString("Side", side);
        tag.putString("PeripheralType", peripheralType);
        tag.putString("Method", method);
        tag.putString("Type", type.serializedName());
        tag.putLong("UpdateTick", updateTick);
        switch (type) {
            case REAL -> tag.putDouble("Value", luaValue instanceof Number number ? number.doubleValue() : 0.0D);
            case BOOLEAN -> tag.putBoolean("Value", luaValue instanceof Boolean bool && bool);
            case TEXT -> tag.putString("Value", String.valueOf(luaValue));
        }
        if (signalValue != null) {
            tag.put("SignalValue", SynaxisSignalValues.toTag(signalValue));
        }
        return tag;
    }

    public static Optional<SuperHubSignal> fromTag(CompoundTag tag) {
        String name = tag.getString("Name");
        if (name.isBlank()) {
            return Optional.empty();
        }
        SuperHubSignalType type = SuperHubSignalType.parse(tag.getString("Type"));
        Object value = switch (type) {
            case REAL -> tag.getDouble("Value");
            case BOOLEAN -> tag.getBoolean("Value");
            case TEXT -> tag.getString("Value");
        };
        SignalValue signalValue = null;
        if (tag.contains("SignalValue", Tag.TAG_COMPOUND)) {
            signalValue = SynaxisSignalValues.fromTag(tag.getCompound("SignalValue")).orElse(null);
        } else if (type == SuperHubSignalType.REAL && value instanceof Number number) {
            signalValue = new SignalValue.Real(number.doubleValue());
        } else if (type == SuperHubSignalType.BOOLEAN && value instanceof Boolean bool) {
            signalValue = new SignalValue.Bool(bool);
        }
        return Optional.of(new SuperHubSignal(
                name,
                tag.getString("Side"),
                tag.getString("PeripheralType"),
                tag.getString("Method"),
                type,
                value,
                signalValue,
                tag.getLong("UpdateTick")));
    }
}
