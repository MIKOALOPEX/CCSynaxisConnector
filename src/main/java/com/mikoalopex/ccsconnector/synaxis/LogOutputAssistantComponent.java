package com.mikoalopex.ccsconnector.synaxis;

import com.mikoalopex.ccsconnector.CCSConnector;
import com.mikoalopex.ccsconnector.content.LogOutputAssistantBlockEntity;
import com.mikoalopex.ccsconnector.content.LogOutputColumn;
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
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalReader;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalValue;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalWriter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LogOutputAssistantComponent implements ComponentType {
    public static final ComponentTypeId ID = ComponentTypeId.of(CCSConnector.MODID + ":log_output_assistant");
    public static final LogOutputAssistantComponent INSTANCE = new LogOutputAssistantComponent();

    private static final ComponentSchema EMPTY_SCHEMA = ComponentSchema.of(List.of(), List.of());

    private LogOutputAssistantComponent() {
    }

    @Override
    public ComponentTypeId id() {
        return ID;
    }

    @Override
    public ComponentSchema schema(ComponentConfig config) {
        return config instanceof Config logConfig ? logConfig.schema() : EMPTY_SCHEMA;
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
    }

    @Override
    public void step(EvalContext context, ComponentConfig config, ComponentMemory memory, SignalReader reader, SignalWriter writer) {
        if (!(config instanceof Config logConfig) || logConfig.assistant() == null) {
            return;
        }
        LogOutputAssistantBlockEntity assistant = logConfig.assistant();
        for (LogOutputColumn column : assistant.columns()) {
            SignalValue value = reader.read(new InputPort(column.portName()));
            if (value != null) {
                assistant.writeFromSynaxis(column.portName(), value);
            }
        }
    }

    public record Config(LogOutputAssistantBlockEntity assistant, ComponentSchema schema) implements ComponentConfig {
        public static Config empty() {
            return new Config(null, EMPTY_SCHEMA);
        }
    }
}
