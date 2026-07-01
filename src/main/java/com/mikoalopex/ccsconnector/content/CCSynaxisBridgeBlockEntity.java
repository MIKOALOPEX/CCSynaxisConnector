package com.mikoalopex.ccsconnector.content;

import com.mikoalopex.ccsconnector.CCSConnector;
import com.mikoalopex.ccsconnector.synaxis.CCSynaxisBridgeComponent;
import com.mikoalopex.ccsconnector.synaxis.CCSynaxisPlantPort;
import com.mikoalopex.ccsconnector.synaxis.SynaxisSignalValues;
import com.verr1.synaxis.foundation.blockentity.NetworkBlockEntity;
import com.verr1.synaxis.foundation.command.CommandKey;
import com.verr1.synaxis.foundation.command.CommandRegistry;
import com.verr1.synaxis.foundation.cimulink.core.component.ComponentSchema;
import com.verr1.synaxis.foundation.cimulink.core.signal.PortDef;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalValue;
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
import com.verr1.synaxis.foundation.state.StateKey;
import com.verr1.synaxis.foundation.state.StateSchema;
import com.verr1.synaxis.foundation.state.SyncApplyMode;
import com.verr1.synaxis.foundation.state.SyncTarget;
import com.verr1.synaxis.foundation.ui.OptionSet;
import com.verr1.synaxis.foundation.ui.UiSchema;
import com.verr1.synaxis.foundation.ui.Unit;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CCSynaxisBridgeBlockEntity extends NetworkBlockEntity implements CimulinkEndpoint, CimulinkEndpointProvider {
    private static final String ENDPOINT_ID_TAG = "EndpointId";
    private static final String CHANNELS_TAG = "Bridges";
    private static final String VALUES_TAG = "Values";
    private static final String LAST_WRITE_TICKS_TAG = "LastWriteTicks";
    private static final String UI_STATUS_TAG = "UiStatus";

    private static final EndpointRuntimeBinding BINDING = EndpointRuntimeBinding.allowing();
    private static final String DEFAULT_DRAFT_TYPE = "real";
    private static final String DEFAULT_DRAFT_DIRECTION = "cc_to_synaxis";

    private static final StateKey<String> UI_NEW_NAME =
            StateKey.string(CCSConnector.id("bridge_ui/new_name"), "");
    private static final StateKey<String> UI_NEW_TYPE =
            StateKey.string(CCSConnector.id("bridge_ui/new_type"), DEFAULT_DRAFT_TYPE);
    private static final StateKey<String> UI_NEW_DIRECTION =
            StateKey.string(CCSConnector.id("bridge_ui/new_direction"), DEFAULT_DRAFT_DIRECTION);
    private static final StateKey<String> UI_REMOVE_NAME =
            StateKey.string(CCSConnector.id("bridge_ui/remove_name"), "");
    private static final StateKey<String> UI_CHANNELS =
            StateKey.string(CCSConnector.id("bridge_ui/channels"), "");
    private static final StateKey<String> UI_STATUS =
            StateKey.string(CCSConnector.id("bridge_ui/status"), "");

    private static final CommandKey<String> SET_UI_NEW_NAME =
            CommandKey.fromState(CCSConnector.id("bridge_ui/set_new_name"), UI_NEW_NAME);
    private static final CommandKey<String> SET_UI_NEW_TYPE =
            CommandKey.fromState(CCSConnector.id("bridge_ui/set_new_type"), UI_NEW_TYPE);
    private static final CommandKey<String> SET_UI_NEW_DIRECTION =
            CommandKey.fromState(CCSConnector.id("bridge_ui/set_new_direction"), UI_NEW_DIRECTION);
    private static final CommandKey<String> SET_UI_REMOVE_NAME =
            CommandKey.fromState(CCSConnector.id("bridge_ui/set_remove_name"), UI_REMOVE_NAME);
    private static final CommandKey<Unit> ADD_BRIDGE =
            CommandKey.of(CCSConnector.id("bridge_ui/add"), StreamCodec.unit(Unit.INSTANCE));
    private static final CommandKey<Unit> REMOVE_BRIDGE =
            CommandKey.of(CCSConnector.id("bridge_ui/remove"), StreamCodec.unit(Unit.INSTANCE));

    private static final OptionSet<String> BRIDGE_TYPE_OPTIONS = OptionSet.of(
            String.class,
            List.of("real", "boolean"),
            value -> Component.literal(value));
    private static final OptionSet<String> BRIDGE_DIRECTION_OPTIONS = OptionSet.of(
            String.class,
            List.of("cc_to_synaxis", "synaxis_to_cc"),
            value -> Component.literal(value));

    private final LinkedHashMap<String, BridgeChannel> channels = new LinkedHashMap<>();
    private final ConcurrentMap<String, SignalValue> values = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> lastWriteTicks = new ConcurrentHashMap<>();
    private final CCSynaxisBridgePeripheral peripheral = new CCSynaxisBridgePeripheral(this);

    private String uiNewName = "";
    private String uiNewType = DEFAULT_DRAFT_TYPE;
    private String uiNewDirection = DEFAULT_DRAFT_DIRECTION;
    private String uiRemoveName = "";
    private String uiChannelsSnapshot = "No bridges";
    private String uiStatus = "Ready";

    private EndpointId endpointId = EndpointId.random();
    private boolean endpointRegistered;
    private boolean endpointDirty = true;
    private long lastClientValueSyncTick = Long.MIN_VALUE;

    public CCSynaxisBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(CCSConnector.CC_SYNAXIS_BRIDGE_BE.get(), pos, state);
    }

    public IPeripheral peripheral() {
        return peripheral;
    }

    @Override
    protected void defineState(StateSchema.Builder builder) {
        builder.field(UI_NEW_NAME)
                .runtime()
                .editable()
                .sync(SyncTarget.UI)
                .syncApply(SyncApplyMode.REPLICA_ONLY)
                .bind(() -> uiNewName, value -> uiNewName = value == null ? "" : value);
        builder.field(UI_NEW_TYPE)
                .runtime()
                .editable()
                .sync(SyncTarget.UI)
                .syncApply(SyncApplyMode.REPLICA_ONLY)
                .validate(CCSynaxisBridgeBlockEntity::isValidTypeName)
                .bind(() -> uiNewType, value -> uiNewType = isValidTypeName(value) ? value : DEFAULT_DRAFT_TYPE);
        builder.field(UI_NEW_DIRECTION)
                .runtime()
                .editable()
                .sync(SyncTarget.UI)
                .syncApply(SyncApplyMode.REPLICA_ONLY)
                .validate(CCSynaxisBridgeBlockEntity::isValidDirectionName)
                .bind(() -> uiNewDirection, value -> uiNewDirection = isValidDirectionName(value) ? value : DEFAULT_DRAFT_DIRECTION);
        builder.field(UI_REMOVE_NAME)
                .runtime()
                .editable()
                .sync(SyncTarget.UI)
                .syncApply(SyncApplyMode.REPLICA_ONLY)
                .bind(() -> uiRemoveName, value -> uiRemoveName = value == null ? "" : value);
        builder.field(UI_CHANNELS)
                .runtime()
                .serverOnly()
                .sync(SyncTarget.UI)
                .syncApply(SyncApplyMode.SET_AUTHORITATIVE)
                .bind(() -> uiChannelsSnapshot, value -> uiChannelsSnapshot = value == null ? "" : value);
        builder.field(UI_STATUS)
                .runtime()
                .serverOnly()
                .sync(SyncTarget.UI)
                .syncApply(SyncApplyMode.SET_AUTHORITATIVE)
                .bind(() -> uiStatus, value -> uiStatus = value == null ? "" : value);
    }

    @Override
    protected void defineCommands(CommandRegistry commands) {
        commands.registerSetField(SET_UI_NEW_NAME, UI_NEW_NAME);
        commands.registerSetField(SET_UI_NEW_TYPE, UI_NEW_TYPE);
        commands.registerSetField(SET_UI_NEW_DIRECTION, UI_NEW_DIRECTION);
        commands.registerSetField(SET_UI_REMOVE_NAME, UI_REMOVE_NAME);
        commands.register(ADD_BRIDGE, (context, ignored) -> addChannelFromUi(context.player()));
        commands.register(REMOVE_BRIDGE, (context, ignored) -> removeChannelFromUi(context.player()));
    }

    @Override
    protected void defineUi(UiSchema.Builder builder) {
        builder.page("bridges", Component.literal("Bridges"))
                .group("create", Component.literal("Create Bridge"))
                .text(UI_NEW_NAME)
                .id("new_bridge_name")
                .label(Component.literal("Name"))
                .valueTooltip(Component.literal("Leave empty to allocate bridge_1, bridge_2, ..."))
                .command(SET_UI_NEW_NAME)
                .option(UI_NEW_TYPE, BRIDGE_TYPE_OPTIONS)
                .id("new_bridge_type")
                .label(Component.literal("Type"))
                .command(SET_UI_NEW_TYPE)
                .option(UI_NEW_DIRECTION, BRIDGE_DIRECTION_OPTIONS)
                .id("new_bridge_direction")
                .label(Component.literal("Direction"))
                .command(SET_UI_NEW_DIRECTION)
                .action("add_bridge")
                .label(Component.literal("Add Bridge"))
                .command(ADD_BRIDGE)
                .group("remove", Component.literal("Remove Bridge"))
                .text(UI_REMOVE_NAME)
                .id("remove_bridge_name")
                .label(Component.literal("Name"))
                .command(SET_UI_REMOVE_NAME)
                .action("remove_bridge")
                .label(Component.literal("Remove Bridge"))
                .command(REMOVE_BRIDGE)
                .group("current", Component.literal("Current Bridges"))
                .readonly(UI_CHANNELS)
                .id("bridge_channels")
                .label(Component.literal("Bridges"))
                .readonly(UI_STATUS)
                .id("bridge_status")
                .label(Component.literal("Status"));
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
    }

    public CCSynaxisPlantPort createPlantPort(EndpointId endpointId, String requestedName) {
        String name = requestedName == null || requestedName.isBlank() ? "CC Synaxis Bridge" : requestedName;
        return new CCSynaxisPlantPort(this, endpointId, name);
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
        return "CC Synaxis Bridge";
    }

    @Override
    public EndpointDefinition definition() {
        ComponentSchema schema = schema();
        return new EndpointDefinition(
                CCSynaxisBridgeComponent.ID,
                new CCSynaxisBridgeComponent.Config(this, schema),
                schema,
                new EndpointUiHints(portLabels()));
    }

    @Override
    public EndpointRuntimeBinding binding() {
        return BINDING;
    }

    public synchronized Collection<BridgeChannel> channels() {
        return List.copyOf(channels.values());
    }

    public synchronized Optional<BridgeChannel> channel(String name) {
        return Optional.ofNullable(channels.get(name));
    }

    public synchronized BridgeChannel addChannel(String requestedName, BridgeSignalType type, BridgeDirection direction) throws LuaException {
        String name = normalizeOrGenerateName(requestedName);
        if (channels.containsKey(name)) {
            throw new LuaException("Bridge '" + name + "' already exists");
        }
        BridgeChannel channel = new BridgeChannel(name, type, direction);
        channels.put(name, channel);
        values.putIfAbsent(name, channel.defaultValue());
        markBridgeConfigurationChanged();
        return channel;
    }

    public synchronized boolean removeChannel(String name) throws LuaException {
        String normalized = requireChannelName(name);
        BridgeChannel removed = channels.remove(normalized);
        if (removed == null) {
            return false;
        }
        values.remove(normalized);
        lastWriteTicks.remove(normalized);
        markBridgeConfigurationChanged();
        return true;
    }

    public SignalValue readForCc(String name) throws LuaException {
        BridgeChannel channel = requireChannel(name);
        if (!channel.direction().canCcRead()) {
            throw new LuaException("Bridge '" + channel.name() + "' is not readable from CC");
        }
        return valueOrDefault(channel);
    }

    public void writeFromCc(String name, SignalValue value) throws LuaException {
        BridgeChannel channel = requireChannel(name);
        if (!channel.direction().canCcWrite()) {
            throw new LuaException("Bridge '" + channel.name() + "' is not writable from CC");
        }
        writeValue(channel, value, "cc");
    }

    public SignalValue readForSynaxis(String name) {
        BridgeChannel channel = channels.get(name);
        if (channel == null || channel.direction() != BridgeDirection.CC_TO_SYNAXIS) {
            return channel == null ? null : channel.defaultValue();
        }
        return valueOrDefault(channel);
    }

    public void writeFromSynaxis(String name, SignalValue value) {
        BridgeChannel channel = channels.get(name);
        if (channel == null || channel.direction() != BridgeDirection.SYNAXIS_TO_CC) {
            return;
        }
        if (!channel.type().accepts(value)) {
            return;
        }
        writeValue(channel, value, "synaxis");
        peripheral.queueInputEvent(channel.name(), value);
    }

    public void clearMissingSynaxisInput(String name) {
        BridgeChannel channel = channels.get(name);
        if (channel == null || channel.direction() != BridgeDirection.SYNAXIS_TO_CC || channel.saveLastValue()) {
            return;
        }
        resetValueToDefault(channel, true);
    }

    public long lastWriteTick(String name) throws LuaException {
        BridgeChannel channel = requireChannel(name);
        return lastWriteTicks.getOrDefault(channel.name(), -1L);
    }

    public synchronized ComponentSchema schema() {
        List<PortDef> inputs = new ArrayList<>();
        List<PortDef> outputs = new ArrayList<>();
        for (BridgeChannel channel : channels.values()) {
            if (channel.direction() == BridgeDirection.SYNAXIS_TO_CC) {
                inputs.add(channel.portDef());
            } else {
                outputs.add(channel.portDef());
            }
        }
        return ComponentSchema.of(inputs, outputs);
    }

    public synchronized Map<String, Object> channelInfo(BridgeChannel channel) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", channel.name());
        info.put("type", channel.type().serializedName());
        info.put("direction", channel.direction().serializedName());
        info.put("saveLastValue", channel.saveLastValue());
        info.put("ccReadable", channel.direction().canCcRead());
        info.put("ccWritable", channel.direction().canCcWrite());
        info.put("value", SynaxisSignalValues.toLua(valueOrDefault(channel)));
        info.put("lastWriteTick", lastWriteTicks.getOrDefault(channel.name(), -1L));
        return info;
    }

    public synchronized List<Map<String, Object>> channelInfoList() {
        List<Map<String, Object>> result = new ArrayList<>();
        channels.values().forEach(channel -> result.add(channelInfo(channel)));
        return result;
    }

    public String uiStatus() {
        return uiStatus;
    }

    public synchronized String bridgeListSignature() {
        StringBuilder builder = new StringBuilder();
        channels.values().forEach(channel -> builder.append(channel.name())
                .append('|')
                .append(channel.type().serializedName())
                .append('|')
                .append(channel.direction().serializedName())
                .append('|')
                .append(channel.saveLastValue())
                .append(';'));
        builder.append("status=").append(uiStatus);
        return builder.toString();
    }

    public synchronized String nextGeneratedNamePreview() {
        return generateName();
    }

    public String addChannelFromUi(String requestedName, String typeName, String directionName) {
        uiNewName = requestedName == null ? "" : requestedName;
        uiNewType = isValidTypeName(typeName) ? typeName : DEFAULT_DRAFT_TYPE;
        uiNewDirection = isValidDirectionName(directionName) ? directionName : DEFAULT_DRAFT_DIRECTION;
        try {
            BridgeChannel channel = addChannel(
                    uiNewName,
                    BridgeSignalType.parse(uiNewType),
                    BridgeDirection.parse(uiNewDirection));
            uiNewName = "";
            uiStatus = "Added " + channel.name() + " (" + channel.type().serializedName()
                    + ", " + channel.direction().serializedName() + ")";
        } catch (LuaException exception) {
            uiStatus = exception.getMessage();
        }
        markUiStateChanged();
        syncBridgeDataToClient();
        return uiStatus;
    }

    public String removeChannelFromUi(String requestedName) {
        uiRemoveName = requestedName == null ? "" : requestedName;
        try {
            String name = requireChannelName(uiRemoveName);
            boolean removed = removeChannel(name);
            uiStatus = removed ? "Removed " + name : "Bridge '" + name + "' does not exist";
            if (removed) {
                uiRemoveName = "";
            }
        } catch (LuaException exception) {
            uiStatus = exception.getMessage();
        }
        markUiStateChanged();
        syncBridgeDataToClient();
        return uiStatus;
    }

    public synchronized boolean saveLastValue(String name) throws LuaException {
        return requireChannel(name).saveLastValue();
    }

    public synchronized boolean setSaveLastValue(String name, boolean saveLastValue) throws LuaException {
        BridgeChannel channel = requireChannel(name);
        channels.put(channel.name(), channel.withSaveLastValue(saveLastValue));
        uiStatus = channel.name() + " save last value: " + (saveLastValue ? "on" : "off");
        markUiStateChanged();
        setChanged();
        syncBridgeDataToClient();
        return saveLastValue;
    }

    public synchronized boolean toggleSaveLastValue(String name) throws LuaException {
        BridgeChannel channel = requireChannel(name);
        return setSaveLastValue(channel.name(), !channel.saveLastValue());
    }

    public String toggleSaveLastValueFromUi(String requestedName) {
        try {
            boolean saveLastValue = toggleSaveLastValue(requestedName);
            uiStatus = requestedName + " save last value: " + (saveLastValue ? "on" : "off");
        } catch (LuaException exception) {
            uiStatus = exception.getMessage();
        }
        markUiStateChanged();
        syncBridgeDataToClient();
        return uiStatus;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putUUID(ENDPOINT_ID_TAG, endpointId.value());

        ListTag channelTags = new ListTag();
        synchronized (this) {
            channels.values().forEach(channel -> channelTags.add(channel.toTag()));
        }
        tag.put(CHANNELS_TAG, channelTags);

        CompoundTag valueTags = new CompoundTag();
        values.forEach((name, value) -> valueTags.put(name, SynaxisSignalValues.toTag(value)));
        tag.put(VALUES_TAG, valueTags);

        CompoundTag tickTags = new CompoundTag();
        lastWriteTicks.forEach(tickTags::putLong);
        tag.put(LAST_WRITE_TICKS_TAG, tickTags);
        tag.putString(UI_STATUS_TAG, uiStatus);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.hasUUID(ENDPOINT_ID_TAG)) {
            endpointId = EndpointId.of(tag.getUUID(ENDPOINT_ID_TAG));
        }

        synchronized (this) {
            channels.clear();
            ListTag channelTags = tag.getList(CHANNELS_TAG, Tag.TAG_COMPOUND);
            for (Tag entry : channelTags) {
                if (entry instanceof CompoundTag channelTag) {
                    BridgeChannel channel = BridgeChannel.fromTag(channelTag);
                    if (channel != null && !channels.containsKey(channel.name())) {
                        channels.put(channel.name(), channel);
                    }
                }
            }
        }

        values.clear();
        CompoundTag valueTags = tag.getCompound(VALUES_TAG);
        for (String name : valueTags.getAllKeys()) {
            BridgeChannel channel = channels.get(name);
            if (channel == null) {
                continue;
            }
            SynaxisSignalValues.fromTag(valueTags.getCompound(name))
                    .filter(channel.type()::accepts)
                    .ifPresent(value -> values.put(name, value));
        }
        channels.values().forEach(channel -> values.putIfAbsent(channel.name(), channel.defaultValue()));

        lastWriteTicks.clear();
        CompoundTag tickTags = tag.getCompound(LAST_WRITE_TICKS_TAG);
        for (String name : tickTags.getAllKeys()) {
            lastWriteTicks.put(name, tickTags.getLong(name));
        }
        uiStatus = tag.contains(UI_STATUS_TAG, Tag.TAG_STRING) ? tag.getString(UI_STATUS_TAG) : "Ready";
        uiChannelsSnapshot = uiChannelsText();
        endpointDirty = true;
    }

    private void addChannelFromUi(ServerPlayer player) {
        addChannelFromUi(uiNewName, uiNewType, uiNewDirection);
    }

    private void removeChannelFromUi(ServerPlayer player) {
        removeChannelFromUi(uiRemoveName);
    }

    private synchronized Map<String, String> portLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        channels.values().forEach(channel -> {
            String side = channel.direction() == BridgeDirection.SYNAXIS_TO_CC ? "in" : "out";
            labels.put(channel.name(), channel.name() + " [" + side + "] " + channel.type().serializedName());
        });
        return labels;
    }

    private synchronized String uiChannelsText() {
        if (channels.isEmpty()) {
            return "No bridges";
        }
        StringBuilder builder = new StringBuilder();
        channels.values().forEach(channel -> {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(channel.name())
                    .append(" | ")
                    .append(channel.type().serializedName())
                    .append(" | ")
                    .append(channel.direction().serializedName())
                    .append(" | saveLastValue=")
                    .append(channel.saveLastValue());
        });
        return builder.toString();
    }

    private void markUiStateChanged() {
        if (isRemoved()) {
            return;
        }
        uiChannelsSnapshot = uiChannelsText();
        try {
            markStateDirty(UI_CHANNELS);
            markStateDirty(UI_STATUS);
            markStateDirty(UI_NEW_NAME);
            markStateDirty(UI_REMOVE_NAME);
        } catch (RuntimeException ignored) {
            setChanged();
        }
    }

    private void writeValue(BridgeChannel channel, SignalValue value, String writer) {
        if (value == null || !channel.type().accepts(value)) {
            return;
        }
        values.put(channel.name(), value);
        Level level = getLevel();
        lastWriteTicks.put(channel.name(), level == null ? -1L : level.getGameTime());
        uiStatus = writer + " write: " + channel.name();
        markUiStateChanged();
        setChanged();
        syncBridgeValueToClient(level);
    }

    private SignalValue valueOrDefault(BridgeChannel channel) {
        SignalValue value = values.get(channel.name());
        return value == null ? channel.defaultValue() : value;
    }

    private void resetValueToDefault(BridgeChannel channel, boolean queueCcEvent) {
        SignalValue defaultValue = channel.defaultValue();
        SignalValue current = values.get(channel.name());
        if (Objects.equals(current, defaultValue)) {
            return;
        }
        values.put(channel.name(), defaultValue);
        Level level = getLevel();
        lastWriteTicks.put(channel.name(), level == null ? -1L : level.getGameTime());
        uiStatus = "Reset " + channel.name() + " to default";
        markUiStateChanged();
        setChanged();
        syncBridgeValueToClient(level);
        if (queueCcEvent) {
            peripheral.queueInputEvent(channel.name(), defaultValue);
        }
    }

    private BridgeChannel requireChannel(String name) throws LuaException {
        String normalized = requireChannelName(name);
        BridgeChannel channel = channels.get(normalized);
        if (channel == null) {
            throw new LuaException("Unknown bridge '" + normalized + "'");
        }
        return channel;
    }

    private String normalizeOrGenerateName(String requestedName) throws LuaException {
        if (requestedName == null || requestedName.isBlank()) {
            return generateName();
        }
        return requireChannelName(requestedName);
    }

    private synchronized String generateName() {
        int index = 1;
        while (channels.containsKey("bridge_" + index)) {
            index++;
        }
        return "bridge_" + index;
    }

    private static String requireChannelName(String name) throws LuaException {
        if (name == null || name.isBlank()) {
            throw new LuaException("Bridge name is required");
        }
        String normalized = name.trim();
        if (!normalized.matches("[A-Za-z_@$][A-Za-z0-9_@$.:-]*")) {
            throw new LuaException("Invalid bridge name '" + name + "'");
        }
        return normalized;
    }

    private static boolean isValidTypeName(String value) {
        if (value == null) {
            return false;
        }
        for (String option : BRIDGE_TYPE_OPTIONS.values()) {
            if (option.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidDirectionName(String value) {
        if (value == null) {
            return false;
        }
        for (String option : BRIDGE_DIRECTION_OPTIONS.values()) {
            if (option.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private void markBridgeConfigurationChanged() {
        endpointDirty = true;
        markUiStateChanged();
        setChanged();
        if (level != null && !level.isClientSide()) {
            syncBridgeDataToClient();
        }
    }

    private void syncBridgeDataToClient() {
        if (level != null && !level.isClientSide()) {
            sendData();
        }
    }

    private void syncBridgeValueToClient(Level level) {
        if (level == null || level.isClientSide()) {
            return;
        }
        long tick = level.getGameTime();
        if (lastClientValueSyncTick == Long.MIN_VALUE || tick - lastClientValueSyncTick >= 10L) {
            lastClientValueSyncTick = tick;
            sendData();
        }
    }

    private void unregisterEndpoint() {
        if (!endpointRegistered || !(level instanceof ServerLevel serverLevel)) {
            endpointRegistered = false;
            return;
        }
        CimulinkWorldRuntimes.forLevel(serverLevel).unregisterEndpoint(endpointId);
        endpointRegistered = false;
    }
}
