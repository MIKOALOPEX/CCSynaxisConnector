package com.mikoalopex.ccsconnector.content;

import com.verr1.synaxis.foundation.cimulink.core.signal.PortDef;
import com.verr1.synaxis.foundation.cimulink.core.signal.SignalValue;
import net.minecraft.nbt.CompoundTag;

public record BridgeChannel(String name, BridgeSignalType type, BridgeDirection direction, boolean saveLastValue) {
    private static final String NAME_TAG = "Name";
    private static final String TYPE_TAG = "Type";
    private static final String DIRECTION_TAG = "Direction";
    private static final String SAVE_LAST_VALUE_TAG = "SaveLastValue";

    public BridgeChannel(String name, BridgeSignalType type, BridgeDirection direction) {
        this(name, type, direction, false);
    }

    public PortDef portDef() {
        return direction == BridgeDirection.SYNAXIS_TO_CC
                ? PortDef.input(name, type.signalType())
                : PortDef.output(name, type.signalType());
    }

    public SignalValue defaultValue() {
        return type.defaultValue();
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(NAME_TAG, name);
        tag.putString(TYPE_TAG, type.serializedName());
        tag.putString(DIRECTION_TAG, direction.serializedName());
        tag.putBoolean(SAVE_LAST_VALUE_TAG, saveLastValue);
        return tag;
    }

    public BridgeChannel withSaveLastValue(boolean saveLastValue) {
        return new BridgeChannel(name, type, direction, saveLastValue);
    }

    public static BridgeChannel fromTag(CompoundTag tag) {
        try {
            boolean saveLastValue = tag.contains(SAVE_LAST_VALUE_TAG)
                    ? tag.getBoolean(SAVE_LAST_VALUE_TAG)
                    : true;
            return new BridgeChannel(
                    tag.getString(NAME_TAG),
                    BridgeSignalType.parse(tag.getString(TYPE_TAG)),
                    BridgeDirection.parse(tag.getString(DIRECTION_TAG)),
                    saveLastValue);
        } catch (Exception ignored) {
            return null;
        }
    }
}
