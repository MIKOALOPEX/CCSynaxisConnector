package com.mikoalopex.ccsconnector.content;

import com.mikoalopex.ccsconnector.synaxis.SynaxisSignalValues;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalValue;
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

public final class CCSynaxisBridgePeripheral implements IDynamicPeripheral {
    private static final String TYPE = "cc_synaxis_bridge";
    private static final String LEGACY_TYPE = "ccsconnector_bridge";
    private static final String[] METHODS = {
            "list",
            "schema",
            "info",
            "addBridge",
            "removeBridge",
            "get",
            "set",
            "lastWriteTick",
            "getSaveLastValue",
            "setSaveLastValue",
            "toggleSaveLastValue"
    };

    private final CCSynaxisBridgeBlockEntity blockEntity;
    private final AttachedComputerSet computers = new AttachedComputerSet();

    CCSynaxisBridgePeripheral(CCSynaxisBridgeBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Set<String> getAdditionalTypes() {
        return Set.of(LEGACY_TYPE);
    }

    @Override
    public String[] getMethodNames() {
        return METHODS;
    }

    @Override
    public MethodResult callMethod(IComputerAccess computer, ILuaContext context, int method, IArguments arguments)
            throws LuaException {
        return switch (method) {
            case 0, 1 -> MethodResult.of(luaArray(blockEntity.channelInfoList()));
            case 2 -> MethodResult.of(info(arguments.getString(0)));
            case 3 -> addBridge(arguments);
            case 4 -> MethodResult.of(blockEntity.removeChannel(arguments.getString(0)));
            case 5 -> MethodResult.of(SynaxisSignalValues.toLua(blockEntity.readForCc(arguments.getString(0))));
            case 6 -> set(arguments);
            case 7 -> MethodResult.of(blockEntity.lastWriteTick(arguments.getString(0)));
            case 8 -> MethodResult.of(blockEntity.saveLastValue(arguments.getString(0)));
            case 9 -> MethodResult.of(blockEntity.setSaveLastValue(arguments.getString(0), arguments.getBoolean(1)));
            case 10 -> MethodResult.of(blockEntity.toggleSaveLastValue(arguments.getString(0)));
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
        return other instanceof CCSynaxisBridgePeripheral peripheral
                && peripheral.blockEntity == blockEntity;
    }

    void queueInputEvent(String bridge, SignalValue value) {
        computers.queueEvent(TYPE, bridge, SynaxisSignalValues.toLua(value));
    }

    private Object info(String name) throws LuaException {
        BridgeChannel channel = blockEntity.channel(name)
                .orElseThrow(() -> new LuaException("Unknown bridge '" + name + "'"));
        return blockEntity.channelInfo(channel);
    }

    private MethodResult addBridge(IArguments arguments) throws LuaException {
        String name;
        String type;
        String direction;
        if (arguments.count() == 2) {
            name = null;
            type = arguments.getString(0);
            direction = arguments.getString(1);
        } else if (arguments.count() >= 3) {
            Object rawName = arguments.get(0);
            name = rawName == null ? null : arguments.getString(0);
            type = arguments.getString(1);
            direction = arguments.getString(2);
        } else {
            throw new LuaException("Expected addBridge([name], type, direction)");
        }

        BridgeChannel channel = blockEntity.addChannel(
                name,
                BridgeSignalType.parse(type),
                BridgeDirection.parse(direction));
        return MethodResult.of(blockEntity.channelInfo(channel));
    }

    private MethodResult set(IArguments arguments) throws LuaException {
        String name = arguments.getString(0);
        BridgeChannel channel = blockEntity.channel(name)
                .orElseThrow(() -> new LuaException("Unknown bridge '" + name + "'"));
        SignalValue value = SynaxisSignalValues.fromLua(channel.type(), arguments.get(1));
        blockEntity.writeFromCc(channel.name(), value);
        return MethodResult.of(true);
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
