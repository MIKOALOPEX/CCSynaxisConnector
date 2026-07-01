package com.mikoalopex.ccsconnector.content;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.asm.PeripheralMethodSupplier;
import dan200.computercraft.core.methods.MethodSupplier;
import dan200.computercraft.core.methods.PeripheralMethod;
import dan200.computercraft.impl.GenericSources;
import dan200.computercraft.impl.Peripherals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Centralizes optional CC:T implementation fallbacks used to match computer peripheral discovery.
final class CcTweakedInternals {
    private static MethodSupplier<PeripheralMethod> peripheralMethodSupplier;

    private CcTweakedInternals() {
    }

    static IPeripheral genericPeripheral(Level level, BlockPos targetPos, Direction side) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(targetPos);
        if (blockEntity == null) {
            return null;
        }
        try {
            return Peripherals.getGenericPeripheral(serverLevel, targetPos, side, blockEntity);
        } catch (LinkageError | RuntimeException exception) {
            return null;
        }
    }

    static List<String> methodNames(IPeripheral peripheral) {
        try {
            return new ArrayList<>(methodMap(peripheral).keySet());
        } catch (LinkageError | RuntimeException exception) {
            return List.of();
        }
    }

    static Optional<Object[]> call(IPeripheral peripheral, Map<String, IPeripheral> adjacent,
                                   String methodName, Object... arguments) {
        PeripheralMethod method;
        try {
            method = methodMap(peripheral).get(methodName);
        } catch (LinkageError | RuntimeException exception) {
            return Optional.empty();
        }
        if (method == null) {
            return Optional.empty();
        }
        try {
            MethodResult result = method.apply(
                    peripheral,
                    SuperHubPeripheralCall.DIRECT_CONTEXT,
                    SuperHubPeripheralCall.computerAccess("super_hub", adjacent),
                    SuperHubPeripheralCall.arguments(arguments));
            return CcPeripheralAccess.resolve(result);
        } catch (LuaException | LinkageError | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Map<String, PeripheralMethod> methodMap(IPeripheral peripheral) {
        if (peripheral == null) {
            return Map.of();
        }
        return new LinkedHashMap<>(peripheralMethods().getSelfMethods(peripheral));
    }

    private static MethodSupplier<PeripheralMethod> peripheralMethods() {
        MethodSupplier<PeripheralMethod> supplier = peripheralMethodSupplier;
        if (supplier == null) {
            supplier = PeripheralMethodSupplier.create(List.copyOf(GenericSources.getAllMethods()));
            peripheralMethodSupplier = supplier;
        }
        return supplier;
    }
}
