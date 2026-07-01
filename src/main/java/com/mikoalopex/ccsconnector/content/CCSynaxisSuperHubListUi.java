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
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Objects;

public final class CCSynaxisSuperHubListUi extends UIElement {
    private static final float ROOT_WIDTH = 370.0F;
    private static final float ROOT_HEIGHT = 236.0F;
    private static final float ROW_HEIGHT = 16.0F;
    private static final float RESIZE_HANDLE_WIDTH = 4.0F;
    private static final int COL_SOURCE = 0;
    private static final int COL_MEMBER = 1;
    private static final int COL_TYPE = 2;
    private static final int COL_ACCESS = 3;
    private static final int COL_AS = 4;
    private static final int COL_VALUE = 5;
    private static final int COL_ACTION = 6;
    private static final String TOGGLE_TARGET_MESSAGE = "toggle_target";
    private static final String ADD_EXPOSURE_MESSAGE = "add_exposure";
    private static final String REMOVE_EXPOSURE_MESSAGE = "remove_exposure";

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

    private final CCSynaxisSuperHubBlockEntity blockEntity;
    private final Selector<String> deviceSelector;
    private final ScrollerView capabilityList;
    private final UIElement listHeader;
    private final Label titleTargetLabel;
    private final Button switchTargetButton;
    private final Label statusLabel;
    private final float[] columnWidths = {28.0F, 104.0F, 32.0F, 36.0F, 28.0F, 48.0F, 22.0F};
    private final float[] minColumnWidths = {20.0F, 56.0F, 28.0F, 32.0F, 24.0F, 32.0F, 22.0F};

    private String selectedDevice = "";
    private List<String> lastDeviceNames = List.of();
    private String lastSignature = "";
    private String lastStatus = "";
    private SuperHubTarget target = SuperHubTarget.SYNAXIS;
    private boolean suppressDeviceSelection;
    private int resizingBoundary = -1;
    private float resizeLastX;

    private CCSynaxisSuperHubListUi(CCSynaxisSuperHubBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
        this.deviceSelector = selector(List.of(), "", 190.0F);
        this.capabilityList = new ScrollerView();
        this.listHeader = createListHeader();
        this.titleTargetLabel = label("", TEXT_MUTED, Horizontal.RIGHT, 7.0F, 124.0F);
        this.switchTargetButton = button(trText("gui.ccsconnector.super_hub.to", "CC"), 64.0F, false,
                () -> handleToggleTargetMessage(new CompoundTag()));
        this.statusLabel = label("", TEXT_MUTED, Horizontal.LEFT, 8.0F);

        setId("ccs_super_hub_root");
        layout(style -> style.width(ROOT_WIDTH)
                .height(ROOT_HEIGHT)
                .paddingAll(8.0F)
                .gapRow(4.0F)
                .flexDirection(FlexDirection.COLUMN)
                .alignItems(AlignItems.STRETCH));
        style(style -> style.backgroundTexture(new ColorRectTexture(ROOT_BACKGROUND)));
        onMessage(TOGGLE_TARGET_MESSAGE, this::handleToggleTargetMessage);
        onMessage(ADD_EXPOSURE_MESSAGE, this::handleAddExposureMessage);
        onMessage(REMOVE_EXPOSURE_MESSAGE, this::handleRemoveExposureMessage);
        addEventListener(UIEvents.MOUSE_MOVE, this::handleResizeMove, true);
        addEventListener(UIEvents.MOUSE_UP, this::handleResizeEnd, true);

        deviceSelector.setOnValueChanged(value -> {
            if (suppressDeviceSelection) {
                return;
            }
            selectedDevice = value == null ? "" : value;
            lastSignature = "";
            refresh();
        });

        addChildren(title(), createPanel(), listHeader, configureList(), statusLabel);
        refresh();
    }

    public static ModularUI create(CCSynaxisSuperHubBlockEntity blockEntity, Player player) {
        CCSynaxisSuperHubListUi root = new CCSynaxisSuperHubListUi(blockEntity);
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
        UIElement row = row("ccs_super_hub_title", 14.0F, 0);
        row.addChildren(
                label(tr("gui.ccsconnector.super_hub.title"), TEXT_PRIMARY, Horizontal.LEFT, 9.0F, 220.0F),
                titleTargetLabel);
        return row;
    }

    private UIElement createPanel() {
        UIElement panel = new UIElement();
        panel.setId("ccs_super_hub_create_panel");
        panel.layout(style -> style.widthStretch()
                .height(42.0F)
                .paddingAll(5.0F)
                .gapRow(4.0F)
                .flexDirection(FlexDirection.COLUMN)
                .alignItems(AlignItems.STRETCH));
        panel.style(style -> style.backgroundTexture(new ColorRectTexture(PANEL_BACKGROUND)));

        UIElement labels = row("ccs_super_hub_create_labels", 10.0F, 0);
        labels.addChildren(
                label(tr("gui.ccsconnector.super_hub.select_device"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 190.0F),
                label(tr("gui.ccsconnector.super_hub.target_side"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 74.0F),
                label("", TEXT_MUTED, Horizontal.LEFT, 7.0F, 64.0F));

        UIElement controls = row("ccs_super_hub_create_controls", 18.0F, 4.0F);
        switchTargetButton.setId("ccs_super_hub_switch_target");
        controls.addChildren(
                deviceSelector,
                label("", TEXT_MUTED, Horizontal.LEFT, 7.0F, 74.0F),
                switchTargetButton);

        panel.addChildren(labels, controls);
        return panel;
    }

    private UIElement createListHeader() {
        UIElement header = row("ccs_super_hub_list_header", 12.0F, 0);
        rebuildListHeader(header);
        return header;
    }

    private void rebuildListHeader(UIElement header) {
        header.clearAllChildren();
        header.addChildren(
                label(tr("gui.ccsconnector.super_hub.src"), TEXT_MUTED, Horizontal.LEFT, 7.0F, columnWidths[COL_SOURCE]),
                resizeHandle(COL_SOURCE),
                label(tr("gui.ccsconnector.super_hub.method_port"), TEXT_MUTED, Horizontal.LEFT, 7.0F, columnWidths[COL_MEMBER]),
                resizeHandle(COL_MEMBER),
                label(tr("gui.ccsconnector.common.type"), TEXT_MUTED, Horizontal.LEFT, 7.0F, columnWidths[COL_TYPE]),
                resizeHandle(COL_TYPE),
                label(tr("gui.ccsconnector.super_hub.access"), TEXT_MUTED, Horizontal.LEFT, 7.0F, columnWidths[COL_ACCESS]),
                resizeHandle(COL_ACCESS),
                label(tr("gui.ccsconnector.super_hub.as"), TEXT_MUTED, Horizontal.CENTER, 7.0F, columnWidths[COL_AS]),
                resizeHandle(COL_AS),
                label(tr("gui.ccsconnector.common.value"), TEXT_MUTED, Horizontal.LEFT, 7.0F, columnWidths[COL_VALUE]),
                resizeHandle(COL_VALUE),
                label("", TEXT_MUTED, Horizontal.LEFT, 7.0F, columnWidths[COL_ACTION]));
    }

    private ScrollerView configureList() {
        capabilityList.setId("ccs_super_hub_list");
        capabilityList.layout(style -> style.widthStretch()
                .height(130.0F)
                .flexGrow(1.0F));
        capabilityList.style(style -> style.backgroundTexture(new ColorRectTexture(PANEL_BACKGROUND)));
        capabilityList.viewContainer(container -> container.layout(style -> style.paddingAll(4.0F)
                .gapRow(3.0F)
                .flexDirection(FlexDirection.COLUMN)
                .alignItems(AlignItems.STRETCH)));
        return capabilityList;
    }

    private void refresh() {
        refreshDeviceSelector();
        String signature = blockEntity.signalSignature()
                + "|target=" + target.serializedName()
                + "|device=" + selectedDevice;
        if (!Objects.equals(signature, lastSignature)) {
            titleTargetLabel.setText(tr("gui.ccsconnector.super_hub.providing_to", target.displayName()));
            switchTargetButton.setText(trText("gui.ccsconnector.super_hub.to", target.opposite().displayName()));
            rebuildRows();
            lastSignature = signature;
        }
        String status = blockEntity.status();
        if (!Objects.equals(status, lastStatus)) {
            statusLabel.setText(Component.literal(status));
            lastStatus = status;
        }
    }

    private void refreshDeviceSelector() {
        List<String> names = blockEntity.availableDeviceNames();
        String previousSelected = selectedDevice;
        if ((selectedDevice == null || selectedDevice.isBlank() || !names.contains(selectedDevice)) && !names.isEmpty()) {
            selectedDevice = names.getFirst();
        } else if (names.isEmpty()) {
            selectedDevice = "";
        }
        if (!Objects.equals(previousSelected, selectedDevice)) {
            lastSignature = "";
        }
        if (Objects.equals(names, lastDeviceNames)
                && Objects.equals(deviceSelector.getValue(), selectedDevice)) {
            return;
        }
        suppressDeviceSelection = true;
        deviceSelector.setCandidates(names);
        deviceSelector.setSelected(selectedDevice);
        suppressDeviceSelection = false;
        lastDeviceNames = List.copyOf(names);
    }

    private void rebuildRows() {
        capabilityList.clearAllScrollViewChildren();
        if (selectedDevice == null || selectedDevice.isBlank()) {
            UIElement empty = row("ccs_super_hub_empty", ROW_HEIGHT, 0);
            empty.addChild(label(tr("gui.ccsconnector.super_hub.empty_devices"), TEXT_MUTED, Horizontal.CENTER, 8.0F, 344.0F));
            capabilityList.addScrollViewChild(empty);
            return;
        }
        List<CCSynaxisSuperHubBlockEntity.AvailableCapability> capabilities = blockEntity.capabilitiesForDevice(selectedDevice);
        if (capabilities.isEmpty()) {
            UIElement empty = row("ccs_super_hub_empty_device", ROW_HEIGHT, 0);
            empty.addChild(label(tr("gui.ccsconnector.super_hub.empty_capabilities"), TEXT_MUTED, Horizontal.CENTER, 8.0F, 344.0F));
            capabilityList.addScrollViewChild(empty);
            return;
        }
        for (CCSynaxisSuperHubBlockEntity.AvailableCapability capability : capabilities) {
            capabilityList.addScrollViewChild(capabilityRow(capability));
        }
    }

    private UIElement capabilityRow(CCSynaxisSuperHubBlockEntity.AvailableCapability capability) {
        UIElement row = row("ccs_super_hub_cap_" + capability.id() + "_" + target.serializedName(), ROW_HEIGHT, 4.0F);
        row.style(style -> style.backgroundTexture(new ColorRectTexture(ROW_BACKGROUND)));

        SuperHubEntry exposure = blockEntity.exposureFor(target, capability.id());
        boolean exposed = exposure != null;
        String flow = capability.targetFlowName(target);
        int flowColor = capability.input() ? TEXT_WARNING : TEXT_ACCENT;
        String value = capability.luaValue() == null ? "" : String.valueOf(capability.luaValue());
        Button action = button(exposed ? "X" : "+", columnWidths[COL_ACTION], exposed, () -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("target", target.serializedName());
            tag.putString("capability", capability.id());
            sendMessage(exposed ? REMOVE_EXPOSURE_MESSAGE : ADD_EXPOSURE_MESSAGE, tag);
        });

        row.addChildren(
                label(capability.source().displayName(), TEXT_WARNING, Horizontal.LEFT, 7.0F, columnWidths[COL_SOURCE]),
                label(capability.memberName(), TEXT_PRIMARY, Horizontal.LEFT, 7.0F, columnWidths[COL_MEMBER]),
                label(capability.type().serializedName(), TEXT_MUTED, Horizontal.LEFT, 7.0F, columnWidths[COL_TYPE]),
                label(capability.accessName(), capability.input() ? TEXT_WARNING : TEXT_ACCENT, Horizontal.LEFT, 7.0F, columnWidths[COL_ACCESS]),
                label(flow, flowColor, Horizontal.CENTER, 7.0F, columnWidths[COL_AS]),
                label(value, TEXT_MUTED, Horizontal.LEFT, 7.0F, columnWidths[COL_VALUE]),
                action);
        row.style(style -> style.tooltips(
                tr("gui.ccsconnector.tooltip.device", capability.deviceName()),
                tr("gui.ccsconnector.tooltip.location", capability.location()),
                tr("gui.ccsconnector.tooltip.target", target.displayName()),
                tr("gui.ccsconnector.tooltip.provided", exposed ? exposure.portName() : trText("gui.ccsconnector.common.no"))));
        return row;
    }

    private void handleToggleTargetMessage(CompoundTag tag) {
        target = target.opposite();
        lastSignature = "";
        refresh();
    }

    private void handleAddExposureMessage(CompoundTag tag) {
        blockEntity.addExposureFromUi(tag.getString("target"), tag.getString("capability"));
        lastSignature = "";
        refresh();
    }

    private void handleRemoveExposureMessage(CompoundTag tag) {
        blockEntity.removeExposureFromUi(tag.getString("target"), tag.getString("capability"));
        lastSignature = "";
        refresh();
    }

    private UIElement resizeHandle(int boundary) {
        UIElement handle = new UIElement();
        handle.setId("ccs_super_hub_resize_" + boundary);
        handle.layout(style -> style.width(RESIZE_HANDLE_WIDTH).height(ROW_HEIGHT));
        handle.style(style -> style.backgroundTexture(new ColorRectTexture(0x503A5A66))
                .tooltips(tr("gui.ccsconnector.super_hub.drag_resize")));
        handle.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            resizingBoundary = boundary;
            resizeLastX = event.x;
            event.stopPropagation();
        });
        handle.addEventListener(UIEvents.DOUBLE_CLICK, event -> {
            resetColumnWidths();
            event.stopPropagation();
        });
        return handle;
    }

    private void handleResizeMove(UIEvent event) {
        if (resizingBoundary < 0) {
            return;
        }
        if (!isMouseDown(0)) {
            resizingBoundary = -1;
            return;
        }
        float delta = event.x - resizeLastX;
        if (Math.abs(delta) < 0.5F) {
            return;
        }
        if (resizeColumns(resizingBoundary, delta)) {
            rebuildListHeader(listHeader);
            rebuildRows();
            lastSignature = blockEntity.signalSignature()
                    + "|target=" + target.serializedName()
                    + "|device=" + selectedDevice;
        }
        event.stopPropagation();
    }

    private void handleResizeEnd(UIEvent event) {
        if (resizingBoundary >= 0) {
            resizingBoundary = -1;
            event.stopPropagation();
        }
    }

    private boolean resizeColumns(int boundary, float delta) {
        int left = boundary;
        int right = boundary + 1;
        if (left < 0 || right >= columnWidths.length) {
            return false;
        }
        float maxShrinkLeft = columnWidths[left] - minColumnWidths[left];
        float maxGrowLeft = columnWidths[right] - minColumnWidths[right];
        float applied = clamp(delta, -maxShrinkLeft, maxGrowLeft);
        if (Math.abs(applied) < 0.5F) {
            return false;
        }
        columnWidths[left] += applied;
        columnWidths[right] -= applied;
        resizeLastX += applied;
        return true;
    }

    private void resetColumnWidths() {
        float[] defaults = {28.0F, 104.0F, 32.0F, 36.0F, 28.0F, 48.0F, 22.0F};
        System.arraycopy(defaults, 0, columnWidths, 0, columnWidths.length);
        rebuildListHeader(listHeader);
        rebuildRows();
        lastSignature = blockEntity.signalSignature()
                + "|target=" + target.serializedName()
                + "|device=" + selectedDevice;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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

    private static Selector<String> selector(List<String> candidates, String selected, float width) {
        Selector<String> selector = new Selector<>();
        selector.setCandidates(candidates);
        selector.setSelected(selected);
        selector.layout(style -> style.width(width).height(16.0F));
        selector.style(style -> style.backgroundTexture(new ColorRectTexture(FIELD_BACKGROUND)));
        selector.selectorStyle(style -> style.maxItemCount(5)
                .scrollerViewHeight(72.0F)
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
