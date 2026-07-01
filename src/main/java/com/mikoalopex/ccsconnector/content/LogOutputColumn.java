package com.mikoalopex.ccsconnector.content;

import com.verr1.synaxis.foundation.cimulink.core.signal.PortDef;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record LogOutputColumn(
        String id,
        String name,
        String portName,
        BridgeSignalType type,
        Object value,
        boolean activated,
        long updateTick) {

    public PortDef portDef() {
        return PortDef.input(portName, type.signalType());
    }

    public LogOutputColumn withValue(Object value, boolean activated, long updateTick) {
        return new LogOutputColumn(id, name, portName, type, value, activated, updateTick);
    }

    public Map<String, Object> info() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("name", name);
        result.put("portName", portName);
        result.put("type", type.serializedName());
        result.put("value", value);
        result.put("activated", activated);
        result.put("updateTick", updateTick);
        return result;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Name", name);
        tag.putString("PortName", portName);
        tag.putString("Type", type.serializedName());
        tag.putBoolean("Activated", activated);
        tag.putLong("UpdateTick", updateTick);
        if (value != null) {
            tag.putBoolean("HasValue", true);
            switch (type) {
                case REAL -> tag.putDouble("Value", value instanceof Number number ? number.doubleValue() : 0.0D);
                case BOOLEAN -> tag.putBoolean("Value", value instanceof Boolean bool && bool);
            }
        }
        return tag;
    }

    public static Optional<LogOutputColumn> fromTag(CompoundTag tag) {
        String id = tag.getString("Id");
        String portName = tag.getString("PortName");
        if (id.isBlank() || portName.isBlank()) {
            return Optional.empty();
        }
        BridgeSignalType type = safeType(tag.getString("Type"));
        Object value = null;
        boolean activated = tag.contains("Activated", Tag.TAG_BYTE) && tag.getBoolean("Activated");
        if (tag.getBoolean("HasValue") || tag.contains("Value", Tag.TAG_ANY_NUMERIC)) {
            value = switch (type) {
                case REAL -> tag.getDouble("Value");
                case BOOLEAN -> tag.getBoolean("Value");
            };
            if (!tag.contains("Activated", Tag.TAG_BYTE)) {
                activated = !isDefaultValue(type, value);
            }
        }
        String name = tag.getString("Name");
        return Optional.of(new LogOutputColumn(
                id,
                name.isBlank() ? portName : name,
                portName,
                type,
                value,
                activated,
                tag.getLong("UpdateTick")));
    }

    public static Object luaValue(BridgeSignalType type, SignalValue value) {
        return switch (type) {
            case REAL -> value instanceof SignalValue.Real real ? real.value() : null;
            case BOOLEAN -> value instanceof SignalValue.Bool bool ? bool.value() : null;
        };
    }

    public static BridgeSignalType safeType(String typeName) {
        try {
            return BridgeSignalType.parse(typeName);
        } catch (Exception exception) {
            return BridgeSignalType.REAL;
        }
    }

    public static boolean isDefaultValue(BridgeSignalType type, Object value) {
        return switch (type) {
            case REAL -> value instanceof Number number && number.doubleValue() == 0.0D;
            case BOOLEAN -> value instanceof Boolean bool && !bool;
        };
    }
}
