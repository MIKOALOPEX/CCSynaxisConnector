package com.mikoalopex.ccsconnector.content;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.AttachedComputerSet;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CCSynaxisSuperHubPeripheral implements IDynamicPeripheral {
    private static final String TYPE = "cc_synaxis_super_hub";
    private static final String[] METHODS = {
            "list",
            "schema",
            "get",
            "getAll",
            "refresh",
            "lastUpdateTick",
            "listSynaxis",
            "getSynaxis",
            "getAllSynaxis",
            "synaxisLastUpdateTick",
            "listCcCandidates",
            "listExposedCc",
            "getCc",
            "getAllCc",
            "expose",
            "toggleExpose",
            "set",
            "write",
            "listProvidedToSynaxis"
    };

    private final CCSynaxisSuperHubBlockEntity blockEntity;
    private final AttachedComputerSet computers = new AttachedComputerSet();

    CCSynaxisSuperHubPeripheral(CCSynaxisSuperHubBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Set<String> getAdditionalTypes() {
        return Set.of("ccsconnector_super_hub");
    }

    @Override
    public String[] getMethodNames() {
        return METHODS;
    }

    @Override
    public MethodResult callMethod(IComputerAccess computer, ILuaContext context, int method, IArguments arguments)
            throws LuaException {
        return switch (method) {
            case 0, 1 -> MethodResult.of(luaArray(blockEntity.exposedCcInfoList()));
            case 2 -> MethodResult.of(blockEntity.ccLuaValue(arguments.getString(0)));
            case 3 -> MethodResult.of(blockEntity.ccLuaValues());
            case 4 -> {
                blockEntity.refreshNow();
                yield MethodResult.of(luaArray(blockEntity.exposedCcInfoList()));
            }
            case 5 -> MethodResult.of(blockEntity.lastUpdateTick(arguments.getString(0)));
            case 6 -> MethodResult.of(luaArray(blockEntity.synaxisSignalInfoList()));
            case 7 -> MethodResult.of(blockEntity.synaxisLuaValue(arguments.getString(0)));
            case 8 -> MethodResult.of(blockEntity.synaxisLuaValues());
            case 9 -> MethodResult.of(blockEntity.synaxisLastUpdateTick(arguments.getString(0)));
            case 10 -> MethodResult.of(luaArray(blockEntity.availableDeviceInfoList()));
            case 11 -> MethodResult.of(luaArray(blockEntity.exposedCcInfoList()));
            case 12 -> MethodResult.of(blockEntity.ccLuaValue(arguments.getString(0)));
            case 13 -> MethodResult.of(blockEntity.ccLuaValues());
            case 14, 15 -> throw new LuaException("Configure exposure in the Super Hub UI");
            case 16, 17 -> MethodResult.of(blockEntity.writeFromCc(arguments.getString(0), arguments.get(1)));
            case 18 -> MethodResult.of(luaArray(blockEntity.exposedSynaxisInfoList()));
            default -> throw new LuaException("Unknown method");
        };
    }

    @Override
    public void attach(IComputerAccess computer) {
        computers.add(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        computers.remove(computer);
    }

    @Override
    public Object getTarget() {
        return blockEntity;
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof CCSynaxisSuperHubPeripheral peripheral
                && peripheral.blockEntity == blockEntity;
    }

    void queueEntryChanged(SuperHubEntry entry) {
        if (entry.target() == SuperHubTarget.CC) {
            computers.queueEvent(TYPE, entry.portName(), entry.luaValue(), entry.type().serializedName());
        }
    }

    private static Map<Integer, Object> luaArray(List<Map<String, Object>> values) {
        Map<Integer, Object> result = new LinkedHashMap<>();
        int index = 1;
        for (Map<String, Object> value : values) {
            result.put(index++, value);
        }
        return result;
    }
}
