package com.mikoalopex.ccsconnector.content;

import com.mikoalopex.ccsconnector.CCSConnector;
import com.mikoalopex.ccsconnector.synaxis.CCSynaxisSuperHubComponent;
import com.mikoalopex.ccsconnector.synaxis.CCSynaxisSuperHubPlantPort;
import com.mikoalopex.ccsconnector.synaxis.SynaxisSignalValues;
import com.verr1.synaxis.foundation.blockentity.NetworkBlockEntity;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentSchema;
import com.verr1.synaxis.foundation.cimulink.core.component.ExecutionDomain;
import com.verr1.synaxis.foundation.cimulink.core.signal.PortDef;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalKind;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalType;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalValue;
import com.verr1.synaxis.foundation.cimulink.game.body.GameThreadPlantPort;
import com.verr1.synaxis.foundation.cimulink.game.body.PhysicsSafePlantPort;
import com.verr1.synaxis.foundation.cimulink.game.body.PlantPort;
import com.verr1.synaxis.foundation.cimulink.game.body.PlantRecord;
import com.verr1.synaxis.foundation.cimulink.game.endpoint.CimulinkEndpoint;
import com.verr1.synaxis.foundation.cimulink.game.endpoint.CimulinkEndpointProvider;
import com.verr1.synaxis.foundation.cimulink.game.endpoint.EndpointAddress;
import com.verr1.synaxis.foundation.cimulink.game.endpoint.EndpointDefinition;
import com.verr1.synaxis.foundation.cimulink.game.endpoint.EndpointId;
import com.verr1.synaxis.foundation.cimulink.game.endpoint.EndpointRuntimeBinding;
import com.verr1.synaxis.foundation.cimulink.game.endpoint.EndpointUiHints;
import com.verr1.synaxis.foundation.cimulink.game.runtime.CimulinkLevelRuntime;
import com.verr1.synaxis.foundation.cimulink.game.runtime.CimulinkWorldRuntimes;
import com.verr1.synaxis.foundation.physics.SynaxisPhysics;
import com.verr1.synaxis.foundation.state.StateSchema;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class CCSynaxisSuperHubBlockEntity extends NetworkBlockEntity implements CimulinkEndpoint, CimulinkEndpointProvider {
    private static final String ENDPOINT_ID_TAG = "EndpointId";
    private static final String ENTRIES_TAG = "Entries";
    private static final String AVAILABLE_DEVICES_TAG = "AvailableDevices";
    private static final String AVAILABLE_PERIPHERALS_TAG = "AvailablePeripherals";
    private static final String STATUS_TAG = "Status";
    private static final EndpointRuntimeBinding BINDING = EndpointRuntimeBinding.allowing();

    private final LinkedHashMap<String, AvailableDevice> availableDevices = new LinkedHashMap<>();
    private final LinkedHashMap<String, SuperHubEntry> entries = new LinkedHashMap<>();
    private final LinkedHashMap<String, SynaxisHubSignal> synaxisValues = new LinkedHashMap<>();
    private final CCSynaxisSuperHubPeripheral peripheral = new CCSynaxisSuperHubPeripheral(this);

    private EndpointId endpointId = EndpointId.random();
    private boolean endpointRegistered;
    private boolean endpointDirty = true;
    private String status = "Ready";
    private long lastScanTick = Long.MIN_VALUE;
    private long lastClientSyncTick = Long.MIN_VALUE;
    private int lastAdjacentCcPeripheralCount;
    private int lastCcMethodCount;
    private int lastSynaxisPlantCount;
    private int lastSynaxisCapabilityCount;
    private int lastMethodDiscoveryFailureCount;
    private int lastCallFailureCount;

    public CCSynaxisSuperHubBlockEntity(BlockPos pos, BlockState state) {
        super(CCSConnector.CC_SYNAXIS_SUPER_HUB_BE.get(), pos, state);
    }

    public IPeripheral peripheral() {
        return peripheral;
    }

    @Override
    protected void defineState(StateSchema.Builder builder) {
    }

    @Override
    protected void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        CimulinkLevelRuntime runtime = CimulinkWorldRuntimes.forLevel(serverLevel);
        if (!endpointRegistered) {
            runtime.registerEndpoint(this);
            endpointRegistered = true;
            endpointDirty = false;
        } else if (endpointDirty) {
            runtime.refreshEndpoint(this);
            endpointDirty = false;
        }
        refreshNow(runtime);
    }

    public CCSynaxisSuperHubPlantPort createPlantPort(EndpointId endpointId, String requestedName) {
        String name = requestedName == null || requestedName.isBlank() ? "CC Synaxis Super Hub" : requestedName;
        return new CCSynaxisSuperHubPlantPort(this, endpointId, name);
    }

    @Override
    public void invalidate() {
        unregisterEndpoint();
        super.invalidate();
    }

    @Override
    public void onChunkUnloaded() {
        unregisterEndpoint();
        super.onChunkUnloaded();
    }

    @Override
    public void remove() {
        unregisterEndpoint();
        super.remove();
    }

    @Override
    public void destroy() {
        unregisterEndpoint();
        super.destroy();
    }

    void unregisterEndpointForRemoval() {
        unregisterEndpoint();
    }

    @Override
    public CimulinkEndpoint endpoint() {
        return this;
    }

    @Override
    public EndpointId id() {
        return endpointId;
    }

    @Override
    public EndpointAddress address() {
        Level level = getLevel();
        if (level == null) {
            return EndpointAddress.of(Level.OVERWORLD, getBlockPos());
        }
        return EndpointAddress.of(level, getBlockPos())
                .withBody(SynaxisPhysics.bodyAt(level, getBlockPos()));
    }

    @Override
    public String displayName() {
        return "CC Synaxis Super Hub";
    }

    @Override
    public EndpointDefinition definition() {
        ComponentSchema schema = schema();
        return new EndpointDefinition(
                CCSynaxisSuperHubComponent.ID,
                new CCSynaxisSuperHubComponent.Config(this, schema),
                schema,
                new EndpointUiHints(portLabels()));
    }

    @Override
    public EndpointRuntimeBinding binding() {
        return BINDING;
    }

    public synchronized Collection<SuperHubEntry> entries() {
        return List.copyOf(entries.values());
    }

    public synchronized Collection<SuperHubEntry> entriesForTarget(SuperHubTarget target) {
        return entries.values().stream()
                .filter(entry -> entry.target() == target)
                .toList();
    }

    public synchronized Collection<SuperHubEntry> inputEntries() {
        return entries.values().stream()
                .filter(entry -> entry.target() == SuperHubTarget.SYNAXIS)
                .filter(SuperHubEntry::input)
                .filter(entry -> entry.type().hasSynaxisPort())
                .toList();
    }

    public synchronized Collection<SuperHubEntry> outputEntries() {
        return entries.values().stream()
                .filter(entry -> entry.target() == SuperHubTarget.SYNAXIS)
                .filter(entry -> !entry.input())
                .filter(entry -> entry.type().hasSynaxisPort())
                .toList();
    }

    public synchronized ComponentSchema schema() {
        List<PortDef> inputs = new ArrayList<>();
        List<PortDef> outputs = new ArrayList<>();
        for (SuperHubEntry entry : entries.values()) {
            if (entry.target() != SuperHubTarget.SYNAXIS || !entry.type().hasSynaxisPort()) {
                continue;
            }
            if (entry.input()) {
                inputs.add(entry.portDef());
            } else {
                outputs.add(entry.portDef());
            }
        }
        return ComponentSchema.of(inputs, outputs);
    }

    public synchronized SignalValue readForSynaxis(String name) {
        SuperHubEntry entry = entryByPortName(SuperHubTarget.SYNAXIS, name);
        if (entry == null || entry.input() || !entry.type().hasSynaxisPort()) {
            return null;
        }
        return entry.signalValue() == null ? defaultSignalValue(entry.type()) : entry.signalValue();
    }

    public void writeFromSynaxis(String name, SignalValue value) {
        SuperHubEntry entry;
        synchronized (this) {
            entry = entryByPortName(SuperHubTarget.SYNAXIS, name);
        }
        if (entry == null || !entry.input() || !entry.type().hasSynaxisPort()) {
            return;
        }
        Object luaValue = luaValueForSignal(entry.type(), value);
        if (luaValue == null) {
            return;
        }
        CimulinkLevelRuntime runtime = runtimeOrNull();
        long tick = level == null ? -1L : level.getGameTime();
        if (!writeEntryValue(entry, luaValue, value, runtime, tick)) {
            return;
        }
        updateEntryValue(entry.id(), luaValue, value, tick);
    }

    public synchronized Object ccLuaValue(String name) throws LuaException {
        SuperHubEntry entry = entryByPortName(SuperHubTarget.CC, name);
        if (entry == null) {
            throw new LuaException("Unknown Super Hub CC exposure '" + name + "'");
        }
        return entry.luaValue();
    }

    public synchronized Map<String, Object> ccLuaValues() {
        Map<String, Object> result = new LinkedHashMap<>();
        entries.values().stream()
                .filter(entry -> entry.target() == SuperHubTarget.CC)
                .forEach(entry -> result.put(entry.portName(), entry.luaValue()));
        return result;
    }

    public boolean writeFromCc(String name, Object luaValue) throws LuaException {
        SuperHubEntry entry;
        synchronized (this) {
            entry = entryByPortName(SuperHubTarget.CC, name);
        }
        if (entry == null) {
            throw new LuaException("Unknown Super Hub CC exposure '" + name + "'");
        }
        if (!entry.input()) {
            throw new LuaException("Exposure '" + name + "' is read-only");
        }
        SignalValue signalValue = signalValueFromLua(entry.type(), luaValue);
        CimulinkLevelRuntime runtime = runtimeOrNull();
        long tick = level == null ? -1L : level.getGameTime();
        if (!writeEntryValue(entry, luaValue, signalValue, runtime, tick)) {
            throw new LuaException("Failed to write exposure '" + name + "'");
        }
        updateEntryValue(entry.id(), normalizedLuaValue(entry.type(), luaValue), signalValue, tick);
        return true;
    }

    public synchronized long lastUpdateTick(String name) throws LuaException {
        SuperHubEntry entry = entryByPortName(SuperHubTarget.CC, name);
        if (entry == null) {
            throw new LuaException("Unknown Super Hub CC exposure '" + name + "'");
        }
        return entry.updateTick();
    }

    public synchronized List<String> availableDeviceNames() {
        return List.copyOf(availableDevices.keySet());
    }

    public synchronized List<Map<String, Object>> availableDeviceInfoList() {
        List<Map<String, Object>> result = new ArrayList<>();
        availableDevices.values().forEach(device -> result.add(device.info()));
        return result;
    }

    public synchronized List<Map<String, Object>> availablePeripheralInfoList() {
        return availableDeviceInfoList();
    }

    public synchronized List<AvailableCapability> capabilitiesForDevice(String deviceKey) {
        AvailableDevice device = availableDevices.get(deviceKey);
        return device == null ? List.of() : device.capabilities();
    }

    public synchronized SuperHubEntry exposureFor(SuperHubTarget target, String capabilityId) {
        return entries.values().stream()
                .filter(entry -> entry.target() == target)
                .filter(entry -> Objects.equals(entry.capabilityId(), capabilityId))
                .findFirst()
                .orElse(null);
    }

    public synchronized List<Map<String, Object>> entryInfoList() {
        List<Map<String, Object>> result = new ArrayList<>();
        entries.values().forEach(entry -> result.add(entry.info()));
        return result;
    }

    public synchronized List<Map<String, Object>> exposedCcInfoList() {
        List<Map<String, Object>> result = new ArrayList<>();
        entries.values().stream()
                .filter(entry -> entry.target() == SuperHubTarget.CC)
                .forEach(entry -> result.add(entry.info()));
        return result;
    }

    public synchronized List<Map<String, Object>> exposedSynaxisInfoList() {
        List<Map<String, Object>> result = new ArrayList<>();
        entries.values().stream()
                .filter(entry -> entry.target() == SuperHubTarget.SYNAXIS)
                .forEach(entry -> result.add(entry.info()));
        return result;
    }

    public synchronized List<Map<String, Object>> synaxisSignalInfoList() {
        List<Map<String, Object>> result = new ArrayList<>();
        synaxisValues.values().forEach(signal -> result.add(signal.info()));
        return result;
    }

    public synchronized Object synaxisLuaValue(String name) throws LuaException {
        SynaxisHubSignal signal = synaxisValues.get(name);
        if (signal == null) {
            throw new LuaException("Unknown Synaxis signal '" + name + "'");
        }
        return signal.luaValue();
    }

    public synchronized Map<String, Object> synaxisLuaValues() {
        Map<String, Object> result = new LinkedHashMap<>();
        synaxisValues.values().forEach(signal -> result.put(signal.name(), signal.luaValue()));
        return result;
    }

    public synchronized long synaxisLastUpdateTick(String name) throws LuaException {
        SynaxisHubSignal signal = synaxisValues.get(name);
        if (signal == null) {
            throw new LuaException("Unknown Synaxis signal '" + name + "'");
        }
        return signal.updateTick();
    }

    public String addExposureFromUi(String targetName, String capabilityId) {
        SuperHubTarget target = SuperHubTarget.parse(targetName);
        AvailableCapability capability;
        synchronized (this) {
            if (exposureFor(target, capabilityId) != null) {
                status = "Already provided to " + target.displayName();
                syncToClient();
                return status;
            }
            capability = capabilityById(capabilityId);
        }
        if (capability == null) {
            status = "Capability not available";
            syncToClient();
            return status;
        }
        if (target == SuperHubTarget.SYNAXIS && !capability.type().hasSynaxisPort()) {
            status = "Cannot expose " + capability.type().serializedName() + " to Synaxis";
            syncToClient();
            return status;
        }
        synchronized (this) {
            String id = uniqueEntryId();
            String portName = uniquePortName(basePortName(capability.deviceName(), capability.memberName()), null, target);
            SuperHubEntry entry = capability.toEntry(id, target, portName);
            entries.put(id, entry);
            if (target == SuperHubTarget.SYNAXIS && capability.type().hasSynaxisPort()) {
                endpointDirty = true;
            }
            status = "Provided " + portName + " to " + target.displayName();
            setChanged();
            syncToClient();
            return status;
        }
    }

    public String removeExposureFromUi(String targetName, String capabilityId) {
        SuperHubTarget target = SuperHubTarget.parse(targetName);
        SuperHubEntry entry;
        synchronized (this) {
            entry = exposureFor(target, capabilityId);
        }
        if (entry == null) {
            status = "Exposure not found";
            syncToClient();
            return status;
        }
        return removeEntryFromUi(entry.id());
    }

    public String removeEntryFromUi(String id) {
        synchronized (this) {
            SuperHubEntry removed = entries.remove(id);
            if (removed == null) {
                status = "Entry not found: " + id;
            } else {
                if (removed.target() == SuperHubTarget.SYNAXIS && removed.type().hasSynaxisPort()) {
                    endpointDirty = true;
                }
                status = "Removed " + removed.portName();
                setChanged();
            }
            syncToClient();
            return status;
        }
    }

    public String status() {
        return status;
    }

    public synchronized String signalSignature() {
        StringBuilder builder = new StringBuilder();
        builder.append("devices=");
        availableDevices.values().forEach(device -> {
            builder.append(device.key())
                    .append('|')
                    .append(device.source().serializedName())
                    .append('|')
                    .append(device.location())
                    .append(';');
            device.capabilities().forEach(capability -> builder.append(capability.id())
                    .append('|')
                    .append(capability.memberName())
                    .append('|')
                    .append(capability.type().serializedName())
                    .append('|')
                    .append(capability.input())
                    .append('|')
                    .append(capability.luaValue())
                    .append(';'));
        });
        builder.append("entries=");
        entries.values().forEach(entry -> builder.append(entry.id())
                .append('|')
                .append(entry.target().serializedName())
                .append('|')
                .append(entry.source().serializedName())
                .append('|')
                .append(entry.capabilityId())
                .append('|')
                .append(entry.portName())
                .append('|')
                .append(entry.type().serializedName())
                .append('|')
                .append(entry.input())
                .append('|')
                .append(entry.luaValue())
                .append(';'));
        builder.append("synaxis=");
        synaxisValues.values().forEach(signal -> builder.append(signal.name())
                .append('|')
                .append(signal.source())
                .append('|')
                .append(signal.type())
                .append('|')
                .append(signal.luaValue())
                .append(';'));
        builder.append("status=").append(status);
        return builder.toString();
    }

    public void refreshNow() {
        CimulinkLevelRuntime runtime = runtimeOrNull();
        if (runtime == null) {
            return;
        }
        refreshNow(runtime);
    }

    private void refreshNow(CimulinkLevelRuntime runtime) {
        if (level == null) {
            return;
        }
        long tick = level.getGameTime();
        lastScanTick = tick;
        lastCallFailureCount = 0;
        lastMethodDiscoveryFailureCount = 0;
        lastSynaxisPlantCount = 0;
        lastSynaxisCapabilityCount = 0;

        LinkedHashMap<String, AvailableDevice> discovered = scanAvailableDevices(runtime, tick);
        applyAvailableDevices(discovered);
        updateConfiguredEntries(runtime, tick);
        applySynaxisSignals(synaxisSignalsFromDevices(discovered));
        updateStatus();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putUUID(ENDPOINT_ID_TAG, endpointId.value());
        ListTag entryTags = new ListTag();
        synchronized (this) {
            entries.values().forEach(entry -> entryTags.add(entry.toTag()));
        }
        tag.put(ENTRIES_TAG, entryTags);
        ListTag deviceTags = new ListTag();
        synchronized (this) {
            availableDevices.values().forEach(device -> deviceTags.add(device.toTag()));
        }
        tag.put(AVAILABLE_DEVICES_TAG, deviceTags);
        tag.putString(STATUS_TAG, status);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.hasUUID(ENDPOINT_ID_TAG)) {
            endpointId = EndpointId.of(tag.getUUID(ENDPOINT_ID_TAG));
        }
        synchronized (this) {
            entries.clear();
            ListTag entryTags = tag.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
            for (Tag entryTag : entryTags) {
                if (entryTag instanceof CompoundTag compoundTag) {
                    SuperHubEntry.fromTag(compoundTag).ifPresent(entry -> entries.put(entry.id(), entry));
                }
            }
            availableDevices.clear();
            String devicesTagName = tag.contains(AVAILABLE_DEVICES_TAG, Tag.TAG_LIST)
                    ? AVAILABLE_DEVICES_TAG
                    : AVAILABLE_PERIPHERALS_TAG;
            ListTag deviceTags = tag.getList(devicesTagName, Tag.TAG_COMPOUND);
            for (Tag deviceTag : deviceTags) {
                if (deviceTag instanceof CompoundTag compoundTag) {
                    AvailableDevice.fromTag(compoundTag).ifPresent(device -> availableDevices.put(device.key(), device));
                }
            }
        }
        status = tag.contains(STATUS_TAG, Tag.TAG_STRING) ? tag.getString(STATUS_TAG) : status;
        endpointDirty = true;
    }

    private LinkedHashMap<String, AvailableDevice> scanAvailableDevices(CimulinkLevelRuntime runtime, long tick) {
        LinkedHashMap<String, AvailableDevice> discovered = new LinkedHashMap<>();
        scanAvailableCcDevices(discovered, tick);
        scanAvailableSynaxisDevices(runtime, tick, discovered);
        return discovered;
    }

    private void scanAvailableCcDevices(LinkedHashMap<String, AvailableDevice> discovered, long tick) {
        Map<String, IPeripheral> adjacent = adjacentPeripherals();
        lastAdjacentCcPeripheralCount = adjacent.size();
        lastCcMethodCount = 0;
        adjacent.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    IPeripheral peripheral = entry.getValue();
                    List<String> methods = readableMethods(CcPeripheralAccess.methodNames(peripheral));
                    if (methods.isEmpty()) {
                        return;
                    }
                    String displayName = peripheralDisplayName(peripheral.getType());
                    String key = uniqueCcDeviceKey(discovered, entry.getKey(), displayName);
                    List<AvailableCapability> capabilities = new ArrayList<>();
                    for (String method : methods) {
                        AvailableCapability capability = ccCapability(
                                key,
                                displayName,
                                entry.getKey(),
                                peripheral.getType(),
                                methods,
                                method,
                                tick);
                        capabilities.add(capability);
                    }
                    lastCcMethodCount += capabilities.size();
                    discovered.put(key, new AvailableDevice(
                            key,
                            SuperHubSource.CC,
                            displayName,
                            entry.getKey(),
                            List.copyOf(capabilities)));
                });
    }

    private AvailableCapability ccCapability(String deviceKey, String displayName, String side, String peripheralType,
                                             List<String> methods, String method, long tick) {
        boolean input = isSetter(method);
        SuperHubSignalType type = inferCcMethodType(side, peripheralType, methods, method);
        Object luaValue = null;
        SignalValue signalValue = null;
        if (!input) {
            Optional<Object[]> result = callPeripheralMethod(side, peripheralType, method);
            if (result.isPresent() && result.get().length > 0) {
                Optional<SuperHubSignalType.SuperHubValue> converted = SuperHubSignalType.fromLua(result.get()[0]);
                if (converted.isPresent()) {
                    type = converted.get().type();
                    luaValue = converted.get().luaValue();
                    signalValue = converted.get().signalValue();
                }
            }
        }
        String capabilityId = "cc:" + side + ":" + peripheralType + ":" + method;
        return new AvailableCapability(
                capabilityId,
                deviceKey,
                SuperHubSource.CC,
                displayName,
                method,
                side,
                side,
                peripheralType,
                method,
                "",
                "",
                "",
                type,
                input,
                luaValue,
                signalValue,
                tick);
    }

    private void scanAvailableSynaxisDevices(CimulinkLevelRuntime runtime, long tick,
                                             LinkedHashMap<String, AvailableDevice> discovered) {
        Set<String> seenEndpoints = new HashSet<>();
        List<PlantRecord> records = runtime.runtime().gameServices().querySelfClusterPlantPorts(endpointId);
        for (PlantRecord record : records) {
            if (record.endpointId().equals(endpointId) || !record.loaded()) {
                continue;
            }
            String endpoint = record.endpointId().toString();
            seenEndpoints.add(endpoint);
            lastSynaxisPlantCount++;
            addSynaxisDevice(discovered, runtime, tick, null, "bus", endpoint, record.deviceName(), record.schema());
        }
        scanAdjacentSynaxisDevices(runtime, tick, discovered, seenEndpoints);
    }

    private void scanAdjacentSynaxisDevices(CimulinkLevelRuntime runtime, long tick,
                                            LinkedHashMap<String, AvailableDevice> discovered,
                                            Set<String> seenEndpoints) {
        Level level = getLevel();
        if (level == null) {
            return;
        }
        for (Direction direction : Direction.values()) {
            BlockPos targetPos = getBlockPos().relative(direction);
            BlockEntity blockEntity = level.getBlockEntity(targetPos);
            if (blockEntity == null) {
                continue;
            }
            Optional<PlantPort> plantPort = adjacentPlantPort(runtime, blockEntity, direction);
            if (plantPort.isEmpty()) {
                continue;
            }
            PlantPort port = plantPort.get();
            String endpoint = port.endpointId().toString();
            if (seenEndpoints.contains(endpoint)) {
                continue;
            }
            seenEndpoints.add(endpoint);
            lastSynaxisPlantCount++;
            addSynaxisDevice(
                    discovered,
                    runtime,
                    tick,
                    port,
                    "adjacent:" + direction.getSerializedName(),
                    endpoint,
                    port.deviceName(),
                    port.schema());
        }
    }

    private void addSynaxisDevice(LinkedHashMap<String, AvailableDevice> discovered, CimulinkLevelRuntime runtime,
                                  long tick, PlantPort directPort, String source, String endpoint, String deviceName,
                                  ComponentSchema schema) {
        String displayName = uniqueDeviceDisplayName(discovered, SuperHubSource.SYNAXIS,
                deviceName == null || deviceName.isBlank() ? "synaxis_device" : sanitizeSignalName(deviceName));
        String key = deviceKey(SuperHubSource.SYNAXIS, displayName);
        List<AvailableCapability> capabilities = new ArrayList<>();
        for (PortDef output : schema.outputs()) {
            signalType(output.type()).ifPresent(type -> {
                SignalValue signalValue = directPort == null
                        ? readPlantOutput(runtime, endpoint, output.name())
                        : readAdjacentPlantOutput(directPort, output.name());
                Object luaValue = signalValue == null ? null : SynaxisSignalValues.toLua(signalValue);
                capabilities.add(synaxisCapability(
                        key,
                        displayName,
                        source,
                        endpoint,
                        deviceName,
                        output.name(),
                        type,
                        false,
                        luaValue,
                        signalValue,
                        tick));
            });
        }
        for (PortDef input : schema.inputs()) {
            signalType(input.type()).ifPresent(type -> capabilities.add(synaxisCapability(
                    key,
                    displayName,
                    source,
                    endpoint,
                    deviceName,
                    input.name(),
                    type,
                    true,
                    null,
                    null,
                    tick)));
        }
        if (capabilities.isEmpty()) {
            return;
        }
        lastSynaxisCapabilityCount += capabilities.size();
        discovered.put(key, new AvailableDevice(
                key,
                SuperHubSource.SYNAXIS,
                displayName,
                source,
                List.copyOf(capabilities)));
    }

    private AvailableCapability synaxisCapability(String deviceKey, String displayName, String source, String endpoint,
                                                  String deviceName, String portName, SuperHubSignalType type,
                                                  boolean input, Object luaValue, SignalValue signalValue, long tick) {
        String capabilityId = "synaxis:" + endpoint + ":" + (input ? "in" : "out") + ":" + portName;
        return new AvailableCapability(
                capabilityId,
                deviceKey,
                SuperHubSource.SYNAXIS,
                displayName,
                portName,
                source,
                "",
                "",
                "",
                endpoint,
                source,
                portName,
                type,
                input,
                luaValue,
                signalValue,
                tick);
    }

    private LinkedHashMap<String, SynaxisHubSignal> synaxisSignalsFromDevices(LinkedHashMap<String, AvailableDevice> devices) {
        LinkedHashMap<String, SynaxisHubSignal> result = new LinkedHashMap<>();
        for (AvailableDevice device : devices.values()) {
            if (device.source() != SuperHubSource.SYNAXIS) {
                continue;
            }
            for (AvailableCapability capability : device.capabilities()) {
                if (capability.input() || capability.signalValue() == null || capability.luaValue() == null) {
                    continue;
                }
                String name = uniqueName(result, synaxisSignalName(device.displayName(), capability.synaxisPort()));
                result.put(name, SynaxisHubSignal.of(
                        name,
                        capability.synaxisSource(),
                        device.displayName(),
                        capability.synaxisPort(),
                        capability.signalValue(),
                        capability.updateTick()));
            }
        }
        return result;
    }

    private Map<String, IPeripheral> adjacentPeripherals() {
        Level level = getLevel();
        return CcPeripheralAccess.adjacentPeripherals(level, getBlockPos(), peripheral);
    }

    private synchronized void applyAvailableDevices(LinkedHashMap<String, AvailableDevice> discovered) {
        if (availableDevices.equals(discovered)) {
            return;
        }
        availableDevices.clear();
        availableDevices.putAll(discovered);
        setChanged();
        syncToClient();
    }

    private void updateConfiguredEntries(CimulinkLevelRuntime runtime, long tick) {
        List<SuperHubEntry> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(entries.values());
        }
        for (SuperHubEntry entry : snapshot) {
            if (!entry.input()) {
                updateOutputEntry(entry, runtime, tick);
            }
        }
    }

    private void updateOutputEntry(SuperHubEntry entry, CimulinkLevelRuntime runtime, long tick) {
        Optional<SuperHubSignalType.SuperHubValue> converted = readEntryValue(entry, runtime);
        if (converted.isEmpty()) {
            return;
        }
        SuperHubSignalType.SuperHubValue value = converted.get();
        synchronized (this) {
            SuperHubEntry current = entries.get(entry.id());
            if (current == null) {
                return;
            }
            boolean schemaChanged = current.type() != value.type();
            SuperHubEntry updated = current.withTypeAndValue(value.type(), value.luaValue(), value.signalValue(), tick);
            if (current.equals(updated)) {
                return;
            }
            entries.put(updated.id(), updated);
            if (current.target() == SuperHubTarget.SYNAXIS
                    && schemaChanged
                    && (current.type().hasSynaxisPort() || updated.type().hasSynaxisPort())) {
                endpointDirty = true;
            }
            if (!Objects.equals(current.luaValue(), updated.luaValue())) {
                peripheral.queueEntryChanged(updated);
            }
            setChanged();
            syncToClient();
        }
    }

    private Optional<SuperHubSignalType.SuperHubValue> readEntryValue(SuperHubEntry entry, CimulinkLevelRuntime runtime) {
        if (entry.source() == SuperHubSource.CC) {
            Optional<Object[]> result = callPeripheralMethod(entry.side(), entry.peripheralType(), entry.method());
            if (result.isEmpty() || result.get().length == 0) {
                return Optional.empty();
            }
            return SuperHubSignalType.fromLua(result.get()[0]);
        }
        SignalValue signalValue = readSynaxisSignal(entry, runtime);
        if (signalValue == null) {
            return Optional.empty();
        }
        return superValueFromSignal(entry.type(), signalValue);
    }

    private boolean writeEntryValue(SuperHubEntry entry, Object luaValue, SignalValue signalValue,
                                    CimulinkLevelRuntime runtime, long tick) {
        if (entry.source() == SuperHubSource.CC) {
            return callPeripheralMethod(entry.side(), entry.peripheralType(), entry.method(), luaValue).isPresent();
        }
        if (runtime == null || signalValue == null) {
            return false;
        }
        return writeSynaxisSignal(entry, signalValue, runtime, tick);
    }

    private void updateEntryValue(String id, Object luaValue, SignalValue signalValue, long tick) {
        synchronized (this) {
            SuperHubEntry current = entries.get(id);
            if (current != null) {
                SuperHubEntry updated = current.withValue(luaValue, signalValue, tick);
                entries.put(updated.id(), updated);
                peripheral.queueEntryChanged(updated);
                setChanged();
                syncToClient();
            }
        }
    }

    private Optional<Object[]> callPeripheralMethod(String side, String peripheralType, String methodName, Object... arguments) {
        Map<String, IPeripheral> adjacent = adjacentPeripherals();
        try {
            return CcPeripheralAccess.call(adjacent, side, peripheralType, methodName, arguments);
        } catch (RuntimeException exception) {
            lastCallFailureCount++;
            return Optional.empty();
        }
    }

    private synchronized void applySynaxisSignals(LinkedHashMap<String, SynaxisHubSignal> discovered) {
        if (synaxisValues.equals(discovered)) {
            return;
        }
        synaxisValues.clear();
        synaxisValues.putAll(discovered);
        setChanged();
        syncToClient();
    }

    private Optional<PlantPort> adjacentPlantPort(CimulinkLevelRuntime runtime, BlockEntity blockEntity, Direction direction) {
        return SynaxisPlantAccess.adjacentPlantPort(
                runtime,
                blockEntity,
                syntheticAdjacentEndpointId(blockEntity, direction),
                "adjacent_" + direction.getSerializedName() + "_" + sanitizeSignalName(blockEntity.getType().toString()));
    }

    private Optional<PlantPort> adjacentPlantPortBySource(CimulinkLevelRuntime runtime, String source, String endpointIdString) {
        Direction direction = adjacentDirection(source);
        if (direction == null || level == null) {
            return Optional.empty();
        }
        BlockEntity blockEntity = level.getBlockEntity(getBlockPos().relative(direction));
        if (blockEntity == null) {
            return Optional.empty();
        }
        Optional<PlantPort> plantPort = adjacentPlantPort(runtime, blockEntity, direction);
        if (plantPort.isEmpty()) {
            return Optional.empty();
        }
        if (!Objects.equals(plantPort.get().endpointId().toString(), endpointIdString)) {
            return Optional.empty();
        }
        return plantPort;
    }

    private SignalValue readSynaxisSignal(SuperHubEntry entry, CimulinkLevelRuntime runtime) {
        if (runtime == null || entry.endpointId().isBlank()) {
            return null;
        }
        if (entry.synaxisSource().startsWith("adjacent:")) {
            Optional<PlantPort> direct = adjacentPlantPortBySource(runtime, entry.synaxisSource(), entry.endpointId());
            if (direct.isPresent()) {
                SignalValue value = readAdjacentPlantOutput(direct.get(), entry.synaxisPort());
                if (value != null) {
                    return value;
                }
            }
        }
        return readPlantOutput(runtime, entry.endpointId(), entry.synaxisPort());
    }

    private SignalValue readPlantOutput(CimulinkLevelRuntime runtime, String endpointIdString, String portName) {
        try {
            return runtime.runtime().gameServices()
                    .readPlantOutput(ExecutionDomain.GAME_TICK, EndpointId.parse(endpointIdString), portName);
        } catch (RuntimeException exception) {
            lastCallFailureCount++;
            return null;
        }
    }

    private SignalValue readAdjacentPlantOutput(PlantPort plantPort, String portName) {
        try {
            if (plantPort instanceof GameThreadPlantPort gameThreadPlantPort
                    && plantPort.supportsDomain(ExecutionDomain.GAME_TICK)) {
                return gameThreadPlantPort.readOutput(portName);
            }
            if (plantPort instanceof PhysicsSafePlantPort physicsSafePlantPort
                    && plantPort.supportsDomain(ExecutionDomain.PHYSICS_SUBSTEP_READONLY)) {
                return physicsSafePlantPort.readPhysicsOutput(portName);
            }
        } catch (RuntimeException exception) {
            lastCallFailureCount++;
        }
        return null;
    }

    private boolean writeSynaxisSignal(SuperHubEntry entry, SignalValue value, CimulinkLevelRuntime runtime, long tick) {
        if (entry.synaxisSource().startsWith("adjacent:")) {
            Optional<PlantPort> direct = adjacentPlantPortBySource(runtime, entry.synaxisSource(), entry.endpointId());
            if (direct.isPresent()) {
                return applyAdjacentPlantInput(direct.get(), entry.synaxisPort(), value, tick);
            }
        }
        try {
            runtime.runtime().gameServices()
                    .writePlantInput(ExecutionDomain.GAME_TICK, EndpointId.parse(entry.endpointId()), entry.synaxisPort(), value, tick);
            return true;
        } catch (RuntimeException exception) {
            lastCallFailureCount++;
            return false;
        }
    }

    private boolean applyAdjacentPlantInput(PlantPort plantPort, String portName, SignalValue value, long tick) {
        try {
            if (plantPort instanceof GameThreadPlantPort gameThreadPlantPort
                    && plantPort.supportsDomain(ExecutionDomain.GAME_TICK)) {
                gameThreadPlantPort.applyInput(portName, value);
                return true;
            }
            if (plantPort instanceof PhysicsSafePlantPort physicsSafePlantPort
                    && plantPort.supportsDomain(ExecutionDomain.PHYSICS_SUBSTEP_CONTROL)) {
                physicsSafePlantPort.writePhysicsInput(portName, value, tick);
                return true;
            }
        } catch (RuntimeException exception) {
            lastCallFailureCount++;
        }
        return false;
    }

    private synchronized Map<String, String> portLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        entries.values().forEach(entry -> {
            if (entry.target() == SuperHubTarget.SYNAXIS && entry.type().hasSynaxisPort()) {
                labels.put(entry.portName(), entry.portName()
                        + " ["
                        + (entry.input() ? "in" : "out")
                        + "] "
                        + entry.type().serializedName());
            }
        });
        return labels;
    }

    private synchronized void updateStatus() {
        long ccExposed = entries.values().stream().filter(entry -> entry.target() == SuperHubTarget.CC).count();
        long synaxisExposed = entries.values().stream().filter(entry -> entry.target() == SuperHubTarget.SYNAXIS).count();
        String nextStatus = "devices: " + availableDevices.size()
                + ", CC peripherals: " + lastAdjacentCcPeripheralCount
                + ", CC methods: " + lastCcMethodCount
                + ", Synaxis plants: " + lastSynaxisPlantCount
                + ", Synaxis caps: " + lastSynaxisCapabilityCount
                + ", to CC: " + ccExposed
                + ", to Synaxis: " + synaxisExposed
                + (lastMethodDiscoveryFailureCount == 0 ? "" : ", method scan failed: " + lastMethodDiscoveryFailureCount)
                + (lastCallFailureCount == 0 ? "" : ", failed: " + lastCallFailureCount);
        if (!Objects.equals(status, nextStatus)) {
            status = nextStatus;
            setChanged();
            syncToClient();
        }
    }

    private void syncToClient() {
        if (level == null || level.isClientSide()) {
            return;
        }
        long tick = level.getGameTime();
        if (lastClientSyncTick == Long.MIN_VALUE || tick - lastClientSyncTick >= 10L) {
            lastClientSyncTick = tick;
            sendData();
        }
    }

    private CimulinkLevelRuntime runtimeOrNull() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return CimulinkWorldRuntimes.forLevel(serverLevel);
    }

    private AvailableCapability capabilityById(String capabilityId) {
        for (AvailableDevice device : availableDevices.values()) {
            for (AvailableCapability capability : device.capabilities()) {
                if (Objects.equals(capability.id(), capabilityId)) {
                    return capability;
                }
            }
        }
        return null;
    }

    private SuperHubSignalType inferCcMethodType(String side, String peripheralType, List<String> methods, String method) {
        if (isSetter(method)) {
            Optional<SuperHubSignalType> pairedType = inferPairedGetterType(side, peripheralType, methods, method.substring(3));
            return pairedType.orElse(SuperHubSignalType.REAL);
        }
        Optional<Object[]> result = callPeripheralMethod(side, peripheralType, method);
        if (result.isPresent() && result.get().length > 0) {
            Optional<SuperHubSignalType.SuperHubValue> value = SuperHubSignalType.fromLua(result.get()[0]);
            if (value.isPresent()) {
                return value.get().type();
            }
        }
        return fallbackType(method);
    }

    private Optional<SuperHubSignalType> inferPairedGetterType(String side, String peripheralType,
                                                              List<String> methods, String suffix) {
        for (String prefix : List.of("get", "is", "has")) {
            String getter = prefix + suffix;
            if (!methods.contains(getter)) {
                continue;
            }
            Optional<Object[]> result = callPeripheralMethod(side, peripheralType, getter);
            if (result.isPresent() && result.get().length > 0) {
                Optional<SuperHubSignalType.SuperHubValue> value = SuperHubSignalType.fromLua(result.get()[0]);
                if (value.isPresent()) {
                    return Optional.of(value.get().type());
                }
            }
        }
        return Optional.empty();
    }

    private synchronized SuperHubEntry entryByPortName(SuperHubTarget target, String name) {
        return entries.values().stream()
                .filter(entry -> entry.target() == target)
                .filter(entry -> Objects.equals(entry.portName(), name))
                .findFirst()
                .orElse(null);
    }

    private synchronized String uniqueEntryId() {
        int index = entries.size() + 1;
        String id = "entry_" + index;
        while (entries.containsKey(id)) {
            index++;
            id = "entry_" + index;
        }
        return id;
    }

    private synchronized String uniquePortName(String base, String currentId, SuperHubTarget target) {
        String name = base;
        int index = 2;
        while (portNameExists(name, currentId, target)) {
            name = base + "_" + index;
            index++;
        }
        return name;
    }

    private synchronized boolean portNameExists(String name, String currentId, SuperHubTarget target) {
        return entries.values().stream()
                .anyMatch(entry -> entry.target() == target
                        && !Objects.equals(entry.id(), currentId)
                        && Objects.equals(entry.portName(), name));
    }

    private String uniqueDeviceDisplayName(Map<String, AvailableDevice> existing, SuperHubSource source, String baseName) {
        String base = source == SuperHubSource.CC ? baseName : baseName;
        String name = base;
        int index = 2;
        while (existing.containsKey(deviceKey(source, name))) {
            name = base + "_" + index;
            index++;
        }
        return name;
    }

    private String uniqueCcDeviceKey(Map<String, AvailableDevice> existing, String side, String displayName) {
        String base = ccDeviceKey(side, displayName);
        String key = base;
        int index = 2;
        while (existing.containsKey(key)) {
            key = base + "_" + index;
            index++;
        }
        return key;
    }

    private EndpointId syntheticAdjacentEndpointId(BlockEntity blockEntity, Direction direction) {
        Level level = getLevel();
        String raw = (level == null ? "unknown" : level.dimension().location().toString())
                + "|"
                + blockEntity.getBlockPos().asLong()
                + "|"
                + direction.getSerializedName()
                + "|"
                + blockEntity.getType();
        return EndpointId.of(UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private void unregisterEndpoint() {
        if (!endpointRegistered || !(level instanceof ServerLevel serverLevel)) {
            endpointRegistered = false;
            return;
        }
        CimulinkWorldRuntimes.forLevel(serverLevel).unregisterEndpoint(endpointId);
        endpointRegistered = false;
    }

    private static List<String> readableMethods(Collection<String> methods) {
        List<String> result = new ArrayList<>();
        for (String method : methods) {
            if (isReadableMethod(method)) {
                result.add(method);
            }
        }
        result.sort(Comparator.comparing(CCSynaxisSuperHubBlockEntity::methodSortKey)
                .thenComparing(Comparator.naturalOrder()));
        return result;
    }

    private static String methodSortKey(String method) {
        if (method.startsWith("get")) {
            return "0_" + method;
        }
        if (method.startsWith("is") || method.startsWith("has")) {
            return "1_" + method;
        }
        if (method.startsWith("set")) {
            return "2_" + method;
        }
        return "3_" + method;
    }

    private static boolean isReadableMethod(String methodName) {
        return methodName != null
                && ((methodName.startsWith("get") && methodName.length() > 3)
                || (methodName.startsWith("is") && methodName.length() > 2)
                || (methodName.startsWith("has") && methodName.length() > 3)
                || (methodName.startsWith("set") && methodName.length() > 3));
    }

    private static boolean isSetter(String methodName) {
        return methodName != null && methodName.startsWith("set") && methodName.length() > 3;
    }

    private static SuperHubSignalType fallbackType(String methodName) {
        if (methodName != null && (methodName.startsWith("is") || methodName.startsWith("has"))) {
            return SuperHubSignalType.BOOLEAN;
        }
        return SuperHubSignalType.REAL;
    }

    private static Optional<SuperHubSignalType> signalType(SignalType type) {
        if (type == null) {
            return Optional.empty();
        }
        SignalKind kind = type.kind();
        if (kind == SignalKind.REAL) {
            return Optional.of(SuperHubSignalType.REAL);
        }
        if (kind == SignalKind.BOOLEAN) {
            return Optional.of(SuperHubSignalType.BOOLEAN);
        }
        return Optional.empty();
    }

    private static Optional<SuperHubSignalType.SuperHubValue> superValueFromSignal(SuperHubSignalType type, SignalValue value) {
        Object luaValue = SynaxisSignalValues.toLua(value);
        if (luaValue == null) {
            return Optional.empty();
        }
        return switch (type) {
            case REAL -> luaValue instanceof Number number
                    ? Optional.of(new SuperHubSignalType.SuperHubValue(
                    SuperHubSignalType.REAL,
                    number.doubleValue(),
                    new SignalValue.Real(number.doubleValue())))
                    : Optional.empty();
            case BOOLEAN -> luaValue instanceof Boolean bool
                    ? Optional.of(new SuperHubSignalType.SuperHubValue(
                    SuperHubSignalType.BOOLEAN,
                    bool,
                    new SignalValue.Bool(bool)))
                    : Optional.empty();
            case TEXT -> luaValue instanceof String text
                    ? Optional.of(new SuperHubSignalType.SuperHubValue(SuperHubSignalType.TEXT, text, null))
                    : Optional.empty();
        };
    }

    private static Object luaValueForSignal(SuperHubSignalType type, SignalValue value) {
        Object luaValue = SynaxisSignalValues.toLua(value);
        return switch (type) {
            case REAL -> luaValue instanceof Number number ? number.doubleValue() : null;
            case BOOLEAN -> luaValue instanceof Boolean bool ? bool : null;
            case TEXT -> luaValue instanceof String string ? string : null;
        };
    }

    private static SignalValue signalValueFromLua(SuperHubSignalType type, Object value) throws LuaException {
        return switch (type) {
            case REAL -> {
                if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
                    yield new SignalValue.Real(number.doubleValue());
                }
                throw new LuaException("Expected finite number");
            }
            case BOOLEAN -> {
                if (value instanceof Boolean bool) {
                    yield new SignalValue.Bool(bool);
                }
                throw new LuaException("Expected boolean");
            }
            case TEXT -> null;
        };
    }

    private static Object normalizedLuaValue(SuperHubSignalType type, Object value) {
        return switch (type) {
            case REAL -> value instanceof Number number ? number.doubleValue() : value;
            case BOOLEAN -> value;
            case TEXT -> value == null ? "" : String.valueOf(value);
        };
    }

    private static SignalValue defaultSignalValue(SuperHubSignalType type) {
        return switch (type) {
            case REAL -> new SignalValue.Real(0.0D);
            case BOOLEAN -> new SignalValue.Bool(false);
            case TEXT -> null;
        };
    }

    private static Direction adjacentDirection(String source) {
        if (source == null || !source.startsWith("adjacent:")) {
            return null;
        }
        String name = source.substring("adjacent:".length());
        for (Direction direction : Direction.values()) {
            if (Objects.equals(direction.getSerializedName(), name)) {
                return direction;
            }
        }
        return null;
    }

    private static String deviceKey(SuperHubSource source, String displayName) {
        return "[" + source.displayName() + "] " + displayName;
    }

    private static String ccDeviceKey(String side, String displayName) {
        String sideName = side == null || side.isBlank() ? "side" : side;
        return "[CC:" + sideName + "] " + displayName;
    }

    private static String peripheralDisplayName(String type) {
        String name = type == null || type.isBlank() ? "peripheral" : type.toLowerCase(Locale.ROOT);
        return sanitizeSignalName(name);
    }

    private static String basePortName(String peripheralName, String memberName) {
        return sanitizeSignalName((peripheralName == null ? "device" : peripheralName)
                + "_"
                + (memberName == null || memberName.isBlank() ? "value" : memberName.toLowerCase(Locale.ROOT)));
    }

    private static String synaxisSignalName(String deviceName, String portName) {
        String device = deviceName == null || deviceName.isBlank() ? "synaxis" : deviceName;
        String port = portName == null || portName.isBlank() ? "value" : portName;
        return sanitizeSignalName(device + "_" + port);
    }

    private static String uniqueName(Map<String, ?> existing, String base) {
        String name = base;
        int index = 2;
        while (existing.containsKey(name)) {
            name = base + "_" + index;
            index++;
        }
        return name;
    }

    private static String sanitizeSignalName(String raw) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        if (builder.isEmpty() || Character.isDigit(builder.charAt(0))) {
            builder.insert(0, "signal_");
        }
        return builder.toString();
    }

    public record AvailableDevice(
            String key,
            SuperHubSource source,
            String displayName,
            String location,
            List<AvailableCapability> capabilities) {

        public Map<String, Object> info() {
            Map<String, Object> info = new LinkedHashMap<>();
            Map<Integer, Object> capabilityTable = new LinkedHashMap<>();
            int index = 1;
            for (AvailableCapability capability : capabilities) {
                capabilityTable.put(index++, capability.info(null));
            }
            info.put("name", key);
            info.put("displayName", displayName);
            info.put("source", source.serializedName());
            info.put("location", location);
            info.put("capabilities", capabilityTable);
            return info;
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Key", key);
            tag.putString("Source", source.serializedName());
            tag.putString("DisplayName", displayName);
            tag.putString("Location", location);
            ListTag capabilityTags = new ListTag();
            capabilities.forEach(capability -> capabilityTags.add(capability.toTag()));
            tag.put("Capabilities", capabilityTags);
            return tag;
        }

        public static Optional<AvailableDevice> fromTag(CompoundTag tag) {
            String key = tag.contains("Key", Tag.TAG_STRING) ? tag.getString("Key") : tag.getString("DisplayName");
            if (key.isBlank()) {
                return Optional.empty();
            }
            SuperHubSource source = tag.contains("Source", Tag.TAG_STRING)
                    ? SuperHubSource.parse(tag.getString("Source"))
                    : SuperHubSource.CC;
            List<AvailableCapability> capabilities = new ArrayList<>();
            ListTag capabilityTags = tag.getList("Capabilities", Tag.TAG_COMPOUND);
            for (Tag capabilityTag : capabilityTags) {
                if (capabilityTag instanceof CompoundTag compoundTag) {
                    AvailableCapability.fromTag(compoundTag).ifPresent(capabilities::add);
                }
            }
            if (capabilities.isEmpty() && tag.contains("Methods", Tag.TAG_LIST)) {
                String displayName = tag.getString("DisplayName");
                String side = tag.getString("Side");
                String peripheralType = tag.getString("PeripheralType");
                ListTag methodTags = tag.getList("Methods", Tag.TAG_STRING);
                for (Tag methodTag : methodTags) {
                    if (methodTag instanceof StringTag stringTag) {
                        String method = stringTag.getAsString();
                        capabilities.add(new AvailableCapability(
                                "cc:" + side + ":" + peripheralType + ":" + method,
                                key,
                                SuperHubSource.CC,
                                displayName,
                                method,
                                side,
                                side,
                                peripheralType,
                                method,
                                "",
                                "",
                                "",
                                fallbackType(method),
                                isSetter(method),
                                null,
                                null,
                                -1L));
                    }
                }
            }
            return Optional.of(new AvailableDevice(
                    key,
                    source,
                    tag.getString("DisplayName"),
                    tag.getString("Location"),
                    List.copyOf(capabilities)));
        }
    }

    public record AvailableCapability(
            String id,
            String deviceKey,
            SuperHubSource source,
            String deviceName,
            String memberName,
            String location,
            String side,
            String peripheralType,
            String method,
            String endpointId,
            String synaxisSource,
            String synaxisPort,
            SuperHubSignalType type,
            boolean input,
            Object luaValue,
            SignalValue signalValue,
            long updateTick) {

        public String accessName() {
            return input ? "write" : "read";
        }

        public String targetFlowName(SuperHubTarget target) {
            if (target == SuperHubTarget.SYNAXIS) {
                return input ? "in" : "out";
            }
            return accessName();
        }

        public SuperHubEntry toEntry(String id, SuperHubTarget target, String portName) {
            return new SuperHubEntry(
                    id,
                    target,
                    source,
                    this.id,
                    deviceKey,
                    portName,
                    deviceName,
                    memberName,
                    side,
                    peripheralType,
                    method,
                    endpointId,
                    synaxisSource,
                    source == SuperHubSource.SYNAXIS ? deviceName : "",
                    synaxisPort,
                    type,
                    input,
                    luaValue,
                    signalValue,
                    updateTick);
        }

        public Map<String, Object> info(SuperHubTarget target) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", id);
            info.put("deviceKey", deviceKey);
            info.put("source", source.serializedName());
            info.put("deviceName", deviceName);
            info.put("memberName", memberName);
            info.put("location", location);
            info.put("type", type.serializedName());
            info.put("access", accessName());
            if (target != null) {
                info.put("providesAs", targetFlowName(target));
            }
            info.put("side", side);
            info.put("peripheralType", peripheralType);
            info.put("method", method);
            info.put("endpointId", endpointId);
            info.put("synaxisSource", synaxisSource);
            info.put("synaxisPort", synaxisPort);
            info.put("value", luaValue);
            info.put("lastUpdateTick", updateTick);
            return info;
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", id);
            tag.putString("DeviceKey", deviceKey);
            tag.putString("Source", source.serializedName());
            tag.putString("DeviceName", deviceName);
            tag.putString("MemberName", memberName);
            tag.putString("Location", location);
            tag.putString("Side", side);
            tag.putString("PeripheralType", peripheralType);
            tag.putString("Method", method);
            tag.putString("EndpointId", endpointId);
            tag.putString("SynaxisSource", synaxisSource);
            tag.putString("SynaxisPort", synaxisPort);
            tag.putString("Type", type.serializedName());
            tag.putBoolean("Input", input);
            tag.putLong("UpdateTick", updateTick);
            switch (type) {
                case REAL -> tag.putDouble("Value", luaValue instanceof Number number ? number.doubleValue() : 0.0D);
                case BOOLEAN -> tag.putBoolean("Value", luaValue instanceof Boolean bool && bool);
                case TEXT -> tag.putString("Value", luaValue == null ? "" : String.valueOf(luaValue));
            }
            if (signalValue != null) {
                tag.put("SignalValue", SynaxisSignalValues.toTag(signalValue));
            }
            return tag;
        }

        public static Optional<AvailableCapability> fromTag(CompoundTag tag) {
            String id = tag.getString("Id");
            if (id.isBlank()) {
                return Optional.empty();
            }
            SuperHubSignalType type = SuperHubSignalType.parse(tag.getString("Type"));
            Object value = switch (type) {
                case REAL -> tag.getDouble("Value");
                case BOOLEAN -> tag.getBoolean("Value");
                case TEXT -> tag.getString("Value");
            };
            SignalValue signalValue = null;
            if (tag.contains("SignalValue", Tag.TAG_COMPOUND)) {
                signalValue = SynaxisSignalValues.fromTag(tag.getCompound("SignalValue")).orElse(null);
            } else if (type == SuperHubSignalType.REAL && value instanceof Number number) {
                signalValue = new SignalValue.Real(number.doubleValue());
            } else if (type == SuperHubSignalType.BOOLEAN && value instanceof Boolean bool) {
                signalValue = new SignalValue.Bool(bool);
            }
            return Optional.of(new AvailableCapability(
                    id,
                    tag.getString("DeviceKey"),
                    SuperHubSource.parse(tag.getString("Source")),
                    tag.getString("DeviceName"),
                    tag.getString("MemberName"),
                    tag.getString("Location"),
                    tag.getString("Side"),
                    tag.getString("PeripheralType"),
                    tag.getString("Method"),
                    tag.getString("EndpointId"),
                    tag.getString("SynaxisSource"),
                    tag.getString("SynaxisPort"),
                    type,
                    tag.getBoolean("Input"),
                    value,
                    signalValue,
                    tag.getLong("UpdateTick")));
        }
    }
}
