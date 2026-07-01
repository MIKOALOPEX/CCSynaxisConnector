package com.mikoalopex.ccsconnector.synaxis;

import com.mikoalopex.ccsconnector.content.BridgeSignalType;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalKind;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalType;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalValue;
import dan200.computercraft.api.lua.LuaException;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SynaxisSignalValues {
    private SynaxisSignalValues() {
    }

    public static String typeName(SignalType type) {
        return typeName(type.kind());
    }

    public static String typeName(SignalKind kind) {
        return kind.name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Object toLua(SignalValue value) {
        return switch (value) {
            case SignalValue.Real real -> real.value();
            case SignalValue.Bool bool -> bool.value();
            case SignalValue.Vec3 vec3 -> vector(vec3.x(), vec3.y(), vec3.z());
            case SignalValue.Quaternion quaternion -> quaternion(quaternion.x(), quaternion.y(), quaternion.z(), quaternion.w());
            case SignalValue.Pose pose -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("position", toLua(pose.position()));
                result.put("orientation", toLua(pose.orientation()));
                yield result;
            }
            case SignalValue.Twist twist -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("velocity", toLua(twist.velocity()));
                result.put("omega", toLua(twist.omega()));
                yield result;
            }
            case SignalValue.Bundle bundle -> {
                Map<String, Object> result = new LinkedHashMap<>();
                bundle.fields().forEach((key, field) -> result.put(key, toLua(field)));
                yield result;
            }
            default -> null;
        };
    }

    public static SignalValue fromLua(SignalType type, Object value) throws LuaException {
        SignalKind kind = type.kind();
        return switch (kind) {
            case REAL -> new SignalValue.Real(finiteDouble(value, typeName(type)));
            case BOOLEAN -> new SignalValue.Bool(booleanValue(value, typeName(type)));
            case VEC3 -> vec3Value(value);
            case QUATERNION -> quaternionValue(value);
            default -> throw new LuaException("Unsupported signal type '" + typeName(type) + "'");
        };
    }

    public static SignalValue fromLua(BridgeSignalType type, Object value) throws LuaException {
        return switch (type) {
            case REAL -> new SignalValue.Real(finiteDouble(value, type.serializedName()));
            case BOOLEAN -> new SignalValue.Bool(booleanValue(value, type.serializedName()));
        };
    }

    public static CompoundTag toTag(SignalValue value) {
        CompoundTag tag = new CompoundTag();
        switch (value) {
            case SignalValue.Real real -> {
                tag.putString("Type", "real");
                tag.putDouble("Value", real.value());
            }
            case SignalValue.Bool bool -> {
                tag.putString("Type", "boolean");
                tag.putBoolean("Value", bool.value());
            }
            case SignalValue.Vec3 vec3 -> {
                tag.putString("Type", "vec3");
                tag.putDouble("X", vec3.x());
                tag.putDouble("Y", vec3.y());
                tag.putDouble("Z", vec3.z());
            }
            case SignalValue.Quaternion quaternion -> {
                tag.putString("Type", "quaternion");
                tag.putDouble("X", quaternion.x());
                tag.putDouble("Y", quaternion.y());
                tag.putDouble("Z", quaternion.z());
                tag.putDouble("W", quaternion.w());
            }
            default -> tag.putString("Type", "unsupported");
        }
        return tag;
    }

    public static Optional<SignalValue> fromTag(CompoundTag tag) {
        return switch (tag.getString("Type")) {
            case "real" -> Optional.of(new SignalValue.Real(tag.getDouble("Value")));
            case "boolean" -> Optional.of(new SignalValue.Bool(tag.getBoolean("Value")));
            case "vec3" -> Optional.of(new SignalValue.Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")));
            case "quaternion" -> Optional.of(new SignalValue.Quaternion(
                    tag.getDouble("X"),
                    tag.getDouble("Y"),
                    tag.getDouble("Z"),
                    tag.getDouble("W")));
            default -> Optional.empty();
        };
    }

    private static Map<String, Object> vector(double x, double y, double z) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("x", x);
        result.put("y", y);
        result.put("z", z);
        return result;
    }

    private static Map<String, Object> quaternion(double x, double y, double z, double w) {
        Map<String, Object> result = vector(x, y, z);
        result.put("w", w);
        return result;
    }

    private static SignalValue.Vec3 vec3Value(Object value) throws LuaException {
        Map<?, ?> table = table(value, "vec3");
        return new SignalValue.Vec3(
                finiteDouble(tableValue(table, "x", 1), "vec3.x"),
                finiteDouble(tableValue(table, "y", 2), "vec3.y"),
                finiteDouble(tableValue(table, "z", 3), "vec3.z"));
    }

    private static SignalValue.Quaternion quaternionValue(Object value) throws LuaException {
        Map<?, ?> table = table(value, "quaternion");
        return new SignalValue.Quaternion(
                finiteDouble(tableValue(table, "x", 1), "quaternion.x"),
                finiteDouble(tableValue(table, "y", 2), "quaternion.y"),
                finiteDouble(tableValue(table, "z", 3), "quaternion.z"),
                finiteDouble(tableValue(table, "w", 4), "quaternion.w"));
    }

    private static Map<?, ?> table(Object value, String context) throws LuaException {
        if (value instanceof Map<?, ?> table) {
            return table;
        }
        throw new LuaException("Expected table for " + context);
    }

    private static Object tableValue(Map<?, ?> table, String namedKey, int numericKey) throws LuaException {
        Object value = table.get(namedKey);
        if (value == null) {
            value = table.get(numericKey);
        }
        if (value == null) {
            value = table.get((long) numericKey);
        }
        if (value == null) {
            value = table.get((double) numericKey);
        }
        if (value == null) {
            value = table.get(String.valueOf(numericKey));
        }
        if (value == null) {
            throw new LuaException("Missing table field '" + namedKey + "'");
        }
        return value;
    }

    private static double finiteDouble(Object value, String context) throws LuaException {
        if (value instanceof Number number) {
            double result = number.doubleValue();
            if (Double.isFinite(result)) {
                return result;
            }
        }
        throw new LuaException("Expected finite number for " + context);
    }

    private static boolean booleanValue(Object value, String context) throws LuaException {
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new LuaException("Expected boolean for " + context);
    }
}
