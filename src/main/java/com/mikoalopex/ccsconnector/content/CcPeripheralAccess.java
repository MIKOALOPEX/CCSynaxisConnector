package com.mikoalopex.ccsconnector.content;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class CcPeripheralAccess {
    private static final String ATTACHMENT_NAME = "super_hub";

    private CcPeripheralAccess() {
    }

    static Map<String, IPeripheral> adjacentPeripherals(Level level, BlockPos origin, IPeripheral self) {
        Map<String, IPeripheral> result = new LinkedHashMap<>();
        if (level == null) {
            return result;
        }
        for (Direction direction : Direction.values()) {
            BlockPos targetPos = origin.relative(direction);
            IPeripheral peripheral = findPeripheral(level, targetPos, direction.getOpposite());
            if (peripheral != null && peripheral != self) {
                result.put(direction.getSerializedName(), peripheral);
            }
        }
        return result;
    }

    static List<String> methodNames(IPeripheral peripheral) {
        if (peripheral == null) {
            return List.of();
        }
        if (peripheral instanceof IDynamicPeripheral dynamicPeripheral) {
            try {
                return Arrays.stream(dynamicPeripheral.getMethodNames())
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
            } catch (RuntimeException exception) {
                return List.of();
            }
        }
        return CcTweakedInternals.methodNames(peripheral);
    }

    static Optional<Object[]> call(Map<String, IPeripheral> adjacent, String side, String peripheralType,
                                   String methodName, Object... arguments) {
        IPeripheral peripheral = adjacent.get(side);
        if (peripheral == null || !Objects.equals(peripheral.getType(), peripheralType)) {
            peripheral = adjacent.values().stream()
                    .filter(candidate -> Objects.equals(candidate.getType(), peripheralType))
                    .findFirst()
                    .orElse(null);
        }
        if (peripheral == null) {
            return Optional.empty();
        }
        if (peripheral instanceof IDynamicPeripheral dynamicPeripheral) {
            return callDynamic(dynamicPeripheral, adjacent, methodName, arguments);
        }
        return CcTweakedInternals.call(peripheral, adjacent, methodName, arguments);
    }

    private static IPeripheral findPeripheral(Level level, BlockPos targetPos, Direction contactSide) {
        IPeripheral peripheral = level.getCapability(PeripheralCapability.get(), targetPos, contactSide);
        if (peripheral != null) {
            return peripheral;
        }
        peripheral = CcTweakedInternals.genericPeripheral(level, targetPos, contactSide);
        if (peripheral != null) {
            return peripheral;
        }
        peripheral = level.getCapability(PeripheralCapability.get(), targetPos, null);
        if (peripheral != null) {
            return peripheral;
        }
        for (Direction side : Direction.values()) {
            peripheral = level.getCapability(PeripheralCapability.get(), targetPos, side);
            if (peripheral != null) {
                return peripheral;
            }
            peripheral = CcTweakedInternals.genericPeripheral(level, targetPos, side);
            if (peripheral != null) {
                return peripheral;
            }
        }
        return null;
    }

    private static Optional<Object[]> callDynamic(IDynamicPeripheral peripheral, Map<String, IPeripheral> adjacent,
                                                  String methodName, Object... arguments) {
        String[] methodNames;
        try {
            methodNames = peripheral.getMethodNames();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        for (int index = 0; index < methodNames.length; index++) {
            if (!Objects.equals(methodNames[index], methodName)) {
                continue;
            }
            try {
                MethodResult result = peripheral.callMethod(
                        SuperHubPeripheralCall.computerAccess(ATTACHMENT_NAME, adjacent),
                        SuperHubPeripheralCall.DIRECT_CONTEXT,
                        index,
                        SuperHubPeripheralCall.arguments(arguments));
                return resolve(result);
            } catch (LuaException | RuntimeException exception) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    static Optional<Object[]> resolve(MethodResult result) throws LuaException {
        result = SuperHubPeripheralCall.resolveCompletedTask(result);
        if (result.getCallback() != null) {
            return Optional.empty();
        }
        Object[] values = result.getResult();
        return Optional.of(values == null ? new Object[0] : values);
    }
}
