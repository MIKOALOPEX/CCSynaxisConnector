package com.mikoalopex.ccsconnector.synaxis;

import com.mikoalopex.ccsconnector.content.LogOutputAssistantBlockEntity;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentSchema;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentTypeId;
import com.verr1.synaxis.foundation.cimulink.core.component.ExecutionDomain;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalValue;
import com.verr1.synaxis.foundation.cimulink.game.body.GameThreadPlantPort;
import com.verr1.synaxis.foundation.cimulink.game.endpoint.EndpointAddress;
import com.verr1.synaxis.foundation.cimulink.game.endpoint.EndpointId;

public final class LogOutputAssistantPlantPort implements GameThreadPlantPort {
    private final LogOutputAssistantBlockEntity blockEntity;
    private final EndpointId endpointId;
    private final String deviceName;

    public LogOutputAssistantPlantPort(LogOutputAssistantBlockEntity blockEntity, EndpointId endpointId, String deviceName) {
        this.blockEntity = blockEntity;
        this.endpointId = endpointId;
        this.deviceName = deviceName;
    }

    @Override
    public EndpointId endpointId() {
        return endpointId;
    }

    @Override
    public EndpointAddress address() {
        return blockEntity.address();
    }

    @Override
    public String deviceName() {
        return deviceName;
    }

    @Override
    public ComponentTypeId componentType() {
        return LogOutputAssistantComponent.ID;
    }

    @Override
    public ComponentSchema schema() {
        return blockEntity.schema();
    }

    @Override
    public boolean supportsDomain(ExecutionDomain domain) {
        return domain == ExecutionDomain.GAME_TICK;
    }

    @Override
    public void applyInput(String port, SignalValue value) {
        blockEntity.writeFromSynaxis(port, value);
    }

    @Override
    public SignalValue readOutput(String port) {
        return null;
    }
}
