package com.mikoalopex.ccsconnector.content;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CCSynaxisBridgeListUi extends UIElement {
    private static final float ROOT_WIDTH = 370.0F;
    private static final float ROOT_HEIGHT = 236.0F;
    private static final float ROW_HEIGHT = 16.0F;
    private static final String ADD_BRIDGE_MESSAGE = "add_bridge";
    private static final String REMOVE_BRIDGE_MESSAGE = "remove_bridge";
    private static final String TOGGLE_SAVE_LAST_VALUE_MESSAGE = "toggle_save_last_value";

    private static final int ROOT_BACKGROUND = 0xE0182024;
    private static final int PANEL_BACKGROUND = 0xD0263036;
    private static final int ROW_BACKGROUND = 0xB01A2228;
    private static final int FIELD_BACKGROUND = 0xE00D1418;
    private static final int BUTTON_BACKGROUND = 0xFF2F4650;
    private static final int BUTTON_HOVER = 0xFF3A5A66;
    private static final int BUTTON_PRESSED = 0xFF243740;
    private static final int DANGER_BACKGROUND = 0xFF623336;
    private static final int DANGER_HOVER = 0xFF7A4146;
    private static final int TEXT_PRIMARY = 0xFFEAF6F3;
    private static final int TEXT_MUTED = 0xFF9FB6B7;
    private static final int TEXT_ACCENT = 0xFF7FE0C5;
    private static final int TEXT_WARNING = 0xFFFFC170;

    private final CCSynaxisBridgeBlockEntity blockEntity;
    private final TextField nameField;
    private final Selector<String> typeSelector;
    private final Selector<String> directionSelector;
    private final ScrollerView bridgeList;
    private final Label statusLabel;

    private String lastSignature = "";
    private String lastStatus = "";
    private String lastPlaceholder = "";

    private CCSynaxisBridgeListUi(CCSynaxisBridgeBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
        this.nameField = textField("bridge_1", 106.0F);
        this.typeSelector = selector(List.of("real", "boolean"), "real", 66.0F);
        this.directionSelector = selector(List.of("cc_to_synaxis", "synaxis_to_cc"), "cc_to_synaxis", 124.0F);
        this.bridgeList = new ScrollerView();
        this.statusLabel = label("", TEXT_MUTED, Horizontal.LEFT, 8.0F);

        setId("ccs_bridge_root");
        layout(style -> style.width(ROOT_WIDTH)
                .height(ROOT_HEIGHT)
                .paddingAll(8.0F)
                .gapRow(4.0F)
                .flexDirection(FlexDirection.COLUMN)
                .alignItems(AlignItems.STRETCH));
        style(style -> style.backgroundTexture(new ColorRectTexture(ROOT_BACKGROUND)));
        onMessage(ADD_BRIDGE_MESSAGE, this::handleAddBridgeMessage);
        onMessage(REMOVE_BRIDGE_MESSAGE, this::handleRemoveBridgeMessage);
        onMessage(TOGGLE_SAVE_LAST_VALUE_MESSAGE, this::handleToggleSaveLastValueMessage);

        addChildren(title(), createPanel(), listHeader(), configureList(), statusLabel);
        refresh();
    }

    public static ModularUI create(CCSynaxisBridgeBlockEntity blockEntity, Player player) {
        CCSynaxisBridgeListUi root = new CCSynaxisBridgeListUi(blockEntity);
        return ModularUI.of(
                UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC))),
                player);
    }

    @Override
    public void screenTick() {
        super.screenTick();
        refresh();
    }

    @Override
    public void serverTick() {
        super.serverTick();
        refresh();
    }

    private UIElement title() {
        UIElement row = row("ccs_bridge_title", 14.0F, 0);
        row.addChildren(
                label(tr("gui.ccsconnector.bridge.title"), TEXT_PRIMARY, Horizontal.LEFT, 9.0F, 210.0F),
                label(tr("gui.ccsconnector.bridge.subtitle"), TEXT_MUTED, Horizontal.RIGHT, 7.0F, 134.0F));
        return row;
    }

    private UIElement createPanel() {
        UIElement panel = new UIElement();
        panel.setId("ccs_bridge_create_panel");
        panel.layout(style -> style.widthStretch()
                .height(44.0F)
                .paddingAll(5.0F)
                .gapRow(4.0F)
                .flexDirection(FlexDirection.COLUMN)
                .alignItems(AlignItems.STRETCH));
        panel.style(style -> style.backgroundTexture(new ColorRectTexture(PANEL_BACKGROUND)));

        UIElement labels = row("ccs_bridge_create_labels", 10.0F, 0);
        labels.addChildren(
                label(tr("gui.ccsconnector.common.name"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 106.0F),
                label(tr("gui.ccsconnector.common.type"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 66.0F),
                label(tr("gui.ccsconnector.common.flow"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 124.0F),
                label("", TEXT_MUTED, Horizontal.LEFT, 7.0F, 44.0F));

        UIElement controls = row("ccs_bridge_create_controls", 18.0F, 4.0F);
        Button addButton = button(trText("gui.ccsconnector.common.add"), 44.0F, false, () -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", nameField.getText());
            tag.putString("type", typeSelector.getValue());
            tag.putString("direction", directionSelector.getValue());
            sendMessage(ADD_BRIDGE_MESSAGE, tag);
        });
        controls.addChildren(nameField, typeSelector, directionSelector, addButton);

        panel.addChildren(labels, controls);
        return panel;
    }

    private UIElement listHeader() {
        UIElement header = row("ccs_bridge_list_header", 12.0F, 4.0F);
        header.addChildren(
                label(tr("gui.ccsconnector.common.bridge"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 76.0F),
                label(tr("gui.ccsconnector.common.type"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 42.0F),
                label(tr("gui.ccsconnector.common.port"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 28.0F),
                label(tr("gui.ccsconnector.common.flow"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 74.0F),
                label(tr("gui.ccsconnector.bridge.save_last"), TEXT_MUTED, Horizontal.CENTER, 6.5F, 86.0F),
                label("", TEXT_MUTED, Horizontal.LEFT, 7.0F, 18.0F));
        return header;
    }

    private ScrollerView configureList() {
        bridgeList.setId("ccs_bridge_list");
        bridgeList.layout(style -> style.widthStretch()
                .height(132.0F)
                .flexGrow(1.0F));
        bridgeList.style(style -> style.backgroundTexture(new ColorRectTexture(PANEL_BACKGROUND)));
        bridgeList.viewContainer(container -> container.layout(style -> style.paddingAll(4.0F)
                .gapRow(3.0F)
                .flexDirection(FlexDirection.COLUMN)
                .alignItems(AlignItems.STRETCH)));
        return bridgeList;
    }

    private void refresh() {
        String signature = blockEntity.bridgeListSignature();
        if (!Objects.equals(signature, lastSignature)) {
            rebuildRows(blockEntity.channels());
            lastSignature = signature;
        }

        String status = blockEntity.uiStatus();
        if (!Objects.equals(status, lastStatus)) {
            statusLabel.setText(Component.literal(status));
            lastStatus = status;
        }

        String placeholder = blockEntity.nextGeneratedNamePreview();
        if (!Objects.equals(placeholder, lastPlaceholder)) {
            nameField.textFieldStyle(style -> style.placeholder(Component.literal(placeholder)));
            lastPlaceholder = placeholder;
        }
    }

    private void rebuildRows(Collection<BridgeChannel> channels) {
        bridgeList.clearAllScrollViewChildren();
        if (channels.isEmpty()) {
            UIElement empty = row("ccs_bridge_empty", ROW_HEIGHT, 0);
            empty.addChild(label(tr("gui.ccsconnector.bridge.empty"), TEXT_MUTED, Horizontal.CENTER, 8.0F, 344.0F));
            bridgeList.addScrollViewChild(empty);
            return;
        }

        for (BridgeChannel channel : channels) {
            bridgeList.addScrollViewChild(bridgeRow(channel));
        }
    }

    private UIElement bridgeRow(BridgeChannel channel) {
        UIElement row = row("ccs_bridge_row_" + channel.name(), ROW_HEIGHT, 4.0F);
        row.style(style -> style.backgroundTexture(new ColorRectTexture(ROW_BACKGROUND)));
        row.addChildren(
                label(channel.name(), TEXT_PRIMARY, Horizontal.LEFT, 7.5F, 76.0F),
                label(channel.type().serializedName(), TEXT_ACCENT, Horizontal.LEFT, 7.5F, 42.0F),
                label(portSide(channel.direction()), TEXT_WARNING, Horizontal.LEFT, 7.5F, 28.0F),
                label(flowText(channel.direction()), TEXT_MUTED, Horizontal.LEFT, 7.5F, 74.0F),
                saveLastButton(channel),
                button("X", 18.0F, true, () -> {
                    CompoundTag tag = new CompoundTag();
                    tag.putString("name", channel.name());
                    sendMessage(REMOVE_BRIDGE_MESSAGE, tag);
                }));
        row.style(style -> style.tooltips(
                tr("gui.ccsconnector.tooltip.port", channel.name()),
                tr("gui.ccsconnector.tooltip.synaxis", portSide(channel.direction())),
                tr("gui.ccsconnector.tooltip.value", valueText(channel)),
                tr(channel.saveLastValue()
                        ? "gui.ccsconnector.bridge.tooltip.save_last_true"
                        : "gui.ccsconnector.bridge.tooltip.save_last_false")));
        return row;
    }

    private void handleAddBridgeMessage(CompoundTag tag) {
        blockEntity.addChannelFromUi(
                tag.getString("name"),
                tag.getString("type"),
                tag.getString("direction"));
    }

    private void handleRemoveBridgeMessage(CompoundTag tag) {
        blockEntity.removeChannelFromUi(tag.getString("name"));
    }

    private void handleToggleSaveLastValueMessage(CompoundTag tag) {
        blockEntity.toggleSaveLastValueFromUi(tag.getString("name"));
    }

    private String valueText(BridgeChannel channel) {
        Map<String, Object> info = blockEntity.channelInfo(channel);
        return String.valueOf(info.getOrDefault("value", ""));
    }

    private static String portSide(BridgeDirection direction) {
        return direction == BridgeDirection.SYNAXIS_TO_CC ? "in" : "out";
    }

    private static String flowText(BridgeDirection direction) {
        return trText(direction == BridgeDirection.SYNAXIS_TO_CC
                ? "gui.ccsconnector.bridge.flow.synaxis_to_cc"
                : "gui.ccsconnector.bridge.flow.cc_to_synaxis");
    }

    private Button saveLastButton(BridgeChannel channel) {
        Button button = button(trText("gui.ccsconnector.bridge.save_last"), 86.0F, false, () -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", channel.name());
            sendMessage(TOGGLE_SAVE_LAST_VALUE_MESSAGE, tag);
        });
        int base = channel.saveLastValue() ? 0xFF2F5B4D : FIELD_BACKGROUND;
        int hover = channel.saveLastValue() ? 0xFF3C7563 : BUTTON_HOVER;
        button.buttonStyle(style -> style.baseTexture(new ColorRectTexture(base))
                .hoverTexture(new ColorRectTexture(hover))
                .pressedTexture(new ColorRectTexture(BUTTON_PRESSED)));
        button.textStyle(style -> style.textColor(channel.saveLastValue() ? TEXT_ACCENT : TEXT_MUTED)
                .fontSize(6.5F)
                .textWrap(TextWrap.HIDE)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER)
                .textShadow(false));
        return button;
    }

    private static UIElement row(String id, float height, float gap) {
        UIElement row = new UIElement();
        row.setId(id);
        row.layout(style -> style.widthStretch()
                .height(height)
                .gapColumn(gap)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER)
                .justifyContent(AlignContent.START));
        return row;
    }

    private static TextField textField(String placeholder, float width) {
        TextField field = new TextField();
        field.setAnyString();
        field.layout(style -> style.width(width).height(16.0F));
        field.style(style -> style.backgroundTexture(new ColorRectTexture(FIELD_BACKGROUND)));
        field.textFieldStyle(style -> style.placeholder(Component.literal(placeholder))
                .textColor(TEXT_PRIMARY)
                .cursorColor(TEXT_ACCENT)
                .fontSize(7.5F)
                .textShadow(false));
        return field;
    }

    private static Selector<String> selector(List<String> candidates, String selected, float width) {
        Selector<String> selector = new Selector<>();
        selector.setCandidates(candidates);
        selector.setSelected(selected);
        selector.layout(style -> style.width(width).height(16.0F));
        selector.style(style -> style.backgroundTexture(new ColorRectTexture(FIELD_BACKGROUND)));
        selector.selectorStyle(style -> style.maxItemCount(4)
                .scrollerViewHeight(60.0F)
                .closeAfterSelect(true));
        return selector;
    }

    private static Button button(String text, float width, boolean danger, Runnable action) {
        Button button = new Button();
        button.setText(text);
        button.layout(style -> style.width(width)
                .height(16.0F)
                .alignItems(AlignItems.CENTER)
                .justifyContent(AlignContent.CENTER));
        button.textStyle(style -> style.textColor(TEXT_PRIMARY)
                .fontSize(7.5F)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER)
                .textShadow(false));
        int base = danger ? DANGER_BACKGROUND : BUTTON_BACKGROUND;
        int hover = danger ? DANGER_HOVER : BUTTON_HOVER;
        button.buttonStyle(style -> style.baseTexture(new ColorRectTexture(base))
                .hoverTexture(new ColorRectTexture(hover))
                .pressedTexture(new ColorRectTexture(BUTTON_PRESSED)));
        button.setOnClick(event -> action.run());
        return button;
    }

    private static Label label(String text, int color, Horizontal alignment, float fontSize) {
        return label(Component.literal(text), color, alignment, fontSize);
    }

    private static Label label(Component text, int color, Horizontal alignment, float fontSize) {
        Label label = new Label();
        label.setText(text);
        label.layout(style -> style.widthStretch().height(10.0F));
        label.textStyle(style -> style.textColor(color)
                .fontSize(fontSize)
                .textWrap(TextWrap.HIDE)
                .textAlignHorizontal(alignment)
                .textAlignVertical(Vertical.CENTER)
                .textShadow(false));
        return label;
    }

    private static Label label(String text, int color, Horizontal alignment, float fontSize, float width) {
        Label label = label(text, color, alignment, fontSize);
        label.layout(style -> style.width(width).height(ROW_HEIGHT));
        return label;
    }

    private static Label label(Component text, int color, Horizontal alignment, float fontSize, float width) {
        Label label = label(text, color, alignment, fontSize);
        label.layout(style -> style.width(width).height(ROW_HEIGHT));
        return label;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static String trText(String key, Object... args) {
        return tr(key, args).getString();
    }
}
