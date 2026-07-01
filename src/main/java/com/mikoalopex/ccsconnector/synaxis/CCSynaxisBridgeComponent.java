package com.mikoalopex.ccsconnector.synaxis;

import com.mikoalopex.ccsconnector.CCSConnector;
import com.mikoalopex.ccsconnector.content.BridgeChannel;
import com.mikoalopex.ccsconnector.content.BridgeDirection;
import com.mikoalopex.ccsconnector.content.CCSynaxisBridgeBlockEntity;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentConfig;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentMemory;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentSchema;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentSemantics;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentType;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentTypeId;
import com.verr1.synaxis.foundation.cimulink.core.component.EmptyComponentMemory;
import com.verr1.synaxis.foundation.cimulink.core.component.EvalContext;
import com.verr1.synaxis.foundation.cimulink.core.component.ExecutionDomain;
import com.verr1.synaxis.foundation.cimulink.core.signal.InputPort;
import com.verr1.synaxis.foundation.cimulink.core.signal.OutputPort;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalReader;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalValue;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalWriter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CCSynaxisBridgeComponent implements ComponentType {
    public static final ComponentTypeId ID = ComponentTypeId.of(CCSConnector.MODID + ":bridge");
    public static final CCSynaxisBridgeComponent INSTANCE = new CCSynaxisBridgeComponent();

    private static final ComponentSchema EMPTY_SCHEMA = ComponentSchema.of(List.of(), List.of());

    private CCSynaxisBridgeComponent() {
    }

    @Override
    public ComponentTypeId id() {
        return ID;
    }

    @Override
    public ComponentSchema schema(ComponentConfig config) {
        return config instanceof Config bridgeConfig ? bridgeConfig.schema() : EMPTY_SCHEMA;
    }

    @Override
    public ComponentConfig defaultConfig() {
        return Config.empty();
    }

    @Override
    public ComponentMemory createMemory(ComponentConfig config) {
        return EmptyComponentMemory.INSTANCE;
    }

    @Override
    public ComponentSemantics semantics(ComponentConfig config) {
        ComponentSchema schema = schema(config);
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        schema.outputs().forEach(output -> dependencies.put(output.name(), Set.of()));
        return new ComponentSemantics(true, true, true, Set.of(ExecutionDomain.GAME_TICK), dependencies);
    }

    @Override
    public void evaluate(EvalContext context, ComponentConfig config, ComponentMemory memory, SignalReader reader, SignalWriter writer) {
        if (!(config instanceof Config bridgeConfig)) {
            return;
        }
        CCSynaxisBridgeBlockEntity bridge = bridgeConfig.bridge();
        if (bridge == null) {
            writeDefaults(bridgeConfig.schema(), writer);
            return;
        }
        for (BridgeChannel channel : bridge.channels()) {
            if (channel.direction() == BridgeDirection.CC_TO_SYNAXIS) {
                SignalValue value = bridge.readForSynaxis(channel.name());
                writer.write(new OutputPort(channel.name()), value == null ? channel.defaultValue() : value);
            }
        }
    }

    @Override
    public void step(EvalContext context, ComponentConfig config, ComponentMemory memory, SignalReader reader, SignalWriter writer) {
        if (!(config instanceof Config bridgeConfig) || bridgeConfig.bridge() == null) {
            return;
        }
        CCSynaxisBridgeBlockEntity bridge = bridgeConfig.bridge();
        for (BridgeChannel channel : bridge.channels()) {
            if (channel.direction() == BridgeDirection.SYNAXIS_TO_CC) {
                SignalValue value = reader.read(new InputPort(channel.name()));
                if (value != null) {
                    bridge.writeFromSynaxis(channel.name(), value);
                } else {
                    bridge.clearMissingSynaxisInput(channel.name());
                }
            }
        }
    }

    private static void writeDefaults(ComponentSchema schema, SignalWriter writer) {
        schema.outputs().forEach(output ->
                writer.write(new OutputPort(output.name()), output.defaultValue()));
    }

    public record Config(CCSynaxisBridgeBlockEntity bridge, ComponentSchema schema) implements ComponentConfig {
        public static Config empty() {
            return new Config(null, EMPTY_SCHEMA);
        }
    }
}
