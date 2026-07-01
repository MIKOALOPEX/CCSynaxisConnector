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
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

public final class LogOutputAssistantListUi extends UIElement {
    private static final float ROOT_WIDTH = 390.0F;
    private static final float ROOT_HEIGHT = 292.0F;
    private static final float ROW_HEIGHT = 16.0F;
    private static final String APPLY_SETTINGS_MESSAGE = "apply_settings";
    private static final String ADD_COLUMN_MESSAGE = "add_column";
    private static final String RENAME_COLUMN_MESSAGE = "rename_column";
    private static final String REMOVE_COLUMN_MESSAGE = "remove_column";
    private static final String REFRESH_IMPORTS_MESSAGE = "refresh_imports";
    private static final String IMPORT_SERVER_FILE_MESSAGE = "import_server_file";
    private static final String IMPORT_CLIENT_START_MESSAGE = "import_client_start";
    private static final String IMPORT_CLIENT_CHUNK_MESSAGE = "import_client_chunk";
    private static final String IMPORT_CLIENT_FINISH_MESSAGE = "import_client_finish";
    private static final String IMPORT_CLIENT_ERROR_MESSAGE = "import_client_error";
    private static final int LOCAL_IMPORT_MAX_CANDIDATES = 50;
    private static final int LOCAL_IMPORT_CHUNK_SIZE = 12000;
    private static final int LOCAL_IMPORT_MAX_CHARS = 4 * 1024 * 1024;
    private static final String OUTPUT_DIRECTORY_NAME = "ccsconnector_outputs";

    private static final int ROOT_BACKGROUND = 0xE0182024;
    private static final int PANEL_BACKGROUND = 0xD0263036;
    private static final int ROW_BACKGROUND = 0xB01A2228;
    private static final int FIELD_BACKGROUND = 0xE00D1418;
    private static final int BUTTON_BACKGROUND = 0xFF2F4650;
    private static final int BUTTON_HOVER = 0xFF3A5A66;
    private static final int BUTTON_PRESSED = 0xFF243740;
    private static final int TAB_ACTIVE = 0xFF3E5A64;
    private static final int TAB_ACTIVE_HOVER = 0xFF4A6872;
    private static final int DANGER_BACKGROUND = 0xFF623336;
    private static final int DANGER_HOVER = 0xFF7A4146;
    private static final int TEXT_PRIMARY = 0xFFEAF6F3;
    private static final int TEXT_MUTED = 0xFF9FB6B7;
    private static final int TEXT_ACCENT = 0xFF7FE0C5;
    private static final int TEXT_WARNING = 0xFFFFC170;

    private final LogOutputAssistantBlockEntity blockEntity;
    private final TextField logNameField;
    private final Selector<String> rowIdSelector;
    private final Selector<String> writeModeSelector;
    private final Selector<String> createModeSelector;
    private final Selector<String> createColumnSelector;
    private final Selector<String> emptyRowSelector;
    private final Selector<String> importFileSelector;
    private final Selector<String> clientImportFileSelector;
    private final TextField intervalField;
    private final TextField newColumnNameField;
    private final Selector<String> newColumnTypeSelector;
    private final ScrollerView columnList;
    private final UIElement tabBar;
    private final UIElement pageContainer;
    private final Label outputFileLabel;
    private final Label tableStateLabel;
    private final Label statusLabel;

    private Page activePage = Page.SETTINGS;
    private String lastStructureSignature = "";
    private String lastStatus = "";
    private String lastSettingsSignature = "";
    private String lastColumnPlaceholder = "";
    private String lastImportCandidatesSignature = "";
    private String lastClientImportCandidatesSignature = "";
    private List<String> clientImportCandidates = List.of();

    private LogOutputAssistantListUi(LogOutputAssistantBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
        this.logNameField = textField("synaxis_log", 150.0F);
        this.rowIdSelector = selector(List.of("tick", "row_index", "none"), "tick", 76.0F, 3);
        this.writeModeSelector = selector(List.of("every_tick", "on_change", "interval"), "every_tick", 86.0F, 3);
        this.createModeSelector = selector(List.of("any_input", "specific_input", "immediate"), "any_input", 96.0F, 3);
        this.createColumnSelector = selector(List.of(), "", 94.0F, 5);
        this.emptyRowSelector = selector(List.of("skip_empty_rows", "write_empty_rows"), "skip_empty_rows", 112.0F, 2);
        this.importFileSelector = selector(List.of(), "", 180.0F, 5);
        this.clientImportFileSelector = selector(List.of(), "", 180.0F, 5);
        this.intervalField = numberField("20", 58.0F);
        this.newColumnNameField = textField("column_1", 118.0F);
        this.newColumnTypeSelector = selector(List.of("real", "boolean"), "real", 70.0F, 2);
        this.columnList = new ScrollerView();
        this.tabBar = tabBar();
        this.pageContainer = pageContainer();
        this.outputFileLabel = label("", TEXT_ACCENT, Horizontal.LEFT, 7.0F, 150.0F);
        this.tableStateLabel = label("", TEXT_MUTED, Horizontal.LEFT, 7.0F, 86.0F);
        this.statusLabel = label("", TEXT_MUTED, Horizontal.LEFT, 8.0F);

        setId("ccs_log_output_root");
        layout(style -> style.width(ROOT_WIDTH)
                .height(ROOT_HEIGHT)
                .paddingAll(8.0F)
                .gapRow(4.0F)
                .flexDirection(FlexDirection.COLUMN)
                .alignItems(AlignItems.STRETCH));
        style(style -> style.backgroundTexture(new ColorRectTexture(ROOT_BACKGROUND)));
        onMessage(APPLY_SETTINGS_MESSAGE, this::handleApplySettingsMessage);
        onMessage(ADD_COLUMN_MESSAGE, this::handleAddColumnMessage);
        onMessage(RENAME_COLUMN_MESSAGE, this::handleRenameColumnMessage);
        onMessage(REMOVE_COLUMN_MESSAGE, this::handleRemoveColumnMessage);
        onMessage(REFRESH_IMPORTS_MESSAGE, this::handleRefreshImportsMessage);
        onMessage(IMPORT_SERVER_FILE_MESSAGE, this::handleImportServerFileMessage);
        onMessage(IMPORT_CLIENT_START_MESSAGE, this::handleImportClientStartMessage);
        onMessage(IMPORT_CLIENT_CHUNK_MESSAGE, this::handleImportClientChunkMessage);
        onMessage(IMPORT_CLIENT_FINISH_MESSAGE, this::handleImportClientFinishMessage);
        onMessage(IMPORT_CLIENT_ERROR_MESSAGE, this::handleImportClientErrorMessage);

        rebuildTabs();
        rebuildPage();
        addChildren(title(), tabBar, pageContainer, statusLabel);
        refresh();
    }

    public static ModularUI create(LogOutputAssistantBlockEntity blockEntity, Player player) {
        LogOutputAssistantListUi root = new LogOutputAssistantListUi(blockEntity);
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
        UIElement row = row("ccs_log_output_title", 14.0F, 0);
        row.addChildren(
                label(tr("gui.ccsconnector.log_output.title"), TEXT_PRIMARY, Horizontal.LEFT, 9.0F, 210.0F),
                label(tr("gui.ccsconnector.log_output.subtitle"), TEXT_MUTED, Horizontal.RIGHT, 7.0F, 148.0F));
        return row;
    }

    private UIElement tabBar() {
        UIElement row = row("ccs_log_output_tabs", 18.0F, 4.0F);
        row.style(style -> style.backgroundTexture(new ColorRectTexture(PANEL_BACKGROUND)));
        return row;
    }

    private UIElement pageContainer() {
        UIElement container = new UIElement();
        container.setId("ccs_log_output_page");
        container.layout(style -> style.widthStretch()
                .height(222.0F)
                .flexGrow(1.0F)
                .gapRow(4.0F)
                .flexDirection(FlexDirection.COLUMN)
                .alignItems(AlignItems.STRETCH));
        return container;
    }

    private void rebuildTabs() {
        tabBar.clearAllChildren();
        tabBar.addChildren(
                tabButton(trText("gui.ccsconnector.log_output.tab.settings"), Page.SETTINGS),
                tabButton(trText("gui.ccsconnector.log_output.tab.table"), Page.COLUMNS),
                label("", TEXT_MUTED, Horizontal.LEFT, 7.0F, 204.0F));
    }

    private void rebuildPage() {
        pageContainer.clearAllChildren();
        if (activePage == Page.SETTINGS) {
            pageContainer.addChildren(settingsPanel(), importPanel(), settingsSpacer());
        } else {
            pageContainer.addChildren(createColumnPanel(), listHeader(), configureList());
            rebuildRows(blockEntity.columns());
        }
    }

    private UIElement settingsPanel() {
        UIElement panel = panel("ccs_log_output_settings", 104.0F);
        UIElement labels = row("ccs_log_output_setting_labels", 10.0F, 4.0F);
        labels.addChildren(
                label(tr("gui.ccsconnector.log_output.log_name"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 150.0F),
                label(tr("gui.ccsconnector.log_output.row_id"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 76.0F),
                label(tr("gui.ccsconnector.log_output.empty_rows"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 112.0F));

        UIElement controls = row("ccs_log_output_setting_controls", 18.0F, 4.0F);
        controls.addChildren(
                logNameField,
                rowIdSelector,
                emptyRowSelector);

        UIElement createLabels = row("ccs_log_output_create_mode_labels", 10.0F, 4.0F);
        createLabels.addChildren(
                label(tr("gui.ccsconnector.log_output.write"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 86.0F),
                label(tr("gui.ccsconnector.log_output.interval_ticks"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 58.0F),
                label(tr("gui.ccsconnector.log_output.create_when"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 96.0F),
                label(tr("gui.ccsconnector.log_output.specific_column"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 94.0F));

        UIElement createControls = row("ccs_log_output_create_mode_controls", 18.0F, 4.0F);
        createControls.addChildren(
                writeModeSelector,
                intervalField,
                createModeSelector,
                createColumnSelector);

        UIElement actionRow = row("ccs_log_output_setting_actions", 18.0F, 4.0F);
        actionRow.addChildren(
                label("", TEXT_MUTED, Horizontal.LEFT, 7.0F, 290.0F),
                button(trText("gui.ccsconnector.common.apply"), 48.0F, false, () -> {
                    CompoundTag tag = new CompoundTag();
                    tag.putString("logName", logNameField.getText());
                    tag.putString("rowIdMode", rowIdSelector.getValue());
                    tag.putString("writeMode", writeModeSelector.getValue());
                    tag.putString("createMode", createModeSelector.getValue());
                    tag.putString("createColumn", selectedCreateColumnId());
                    tag.putString("emptyRows", emptyRowSelector.getValue());
                    tag.putString("interval", intervalField.getText());
                    sendMessage(APPLY_SETTINGS_MESSAGE, tag);
                }));

        panel.addChildren(labels, controls, createLabels, createControls, actionRow);
        return panel;
    }

    private UIElement importPanel() {
        UIElement panel = panel("ccs_log_output_import", 112.0F);
        UIElement fileLabels = row("ccs_log_output_import_file_labels", 10.0F, 4.0F);
        fileLabels.addChildren(
                label(tr("gui.ccsconnector.log_output.output_file"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 150.0F),
                label(tr("gui.ccsconnector.log_output.structure"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 86.0F),
                label(tr("gui.ccsconnector.log_output.only_output_folders"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 96.0F));

        UIElement fileControls = row("ccs_log_output_import_file_controls", 18.0F, 4.0F);
        fileControls.addChildren(
                outputFileLabel,
                tableStateLabel,
                label("", TEXT_MUTED, Horizontal.LEFT, 7.0F, 96.0F));

        UIElement clientLabels = row("ccs_log_output_import_client_labels", 10.0F, 4.0F);
        clientLabels.addChildren(
                label(tr("gui.ccsconnector.log_output.client_output_csv"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 180.0F),
                label(tr("gui.ccsconnector.log_output.client_output_hint"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 154.0F));

        UIElement clientControls = row("ccs_log_output_import_client_controls", 18.0F, 4.0F);
        clientControls.addChildren(
                clientImportFileSelector,
                button(trText("gui.ccsconnector.common.scan"), 44.0F, false, this::refreshClientImportFiles),
                button(trText("gui.ccsconnector.common.use"), 40.0F, false, this::importSelectedClientCsv));

        UIElement serverLabels = row("ccs_log_output_import_server_labels", 10.0F, 4.0F);
        serverLabels.addChildren(
                label(tr("gui.ccsconnector.log_output.server_output_csv"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 180.0F),
                label(tr("gui.ccsconnector.log_output.server_output_hint"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 154.0F));

        UIElement serverControls = row("ccs_log_output_import_server_controls", 18.0F, 4.0F);
        serverControls.addChildren(
                importFileSelector,
                button(trText("gui.ccsconnector.common.scan"), 44.0F, false, () -> sendMessage(REFRESH_IMPORTS_MESSAGE, new CompoundTag())),
                button(trText("gui.ccsconnector.common.use"), 40.0F, false, () -> {
                    CompoundTag tag = new CompoundTag();
                    tag.putString("file", importFileSelector.getValue());
                    sendMessage(IMPORT_SERVER_FILE_MESSAGE, tag);
                }));
        panel.addChildren(fileLabels, fileControls, clientLabels, clientControls, serverLabels, serverControls);
        return panel;
    }

    private UIElement settingsSpacer() {
        UIElement spacer = new UIElement();
        spacer.setId("ccs_log_output_settings_spacer");
        spacer.layout(style -> style.widthStretch()
                .height(0.0F)
                .flexGrow(1.0F));
        return spacer;
    }

    private UIElement createColumnPanel() {
        UIElement panel = panel("ccs_log_output_create_column", 42.0F);
        UIElement labels = row("ccs_log_output_create_labels", 10.0F, 4.0F);
        labels.addChildren(
                label(tr("gui.ccsconnector.log_output.column_name"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 118.0F),
                label(tr("gui.ccsconnector.common.type"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 70.0F),
                label(tr("gui.ccsconnector.log_output.adds_input_port"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 122.0F),
                label("", TEXT_MUTED, Horizontal.LEFT, 7.0F, 36.0F));

        UIElement controls = row("ccs_log_output_create_controls", 18.0F, 4.0F);
        controls.addChildren(
                newColumnNameField,
                newColumnTypeSelector,
                label("", TEXT_MUTED, Horizontal.LEFT, 7.0F, 122.0F),
                button(trText("gui.ccsconnector.common.add"), 36.0F, false, () -> {
                    CompoundTag tag = new CompoundTag();
                    tag.putString("name", newColumnNameField.getText());
                    tag.putString("type", newColumnTypeSelector.getValue());
                    sendMessage(ADD_COLUMN_MESSAGE, tag);
                }));
        panel.addChildren(labels, controls);
        return panel;
    }

    private UIElement listHeader() {
        UIElement header = row("ccs_log_output_header", 12.0F, 4.0F);
        header.addChildren(
                label(tr("gui.ccsconnector.common.column"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 104.0F),
                label(tr("gui.ccsconnector.common.type"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 48.0F),
                label(tr("gui.ccsconnector.common.port"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 88.0F),
                label(tr("gui.ccsconnector.common.value"), TEXT_MUTED, Horizontal.LEFT, 7.0F, 56.0F),
                label("", TEXT_MUTED, Horizontal.LEFT, 7.0F, 56.0F));
        return header;
    }

    private ScrollerView configureList() {
        columnList.setId("ccs_log_output_columns");
        columnList.layout(style -> style.widthStretch()
                .height(152.0F)
                .flexGrow(1.0F));
        columnList.style(style -> style.backgroundTexture(new ColorRectTexture(PANEL_BACKGROUND)));
        columnList.viewContainer(container -> container.layout(style -> style.paddingAll(4.0F)
                .gapRow(3.0F)
                .flexDirection(FlexDirection.COLUMN)
                .alignItems(AlignItems.STRETCH)));
        return columnList;
    }

    private void refresh() {
        String settingsSignature = blockEntity.logName()
                + "|"
                + blockEntity.rowIdModeName()
                + "|"
                + blockEntity.writeModeName()
                + "|"
                + blockEntity.createModeName()
                + "|"
                + blockEntity.createColumnId()
                + "|"
                + blockEntity.emptyRowModeName()
                + "|"
                + blockEntity.intervalTicks()
                + "|"
                + blockEntity.outputFileNameForUi()
                + "|"
                + blockEntity.tableHasData();
        if (!Objects.equals(settingsSignature, lastSettingsSignature)) {
            logNameField.setText(blockEntity.logName());
            rowIdSelector.setSelected(blockEntity.rowIdModeName());
            writeModeSelector.setSelected(blockEntity.writeModeName());
            createModeSelector.setSelected(blockEntity.createModeName());
            refreshCreateColumnSelector();
            emptyRowSelector.setSelected(blockEntity.emptyRowModeName());
            intervalField.setText(String.valueOf(blockEntity.intervalTicks()));
            outputFileLabel.setText(Component.literal(blockEntity.outputFileNameForUi()));
            tableStateLabel.setText(tr(blockEntity.tableHasData()
                    ? "gui.ccsconnector.common.locked"
                    : "gui.ccsconnector.common.editable"));
            lastSettingsSignature = settingsSignature;
        }

        String importCandidatesSignature = String.join("|", blockEntity.importCandidates());
        if (!Objects.equals(importCandidatesSignature, lastImportCandidatesSignature)) {
            refreshImportFileSelector();
            lastImportCandidatesSignature = importCandidatesSignature;
        }

        String structureSignature = blockEntity.structureSignature();
        if (!Objects.equals(structureSignature, lastStructureSignature)) {
            rebuildRows(blockEntity.columns());
            lastStructureSignature = structureSignature;
        }

        String status = blockEntity.status();
        if (!Objects.equals(status, lastStatus)) {
            statusLabel.setText(Component.literal(status));
            lastStatus = status;
        }

        String placeholder = blockEntity.nextGeneratedColumnNamePreview();
        if (!Objects.equals(placeholder, lastColumnPlaceholder)) {
            newColumnNameField.textFieldStyle(style -> style.placeholder(Component.literal(placeholder)));
            lastColumnPlaceholder = placeholder;
        }
    }

    private void rebuildRows(Collection<LogOutputColumn> columns) {
        columnList.clearAllScrollViewChildren();
        refreshCreateColumnSelector();
        if (columns.isEmpty()) {
            UIElement empty = row("ccs_log_output_empty", ROW_HEIGHT, 0);
            empty.addChild(label(tr("gui.ccsconnector.log_output.empty_columns"), TEXT_MUTED, Horizontal.CENTER, 8.0F, 358.0F));
            columnList.addScrollViewChild(empty);
            return;
        }
        for (LogOutputColumn column : columns) {
            columnList.addScrollViewChild(columnRow(column));
        }
    }

    private UIElement columnRow(LogOutputColumn column) {
        UIElement row = row("ccs_log_output_column_" + column.id(), ROW_HEIGHT, 4.0F);
        row.style(style -> style.backgroundTexture(new ColorRectTexture(ROW_BACKGROUND)));
        TextField name = textField(column.name(), 104.0F);
        name.setText(column.name());
        boolean locked = blockEntity.tableHasData();
        row.addChildren(
                name,
                label(column.type().serializedName(), TEXT_ACCENT, Horizontal.LEFT, 7.0F, 48.0F),
                label(column.portName(), TEXT_WARNING, Horizontal.LEFT, 7.0F, 88.0F),
                label(!column.activated() || column.value() == null ? "null" : String.valueOf(column.value()),
                        TEXT_MUTED, Horizontal.LEFT, 7.0F, 56.0F),
                locked ? label(tr("gui.ccsconnector.common.locked"), TEXT_MUTED, Horizontal.CENTER, 7.0F, 56.0F) : button("S", 26.0F, false, () -> {
                    if (locked) {
                        return;
                    }
                    CompoundTag tag = new CompoundTag();
                    tag.putString("id", column.id());
                    tag.putString("name", name.getText());
                    sendMessage(RENAME_COLUMN_MESSAGE, tag);
                }),
                locked ? label("", TEXT_MUTED, Horizontal.LEFT, 7.0F, 0.0F) : button("X", 26.0F, true, () -> {
                    if (locked) {
                        return;
                    }
                    CompoundTag tag = new CompoundTag();
                    tag.putString("id", column.id());
                    sendMessage(REMOVE_COLUMN_MESSAGE, tag);
                }));
        row.style(style -> style.tooltips(
                tr("gui.ccsconnector.tooltip.port", column.portName()),
                tr("gui.ccsconnector.tooltip.type", column.type().serializedName()),
                tr("gui.ccsconnector.tooltip.activated", column.activated()
                        ? trText("gui.ccsconnector.common.yes")
                        : trText("gui.ccsconnector.common.no")),
                tr("gui.ccsconnector.tooltip.cached_value",
                        !column.activated() || column.value() == null ? "null" : column.value()),
                tr(locked
                        ? "gui.ccsconnector.log_output.tooltip.locked"
                        : "gui.ccsconnector.log_output.tooltip.save")));
        return row;
    }

    private void handleApplySettingsMessage(CompoundTag tag) {
        blockEntity.setLogNameFromUi(tag.getString("logName"));
        blockEntity.setRowIdModeFromUi(tag.getString("rowIdMode"));
        blockEntity.setWriteModeFromUi(tag.getString("writeMode"), tag.getString("interval"));
        blockEntity.setCreateModeFromUi(tag.getString("createMode"), tag.getString("createColumn"));
        blockEntity.setEmptyRowModeFromUi(tag.getString("emptyRows"));
    }

    private void handleAddColumnMessage(CompoundTag tag) {
        blockEntity.addColumnFromUi(tag.getString("name"), tag.getString("type"));
        newColumnNameField.setText("");
    }

    private void handleRenameColumnMessage(CompoundTag tag) {
        blockEntity.renameColumnFromUi(tag.getString("id"), tag.getString("name"));
    }

    private void handleRemoveColumnMessage(CompoundTag tag) {
        blockEntity.removeColumnFromUi(tag.getString("id"));
    }

    private void handleRefreshImportsMessage(CompoundTag tag) {
        blockEntity.refreshImportCandidatesFromUi();
    }

    private void handleImportServerFileMessage(CompoundTag tag) {
        blockEntity.importServerOutputFileFromUi(tag.getString("file"));
    }

    private void handleImportClientStartMessage(CompoundTag tag) {
        blockEntity.beginClientImportFromUi(tag.getString("fileName"), tag.getInt("length"));
    }

    private void handleImportClientChunkMessage(CompoundTag tag) {
        blockEntity.appendClientImportChunkFromUi(tag.getString("chunk"));
    }

    private void handleImportClientFinishMessage(CompoundTag tag) {
        blockEntity.finishClientImportFromUi();
    }

    private void handleImportClientErrorMessage(CompoundTag tag) {
        blockEntity.clientImportErrorFromUi(tag.getString("message"));
    }

    private void importLocalCsv() {
        try {
            Path path = selectedClientImportFile();
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.length() > LOCAL_IMPORT_MAX_CHARS) {
                sendClientImportError(trText("gui.ccsconnector.log_output.error.file_too_large"));
                return;
            }
            String fileName = path.getFileName() == null ? "import.csv" : path.getFileName().toString();
            CompoundTag start = new CompoundTag();
            start.putString("fileName", fileName);
            start.putInt("length", content.length());
            sendMessage(IMPORT_CLIENT_START_MESSAGE, start);
            for (int offset = 0; offset < content.length(); offset += LOCAL_IMPORT_CHUNK_SIZE) {
                CompoundTag chunk = new CompoundTag();
                chunk.putString("chunk", content.substring(offset, Math.min(content.length(), offset + LOCAL_IMPORT_CHUNK_SIZE)));
                sendMessage(IMPORT_CLIENT_CHUNK_MESSAGE, chunk);
            }
            sendMessage(IMPORT_CLIENT_FINISH_MESSAGE, new CompoundTag());
        } catch (IOException | RuntimeException exception) {
            sendClientImportError(exception.getClass().getSimpleName());
        }
    }

    private void refreshClientImportFiles() {
        try {
            Path outputDir = clientOutputDirectory();
            Files.createDirectories(outputDir);
            try (Stream<Path> stream = Files.list(outputDir)) {
                clientImportCandidates = stream
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".csv"))
                        .sorted(Comparator.naturalOrder())
                        .limit(LOCAL_IMPORT_MAX_CANDIDATES)
                        .toList();
            }
            refreshClientImportFileSelector();
            if (clientImportCandidates.isEmpty()) {
                sendClientImportError(trText("gui.ccsconnector.log_output.error.no_local_csv"));
            }
        } catch (IOException | RuntimeException exception) {
            sendClientImportError(exception.getClass().getSimpleName());
        }
    }

    private void importSelectedClientCsv() {
        importLocalCsv();
    }

    private void sendClientImportError(String message) {
        CompoundTag tag = new CompoundTag();
        tag.putString("message", message);
        sendMessage(IMPORT_CLIENT_ERROR_MESSAGE, tag);
    }

    private Button tabButton(String text, Page page) {
        Button button = new Button();
        button.setText(text);
        button.layout(style -> style.width(78.0F)
                .height(16.0F)
                .alignItems(AlignItems.CENTER)
                .justifyContent(AlignContent.CENTER));
        button.textStyle(style -> style.textColor(TEXT_PRIMARY)
                .fontSize(7.0F)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER)
                .textShadow(false));
        boolean active = activePage == page;
        int base = active ? TAB_ACTIVE : FIELD_BACKGROUND;
        int hover = active ? TAB_ACTIVE_HOVER : BUTTON_HOVER;
        button.buttonStyle(style -> style.baseTexture(new ColorRectTexture(base))
                .hoverTexture(new ColorRectTexture(hover))
                .pressedTexture(new ColorRectTexture(BUTTON_PRESSED)));
        button.setOnClick(event -> {
            if (activePage != page) {
                activePage = page;
                rebuildTabs();
                rebuildPage();
                refresh();
            }
        });
        return button;
    }

    private enum Page {
        SETTINGS,
        COLUMNS
    }

    private void refreshCreateColumnSelector() {
        List<String> candidates = blockEntity.columns().stream()
                .map(column -> column.id() + " | " + column.name())
                .toList();
        String selected = "";
        for (String candidate : candidates) {
            if (candidate.startsWith(blockEntity.createColumnId() + " | ")) {
                selected = candidate;
                break;
            }
        }
        createColumnSelector.setCandidates(candidates);
        createColumnSelector.setSelected(selected);
    }

    private void refreshImportFileSelector() {
        List<String> candidates = blockEntity.importCandidates();
        String selected = importFileSelector.getValue();
        if (selected == null || !candidates.contains(selected)) {
            selected = candidates.isEmpty() ? "" : candidates.getFirst();
        }
        importFileSelector.setCandidates(candidates);
        importFileSelector.setSelected(selected);
    }

    private void refreshClientImportFileSelector() {
        String signature = String.join("|", clientImportCandidates);
        if (Objects.equals(signature, lastClientImportCandidatesSignature)
                && Objects.equals(clientImportFileSelector.getValue(), firstOrSelectedClientCandidate())) {
            return;
        }
        String selected = firstOrSelectedClientCandidate();
        clientImportFileSelector.setCandidates(clientImportCandidates);
        clientImportFileSelector.setSelected(selected);
        lastClientImportCandidatesSignature = signature;
    }

    private String firstOrSelectedClientCandidate() {
        String selected = clientImportFileSelector.getValue();
        if (selected == null || !clientImportCandidates.contains(selected)) {
            selected = clientImportCandidates.isEmpty() ? "" : clientImportCandidates.getFirst();
        }
        return selected;
    }

    private Path selectedClientImportFile() {
        String selected = clientImportFileSelector.getValue();
        if (selected == null || !clientImportCandidates.contains(selected)) {
            throw new IllegalArgumentException(trText("gui.ccsconnector.log_output.error.no_client_csv_selected"));
        }
        Path outputDir = clientOutputDirectory();
        Path file = outputDir.resolve(selected).normalize();
        if (!file.startsWith(outputDir)) {
            throw new IllegalArgumentException(trText("gui.ccsconnector.log_output.error.invalid_client_csv"));
        }
        return file;
    }

    private static Path clientOutputDirectory() {
        return FMLPaths.GAMEDIR.get().resolve(OUTPUT_DIRECTORY_NAME).toAbsolutePath().normalize();
    }

    private String selectedCreateColumnId() {
        String value = createColumnSelector.getValue();
        if (value == null || value.isBlank()) {
            return "";
        }
        int split = value.indexOf(" | ");
        return split < 0 ? value : value.substring(0, split);
    }

    private static UIElement panel(String id, float height) {
        UIElement panel = new UIElement();
        panel.setId(id);
        panel.layout(style -> style.widthStretch()
                .height(height)
                .paddingAll(5.0F)
                .gapRow(4.0F)
                .flexDirection(FlexDirection.COLUMN)
                .alignItems(AlignItems.STRETCH));
        panel.style(style -> style.backgroundTexture(new ColorRectTexture(PANEL_BACKGROUND)));
        return panel;
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
                .fontSize(7.0F)
                .textShadow(false));
        return field;
    }

    private static TextField numberField(String placeholder, float width) {
        TextField field = textField(placeholder, width);
        field.setNumbersOnlyInt(1, 72000);
        return field;
    }

    private static Selector<String> selector(List<String> candidates, String selected, float width, int maxItems) {
        Selector<String> selector = new Selector<>();
        selector.setCandidates(candidates);
        selector.setSelected(selected);
        selector.layout(style -> style.width(width).height(16.0F));
        selector.style(style -> style.backgroundTexture(new ColorRectTexture(FIELD_BACKGROUND)));
        selector.selectorStyle(style -> style.maxItemCount(maxItems)
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
                .fontSize(7.0F)
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
