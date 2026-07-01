package com.mikoalopex.ccsconnector.content;

import com.mikoalopex.ccsconnector.synaxis.SynaxisSignalValues;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalValue;

import java.util.LinkedHashMap;
import java.util.Map;

public record SynaxisHubSignal(
        String name,
        String source,
        String device,
        String port,
        String type,
        Object luaValue,
        long updateTick) {

    public static SynaxisHubSignal of(String name, String source, String device, String port, SignalValue value, long updateTick) {
        return new SynaxisHubSignal(
                name,
                source,
                device,
                port,
                SynaxisSignalValues.typeName(value.kind()),
                SynaxisSignalValues.toLua(value),
                updateTick);
    }

    public Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", name);
        info.put("source", source);
        info.put("device", device);
        info.put("port", port);
        info.put("type", type);
        info.put("value", luaValue);
        info.put("lastUpdateTick", updateTick);
        return info;
    }
}
