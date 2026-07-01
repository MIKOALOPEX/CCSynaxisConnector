package com.mikoalopex.ccsconnector.content;

import com.verr1.synaxis.foundation.cimulink.game.body.PlantEndpointProvider;
import com.verr1.synaxis.foundation.cimulink.game.body.PlantPort;
import com.verr1.synaxis.foundation.cimulink.game.body.PlantPortProviders;
import com.verr1.synaxis.foundation.cimulink.game.endpoint.EndpointId;
import com.verr1.synaxis.foundation.cimulink.game.runtime.CimulinkLevelRuntime;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;

final class SynaxisPlantAccess {
    private SynaxisPlantAccess() {
    }

    static Optional<PlantPort> adjacentPlantPort(CimulinkLevelRuntime runtime, BlockEntity blockEntity,
                                                 EndpointId syntheticEndpointId, String deviceName) {
        Optional<PlantPort> registered = registeredPlantPort(runtime, blockEntity);
        if (registered.isPresent()) {
            return registered;
        }
        Optional<PlantPort> provided = providerPlantPort(blockEntity, syntheticEndpointId, deviceName);
        if (provided.isPresent()) {
            return provided;
        }
        return Optional.empty();
    }

    private static Optional<PlantPort> registeredPlantPort(CimulinkLevelRuntime runtime, BlockEntity blockEntity) {
        if (runtime == null || !(blockEntity instanceof PlantEndpointProvider endpointProvider)) {
            return Optional.empty();
        }
        try {
            return runtime.runtime().gameServices().plantPort(endpointProvider.plantEndpointId());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Optional<PlantPort> providerPlantPort(BlockEntity blockEntity, EndpointId syntheticEndpointId,
                                                         String deviceName) {
        try {
            return PlantPortProviders.tryCreate(blockEntity, syntheticEndpointId, deviceName);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
