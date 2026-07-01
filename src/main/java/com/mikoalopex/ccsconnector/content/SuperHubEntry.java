package com.mikoalopex.ccsconnector.content;

import com.mikoalopex.ccsconnector.synaxis.SynaxisSignalValues;
import com.verr1.synaxis.foundation.cimulink.core.signal.PortDef;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record SuperHubEntry(
        String id,
        SuperHubTarget target,
        SuperHubSource source,
        String capabilityId,
        String deviceKey,
        String portName,
        String displayName,
        String memberName,
        String side,
        String peripheralType,
        String method,
        String endpointId,
        String synaxisSource,
        String synaxisDevice,
        String synaxisPort,
        SuperHubSignalType type,
        boolean input,
        Object luaValue,
        SignalValue signalValue,
        long updateTick) {

    public PortDef portDef() {
        if (target != SuperHubTarget.SYNAXIS || !type.hasSynaxisPort()) {
            throw new IllegalStateException("Signal " + portName + " has no Synaxis port");
        }
        return input
                ? PortDef.input(portName, type.synaxisType())
                : PortDef.output(portName, type.synaxisType());
    }

    public SuperHubEntry withValue(Object luaValue, SignalValue signalValue, long updateTick) {
        return new SuperHubEntry(
                id,
                target,
                source,
                capabilityId,
                deviceKey,
                portName,
                displayName,
                memberName,
                side,
                peripheralType,
                method,
                endpointId,
                synaxisSource,
                synaxisDevice,
                synaxisPort,
                type,
                input,
                luaValue,
                signalValue,
                updateTick);
    }

    public SuperHubEntry withTypeAndValue(SuperHubSignalType type, Object luaValue, SignalValue signalValue, long updateTick) {
        return new SuperHubEntry(
                id,
                target,
                source,
                capabilityId,
                deviceKey,
                portName,
                displayName,
                memberName,
                side,
                peripheralType,
                method,
                endpointId,
                synaxisSource,
                synaxisDevice,
                synaxisPort,
                type,
                input,
                luaValue,
                signalValue,
                updateTick);
    }

    public String accessName() {
        return input ? "write" : "read";
    }

    public String targetFlowName() {
        if (target == SuperHubTarget.SYNAXIS) {
            return input ? "in" : "out";
        }
        return input ? "write" : "read";
    }

    public Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", id);
        info.put("name", portName);
        info.put("target", target.serializedName());
        info.put("source", source.serializedName());
        info.put("capabilityId", capabilityId);
        info.put("deviceKey", deviceKey);
        info.put("displayName", displayName);
        info.put("memberName", memberName);
        info.put("type", type.serializedName());
        info.put("side", side);
        info.put("peripheralType", peripheralType);
        info.put("method", method);
        info.put("endpointId", endpointId);
        info.put("synaxisSource", synaxisSource);
        info.put("synaxisDevice", synaxisDevice);
        info.put("synaxisPortName", synaxisPort);
        info.put("access", accessName());
        info.put("direction", targetFlowName());
        info.put("value", luaValue);
        info.put("hasSynaxisPort", target == SuperHubTarget.SYNAXIS && type.hasSynaxisPort());
        info.put("lastUpdateTick", updateTick);
        return info;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Target", target.serializedName());
        tag.putString("Source", source.serializedName());
        tag.putString("CapabilityId", capabilityId);
        tag.putString("DeviceKey", deviceKey);
        tag.putString("PortName", portName);
        tag.putString("DisplayName", displayName);
        tag.putString("MemberName", memberName);
        tag.putString("Side", side);
        tag.putString("PeripheralType", peripheralType);
        tag.putString("Method", method);
        tag.putString("EndpointId", endpointId);
        tag.putString("SynaxisSource", synaxisSource);
        tag.putString("SynaxisDevice", synaxisDevice);
        tag.putString("SynaxisPort", synaxisPort);
        tag.putString("Type", type.serializedName());
        tag.putBoolean("Input", input);
        tag.putLong("UpdateTick", updateTick);
        switch (type) {
            case REAL -> tag.putDouble("Value", luaValue instanceof Number number ? number.doubleValue() : 0.0D);
            case BOOLEAN -> tag.putBoolean("Value", luaValue instanceof Boolean bool && bool);
            case TEXT -> tag.putString("Value", luaValue == null ? "" : String.valueOf(luaValue));
        }
        if (signalValue != null) {
            tag.put("SignalValue", SynaxisSignalValues.toTag(signalValue));
        }
        return tag;
    }

    public static Optional<SuperHubEntry> fromTag(CompoundTag tag) {
        String id = tag.getString("Id");
        String portName = tag.getString("PortName");
        if (id.isBlank() || portName.isBlank()) {
            return Optional.empty();
        }
        SuperHubTarget target = tag.contains("Target", Tag.TAG_STRING)
                ? SuperHubTarget.parse(tag.getString("Target"))
                : SuperHubTarget.SYNAXIS;
        SuperHubSource source = tag.contains("Source", Tag.TAG_STRING)
                ? SuperHubSource.parse(tag.getString("Source"))
                : SuperHubSource.CC;
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
        String side = tag.getString("Side");
        String peripheralType = tag.getString("PeripheralType");
        String method = tag.getString("Method");
        String endpointId = tag.getString("EndpointId");
        String synaxisSource = tag.getString("SynaxisSource");
        String synaxisDevice = tag.getString("SynaxisDevice");
        String synaxisPort = tag.getString("SynaxisPort");
        String displayName = tag.getString("DisplayName");
        String memberName = tag.contains("MemberName", Tag.TAG_STRING) ? tag.getString("MemberName") : method;
        String capabilityId = tag.contains("CapabilityId", Tag.TAG_STRING)
                ? tag.getString("CapabilityId")
                : fallbackCapabilityId(source, side, peripheralType, method, endpointId, synaxisPort, tag.getBoolean("Input"));
        String deviceKey = tag.contains("DeviceKey", Tag.TAG_STRING) ? tag.getString("DeviceKey") : displayName;
        return Optional.of(new SuperHubEntry(
                id,
                target,
                source,
                capabilityId,
                deviceKey,
                portName,
                displayName,
                memberName,
                side,
                peripheralType,
                method,
                endpointId,
                synaxisSource,
                synaxisDevice,
                synaxisPort,
                type,
                tag.getBoolean("Input"),
                value,
                signalValue,
                tag.getLong("UpdateTick")));
    }

    private static String fallbackCapabilityId(SuperHubSource source, String side, String peripheralType, String method,
                                               String endpointId, String synaxisPort, boolean input) {
        if (source == SuperHubSource.SYNAXIS) {
            return "synaxis:" + endpointId + ":" + (input ? "in" : "out") + ":" + synaxisPort;
        }
        return "cc:" + side + ":" + peripheralType + ":" + method;
    }
}
