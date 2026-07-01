package com.mikoalopex.ccsconnector.synaxis;

import com.mikoalopex.ccsconnector.CCSConnector;
import com.mikoalopex.ccsconnector.content.CCSynaxisSuperHubBlockEntity;
import com.mikoalopex.ccsconnector.content.SuperHubEntry;
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

public final class CCSynaxisSuperHubComponent implements ComponentType {
    public static final ComponentTypeId ID = ComponentTypeId.of(CCSConnector.MODID + ":super_hub");
    public static final CCSynaxisSuperHubComponent INSTANCE = new CCSynaxisSuperHubComponent();

    private static final ComponentSchema EMPTY_SCHEMA = ComponentSchema.of(List.of(), List.of());

    private CCSynaxisSuperHubComponent() {
    }

    @Override
    public ComponentTypeId id() {
        return ID;
    }

    @Override
    public ComponentSchema schema(ComponentConfig config) {
        return config instanceof Config hubConfig ? hubConfig.schema() : EMPTY_SCHEMA;
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
        if (!(config instanceof Config hubConfig) || hubConfig.hub() == null) {
            return;
        }
        for (SuperHubEntry entry : hubConfig.hub().outputEntries()) {
            SignalValue value = hubConfig.hub().readForSynaxis(entry.portName());
            if (value != null) {
                writer.write(new OutputPort(entry.portName()), value);
            }
        }
    }

    @Override
    public void step(EvalContext context, ComponentConfig config, ComponentMemory memory, SignalReader reader, SignalWriter writer) {
        if (!(config instanceof Config hubConfig) || hubConfig.hub() == null) {
            return;
        }
        CCSynaxisSuperHubBlockEntity hub = hubConfig.hub();
        for (SuperHubEntry entry : hub.inputEntries()) {
            SignalValue value = reader.read(new InputPort(entry.portName()));
            if (value != null) {
                hub.writeFromSynaxis(entry.portName(), value);
            }
        }
    }

    public record Config(CCSynaxisSuperHubBlockEntity hub, ComponentSchema schema) implements ComponentConfig {
        public static Config empty() {
            return new Config(null, EMPTY_SCHEMA);
        }
    }
}
