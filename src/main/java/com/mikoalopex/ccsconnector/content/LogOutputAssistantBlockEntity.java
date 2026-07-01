package com.mikoalopex.ccsconnector.content;

import com.mikoalopex.ccsconnector.CCSConnector;
import com.mikoalopex.ccsconnector.synaxis.LogOutputAssistantComponent;
import com.mikoalopex.ccsconnector.synaxis.LogOutputAssistantPlantPort;
import com.verr1.synaxis.foundation.blockentity.NetworkBlockEntity;
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
import com.verr1.synaxis.foundation.state.StateSchema;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class LogOutputAssistantBlockEntity extends NetworkBlockEntity implements CimulinkEndpoint, CimulinkEndpointProvider {
    private static final String ENDPOINT_ID_TAG = "EndpointId";
    private static final String LOG_NAME_TAG = "LogName";
    private static final String ROW_ID_MODE_TAG = "RowIdMode";
    private static final String WRITE_MODE_TAG = "WriteMode";
    private static final String CREATE_MODE_TAG = "CreateMode";
    private static final String CREATE_COLUMN_ID_TAG = "CreateColumnId";
    private static final String SKIP_EMPTY_ROWS_TAG = "SkipEmptyRows";
    private static final String CUSTOM_OUTPUT_FILE_TAG = "CustomOutputFile";
    private static final String TABLE_HAS_DATA_TAG = "TableHasData";
    private static final String IMPORT_CANDIDATES_TAG = "ImportCandidates";
    private static final String LOG_STARTED_TAG = "LogStarted";
    private static final String INTERVAL_TICKS_TAG = "IntervalTicks";
    private static final String COLUMNS_TAG = "Columns";
    private static final String ROW_INDEX_TAG = "RowIndex";
    private static final String LAST_WRITE_TICK_TAG = "LastWriteTick";
    private static final String HEADER_SIGNATURE_TAG = "HeaderSignature";
    private static final String STATUS_TAG = "Status";
    private static final int MAX_COLUMNS = 10;
    private static final int MAX_IMPORT_CANDIDATES = 50;
    private static final int MAX_IMPORT_CHARS = 4 * 1024 * 1024;
    private static final String OUTPUT_DIRECTORY_NAME = "ccsconnector_outputs";
    private static final EndpointRuntimeBinding BINDING = EndpointRuntimeBinding.allowing();
    private static final Map<Path, UUID> CLAIMED_OUTPUT_FILES = new LinkedHashMap<>();

    private final LinkedHashMap<String, LogOutputColumn> columns = new LinkedHashMap<>();
    private final List<String> importCandidates = new ArrayList<>();

    private EndpointId endpointId = EndpointId.random();
    private boolean endpointRegistered;
    private boolean endpointDirty = true;
    private Path claimedOutputFile;
    private String logName = "synaxis_log";
    private String customOutputFile = "";
    private LogOutputRowIdMode rowIdMode = LogOutputRowIdMode.TICK;
    private LogOutputWriteMode writeMode = LogOutputWriteMode.EVERY_TICK;
    private LogOutputCreateMode createMode = LogOutputCreateMode.ANY_INPUT;
    private String createColumnId = "";
    private boolean skipEmptyRows = true;
    private boolean tableHasData;
    private boolean logStarted;
    private int intervalTicks = 20;
    private long rowIndex;
    private long lastWriteTick = Long.MIN_VALUE;
    private String lastWrittenHeaderSignature = "";
    private String status = "Ready";
    private boolean valuesDirty;
    private long lastClientSyncTick = Long.MIN_VALUE;
    private String pendingImportFileName = "";
    private StringBuilder pendingImportContent;

    public LogOutputAssistantBlockEntity(BlockPos pos, BlockState state) {
        super(CCSConnector.LOG_OUTPUT_ASSISTANT_BE.get(), pos, state);
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
        writeIfNeeded(serverLevel);
    }

    public LogOutputAssistantPlantPort createPlantPort(EndpointId endpointId, String requestedName) {
        String name = requestedName == null || requestedName.isBlank() ? "Log Output Assistant" : requestedName;
        return new LogOutputAssistantPlantPort(this, endpointId, name);
    }

    @Override
    public void invalidate() {
        unregisterEndpoint();
        releaseOutputFileClaim();
        super.invalidate();
    }

    @Override
    public void onChunkUnloaded() {
        unregisterEndpoint();
        releaseOutputFileClaim();
        super.onChunkUnloaded();
    }

    @Override
    public void remove() {
        unregisterEndpoint();
        releaseOutputFileClaim();
        super.remove();
    }

    @Override
    public void destroy() {
        unregisterEndpoint();
        releaseOutputFileClaim();
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
        return "Log Output Assistant";
    }

    @Override
    public EndpointDefinition definition() {
        ComponentSchema schema = schema();
        return new EndpointDefinition(
                LogOutputAssistantComponent.ID,
                new LogOutputAssistantComponent.Config(this, schema),
                schema,
                new EndpointUiHints(portLabels()));
    }

    @Override
    public EndpointRuntimeBinding binding() {
        return BINDING;
    }

    public synchronized Collection<LogOutputColumn> columns() {
        return List.copyOf(columns.values());
    }

    public synchronized ComponentSchema schema() {
        List<PortDef> inputs = new ArrayList<>();
        columns.values().forEach(column -> inputs.add(column.portDef()));
        return ComponentSchema.of(inputs, List.of());
    }

    public void writeFromSynaxis(String portName, SignalValue value) {
        long tick = level == null ? -1L : level.getGameTime();
        synchronized (this) {
            LogOutputColumn current = columnByPort(portName);
            if (current == null) {
                return;
            }
            Object next = LogOutputColumn.luaValue(current.type(), value);
            if (next == null) {
                return;
            }
            if (!current.activated() && LogOutputColumn.isDefaultValue(current.type(), next)) {
                return;
            }
            if (!current.activated() || !Objects.equals(current.value(), next)) {
                valuesDirty = true;
            }
            columns.put(current.id(), current.withValue(next, true, tick));
            setChanged();
            syncToClient();
        }
    }

    public synchronized String logName() {
        return logName;
    }

    public synchronized String rowIdModeName() {
        return rowIdMode.serializedName();
    }

    public synchronized String writeModeName() {
        return writeMode.serializedName();
    }

    public synchronized String createModeName() {
        return createMode.serializedName();
    }

    public synchronized String createColumnId() {
        return createColumnId;
    }

    public synchronized boolean skipEmptyRows() {
        return skipEmptyRows;
    }

    public synchronized String emptyRowModeName() {
        return skipEmptyRows ? "skip_empty_rows" : "write_empty_rows";
    }

    public synchronized int intervalTicks() {
        return intervalTicks;
    }

    public synchronized boolean tableHasData() {
        return tableHasData;
    }

    public synchronized String outputFileNameForUi() {
        return customOutputFile.isBlank() ? outputFileName(logName) : customOutputFile;
    }

    public synchronized List<String> importCandidates() {
        return List.copyOf(importCandidates);
    }

    public synchronized String status() {
        return status;
    }

    public synchronized String nextGeneratedColumnNamePreview() {
        return uniqueColumnName("column_" + (columns.size() + 1), null);
    }

    public synchronized List<Map<String, Object>> columnInfoList() {
        List<Map<String, Object>> result = new ArrayList<>();
        columns.values().forEach(column -> result.add(column.info()));
        return result;
    }

    public String setLogNameFromUi(String name) {
        synchronized (this) {
            String sanitized = sanitizeDisplayName(name, "synaxis_log");
            if (!Objects.equals(logName, sanitized)) {
                logName = sanitized;
                customOutputFile = "";
                releaseOutputFileClaim();
                lastWrittenHeaderSignature = "";
                status = "Log name set to " + logName;
                setChanged();
            }
            syncToClient();
            return status;
        }
    }

    public String setRowIdModeFromUi(String modeName) {
        synchronized (this) {
            rowIdMode = LogOutputRowIdMode.parse(modeName);
            lastWrittenHeaderSignature = "";
            status = "Row id: " + rowIdMode.displayName();
            setChanged();
            syncToClient();
            return status;
        }
    }

    public String setWriteModeFromUi(String modeName, String intervalText) {
        synchronized (this) {
            writeMode = LogOutputWriteMode.parse(modeName);
            intervalTicks = parseInterval(intervalText, intervalTicks);
            status = "Write mode: " + writeMode.displayName();
            setChanged();
            syncToClient();
            return status;
        }
    }

    public String setCreateModeFromUi(String modeName, String columnId) {
        synchronized (this) {
            createMode = LogOutputCreateMode.parse(modeName);
            createColumnId = columns.containsKey(columnId) ? columnId : "";
            if (createMode == LogOutputCreateMode.SPECIFIC_INPUT && createColumnId.isBlank()) {
                createMode = LogOutputCreateMode.ANY_INPUT;
            }
            if (createMode == LogOutputCreateMode.IMMEDIATE) {
                logStarted = true;
                valuesDirty = true;
            }
            status = "Create log: " + createMode.displayName();
            setChanged();
            syncToClient();
            return status;
        }
    }

    public String setEmptyRowModeFromUi(String modeName) {
        synchronized (this) {
            skipEmptyRows = !"write_empty_rows".equals(modeName);
            status = skipEmptyRows ? "Empty rows skipped" : "Empty rows written";
            setChanged();
            syncToClient();
            return status;
        }
    }

    public String refreshImportCandidatesFromUi() {
        if (!(level instanceof ServerLevel serverLevel)) {
            synchronized (this) {
                status = "Import list unavailable";
                syncToClient();
                return status;
            }
        }
        try {
            Path outputDir = outputDirectory(serverLevel);
            Files.createDirectories(outputDir);
            List<String> candidates;
            try (Stream<Path> stream = Files.list(outputDir)) {
                candidates = stream
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".csv"))
                        .sorted(Comparator.naturalOrder())
                        .limit(MAX_IMPORT_CANDIDATES)
                        .toList();
            }
            synchronized (this) {
                importCandidates.clear();
                importCandidates.addAll(candidates);
                status = candidates.isEmpty() ? "No CSV files in output folder" : "Found " + candidates.size() + " CSV files";
                setChanged();
                syncToClient();
                return status;
            }
        } catch (IOException exception) {
            synchronized (this) {
                status = "Import scan failed: " + exception.getClass().getSimpleName();
                syncToClient();
                return status;
            }
        }
    }

    public String importServerOutputFileFromUi(String fileName) {
        if (!(level instanceof ServerLevel serverLevel)) {
            synchronized (this) {
                status = "Import unavailable";
                syncToClient();
                return status;
            }
        }
        try {
            Path outputDir = outputDirectory(serverLevel);
            Path source = safeResolveOutputFile(outputDir, fileName);
            if (!Files.isRegularFile(source)) {
                synchronized (this) {
                    status = "CSV not found in output folder";
                    syncToClient();
                    return status;
                }
            }
            String content = Files.readString(source, StandardCharsets.UTF_8);
            return importCsvContent(serverLevel, source.getFileName().toString(), content, true);
        } catch (IOException | IllegalArgumentException exception) {
            synchronized (this) {
                status = "Import failed: " + exception.getClass().getSimpleName();
                syncToClient();
                return status;
            }
        }
    }

    public String beginClientImportFromUi(String fileName, int length) {
        synchronized (this) {
            if (length < 0 || length > MAX_IMPORT_CHARS) {
                status = "Import too large";
                syncToClient();
                return status;
            }
            pendingImportFileName = fileName == null ? "import.csv" : fileName;
            pendingImportContent = new StringBuilder(Math.min(length, 65536));
            status = "Receiving " + sanitizeFileName(pendingImportFileName);
            syncToClient();
            return status;
        }
    }

    public String appendClientImportChunkFromUi(String chunk) {
        synchronized (this) {
            if (pendingImportContent == null) {
                status = "No active import";
                syncToClient();
                return status;
            }
            String text = chunk == null ? "" : chunk;
            if (pendingImportContent.length() + text.length() > MAX_IMPORT_CHARS) {
                pendingImportContent = null;
                pendingImportFileName = "";
                status = "Import too large";
                syncToClient();
                return status;
            }
            pendingImportContent.append(text);
            return status;
        }
    }

    public String finishClientImportFromUi() {
        String fileName;
        String content;
        synchronized (this) {
            if (pendingImportContent == null) {
                status = "No active import";
                syncToClient();
                return status;
            }
            fileName = pendingImportFileName;
            content = pendingImportContent.toString();
            pendingImportFileName = "";
            pendingImportContent = null;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            synchronized (this) {
                status = "Import unavailable";
                syncToClient();
                return status;
            }
        }
        try {
            return importCsvContent(serverLevel, fileName, content, false);
        } catch (IOException | IllegalArgumentException exception) {
            synchronized (this) {
                status = "Import failed: " + exception.getClass().getSimpleName();
                syncToClient();
                return status;
            }
        }
    }

    public String clientImportErrorFromUi(String message) {
        synchronized (this) {
            pendingImportFileName = "";
            pendingImportContent = null;
            status = "Client import failed: " + sanitizeDisplayName(message, "unknown error");
            syncToClient();
            return status;
        }
    }

    public String addColumnFromUi(String name, String typeName) {
        synchronized (this) {
            if (columns.size() >= MAX_COLUMNS) {
                status = "Maximum 10 columns";
                syncToClient();
                return status;
            }
            BridgeSignalType type = LogOutputColumn.safeType(typeName);
            String displayName = sanitizeDisplayName(name, nextGeneratedColumnNamePreview());
            displayName = uniqueColumnName(displayName, null);
            String portName = uniquePortName(sanitizeSignalName(displayName), null);
            String id = uniqueColumnId();
            LogOutputColumn column = new LogOutputColumn(id, displayName, portName, type, null, false, -1L);
            columns.put(id, column);
            endpointDirty = true;
            lastWrittenHeaderSignature = "";
            status = "Added column " + displayName;
            setChanged();
            syncToClient();
            return status;
        }
    }

    public String removeColumnFromUi(String id) {
        synchronized (this) {
            if (tableHasData) {
                status = "Table has data; columns are locked";
                syncToClient();
                return status;
            }
            LogOutputColumn removed = columns.remove(id);
            if (removed == null) {
                status = "Column not found";
            } else {
                if (Objects.equals(createColumnId, id)) {
                    createColumnId = "";
                    createMode = LogOutputCreateMode.ANY_INPUT;
                }
                endpointDirty = true;
                lastWrittenHeaderSignature = "";
                status = "Removed column " + removed.name();
                setChanged();
            }
            syncToClient();
            return status;
        }
    }

    public String renameColumnFromUi(String id, String name) {
        synchronized (this) {
            if (tableHasData) {
                status = "Table has data; column names are locked";
                syncToClient();
                return status;
            }
            LogOutputColumn current = columns.get(id);
            if (current == null) {
                status = "Column not found";
                syncToClient();
                return status;
            }
            String displayName = uniqueColumnName(sanitizeDisplayName(name, current.name()), id);
            LogOutputColumn updated = new LogOutputColumn(
                    current.id(),
                    displayName,
                    current.portName(),
                    current.type(),
                    current.value(),
                    current.activated(),
                    current.updateTick());
            columns.put(id, updated);
            lastWrittenHeaderSignature = "";
            status = "Renamed column " + displayName;
            setChanged();
            syncToClient();
            return status;
        }
    }

    public synchronized String configurationSignature() {
        StringBuilder builder = new StringBuilder();
        builder.append(logName)
                .append('|')
                .append(rowIdMode.serializedName())
                .append('|')
                .append(writeMode.serializedName())
                .append('|')
                .append(createMode.serializedName())
                .append('|')
                .append(createColumnId)
                .append('|')
                .append(skipEmptyRows)
                .append('|')
                .append(customOutputFile)
                .append('|')
                .append(tableHasData)
                .append('|')
                .append(logStarted)
                .append('|')
                .append(intervalTicks)
                .append('|')
                .append(status)
                .append('|');
        columns.values().forEach(column -> builder.append(column.id())
                .append(':')
                .append(column.name())
                .append(':')
                .append(column.portName())
                .append(':')
                .append(column.type().serializedName())
                .append(':')
                .append(column.value())
                .append(':')
                .append(column.activated())
                .append(':')
                .append(column.updateTick())
                .append(';'));
        return builder.toString();
    }

    public synchronized String structureSignature() {
        StringBuilder builder = new StringBuilder();
        builder.append(logName)
                .append('|')
                .append(rowIdMode.serializedName())
                .append('|')
                .append(writeMode.serializedName())
                .append('|')
                .append(createMode.serializedName())
                .append('|')
                .append(createColumnId)
                .append('|')
                .append(skipEmptyRows)
                .append('|')
                .append(customOutputFile)
                .append('|')
                .append(tableHasData)
                .append('|')
                .append(intervalTicks)
                .append('|');
        importCandidates.forEach(candidate -> builder.append("candidate=").append(candidate).append('|'));
        columns.values().forEach(column -> builder.append(column.id())
                .append(':')
                .append(column.name())
                .append(':')
                .append(column.portName())
                .append(':')
                .append(column.type().serializedName())
                .append(';'));
        return builder.toString();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putUUID(ENDPOINT_ID_TAG, endpointId.value());
        synchronized (this) {
            tag.putString(LOG_NAME_TAG, logName);
            tag.putString(ROW_ID_MODE_TAG, rowIdMode.serializedName());
            tag.putString(WRITE_MODE_TAG, writeMode.serializedName());
            tag.putString(CREATE_MODE_TAG, createMode.serializedName());
            tag.putString(CREATE_COLUMN_ID_TAG, createColumnId);
            tag.putBoolean(SKIP_EMPTY_ROWS_TAG, skipEmptyRows);
            tag.putString(CUSTOM_OUTPUT_FILE_TAG, customOutputFile);
            tag.putBoolean(TABLE_HAS_DATA_TAG, tableHasData);
            tag.putBoolean(LOG_STARTED_TAG, logStarted);
            tag.putInt(INTERVAL_TICKS_TAG, intervalTicks);
            tag.putLong(ROW_INDEX_TAG, rowIndex);
            tag.putLong(LAST_WRITE_TICK_TAG, lastWriteTick);
            tag.putString(HEADER_SIGNATURE_TAG, lastWrittenHeaderSignature);
            tag.putString(STATUS_TAG, status);
            ListTag columnTags = new ListTag();
            columns.values().forEach(column -> columnTags.add(column.toTag()));
            tag.put(COLUMNS_TAG, columnTags);
            ListTag candidateTags = new ListTag();
            importCandidates.forEach(candidate -> candidateTags.add(StringTag.valueOf(candidate)));
            tag.put(IMPORT_CANDIDATES_TAG, candidateTags);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.hasUUID(ENDPOINT_ID_TAG)) {
            endpointId = EndpointId.of(tag.getUUID(ENDPOINT_ID_TAG));
        }
        synchronized (this) {
            logName = tag.contains(LOG_NAME_TAG, Tag.TAG_STRING)
                    ? sanitizeDisplayName(tag.getString(LOG_NAME_TAG), "synaxis_log")
                    : logName;
            rowIdMode = LogOutputRowIdMode.parse(tag.getString(ROW_ID_MODE_TAG));
            writeMode = LogOutputWriteMode.parse(tag.getString(WRITE_MODE_TAG));
            createMode = LogOutputCreateMode.parse(tag.getString(CREATE_MODE_TAG));
            createColumnId = tag.getString(CREATE_COLUMN_ID_TAG);
            skipEmptyRows = !tag.contains(SKIP_EMPTY_ROWS_TAG, Tag.TAG_BYTE) || tag.getBoolean(SKIP_EMPTY_ROWS_TAG);
            if (tag.contains(CUSTOM_OUTPUT_FILE_TAG, Tag.TAG_STRING)) {
                String storedOutputFile = tag.getString(CUSTOM_OUTPUT_FILE_TAG);
                customOutputFile = storedOutputFile.isBlank() ? "" : safeOutputFileName(storedOutputFile);
            } else {
                customOutputFile = "";
            }
            tableHasData = tag.getBoolean(TABLE_HAS_DATA_TAG);
            logStarted = tag.getBoolean(LOG_STARTED_TAG);
            intervalTicks = Math.max(1, tag.getInt(INTERVAL_TICKS_TAG));
            rowIndex = tag.getLong(ROW_INDEX_TAG);
            lastWriteTick = tag.getLong(LAST_WRITE_TICK_TAG);
            lastWrittenHeaderSignature = tag.getString(HEADER_SIGNATURE_TAG);
            status = tag.contains(STATUS_TAG, Tag.TAG_STRING) ? tag.getString(STATUS_TAG) : status;
            columns.clear();
            ListTag columnTags = tag.getList(COLUMNS_TAG, Tag.TAG_COMPOUND);
            for (Tag columnTag : columnTags) {
                if (columnTag instanceof CompoundTag compoundTag) {
                    LogOutputColumn.fromTag(compoundTag).ifPresent(column -> columns.put(column.id(), column));
                }
            }
            importCandidates.clear();
            ListTag candidateTags = tag.getList(IMPORT_CANDIDATES_TAG, Tag.TAG_STRING);
            for (Tag candidateTag : candidateTags) {
                if (candidateTag instanceof StringTag stringTag) {
                    String candidate = stringTag.getAsString();
                    if (!candidate.isBlank()) {
                        importCandidates.add(safeOutputFileName(candidate));
                    }
                }
            }
            if (!columns.containsKey(createColumnId)) {
                createColumnId = "";
                if (createMode == LogOutputCreateMode.SPECIFIC_INPUT) {
                    createMode = LogOutputCreateMode.ANY_INPUT;
                }
            }
        }
        endpointDirty = true;
    }

    private void writeIfNeeded(ServerLevel serverLevel) {
        List<LogOutputColumn> snapshot;
        LogOutputWriteMode mode;
        LogOutputRowIdMode rowMode;
        int interval;
        boolean skipEmpty;
        long tick = serverLevel.getGameTime();
        synchronized (this) {
            if (columns.isEmpty()) {
                return;
            }
            if (!logStarted) {
                logStarted = shouldStartLog();
                if (!logStarted) {
                    return;
                }
                valuesDirty = true;
            }
            boolean shouldWrite = switch (writeMode) {
                case EVERY_TICK -> true;
                case ON_CHANGE -> valuesDirty;
                case INTERVAL -> lastWriteTick == Long.MIN_VALUE || tick - lastWriteTick >= intervalTicks;
            };
            if (!shouldWrite) {
                return;
            }
            snapshot = List.copyOf(columns.values());
            mode = writeMode;
            rowMode = rowIdMode;
            interval = intervalTicks;
            skipEmpty = skipEmptyRows;
            if (skipEmpty && isEmptySnapshot(snapshot)) {
                valuesDirty = false;
                if (!Objects.equals(status, "Waiting for input; empty row skipped")) {
                    status = "Waiting for input; empty row skipped";
                    setChanged();
                    syncToClient();
                }
                return;
            }
        }
        try {
            Path outputFile = claimOutputFileForWrite(serverLevel);
            writeRow(outputFile, snapshot, rowMode, tick);
            synchronized (this) {
                lastWriteTick = tick;
                valuesDirty = false;
                status = "Wrote " + outputFile.getFileName() + " at tick " + tick
                        + (mode == LogOutputWriteMode.INTERVAL ? " every " + interval + " ticks" : "");
                setChanged();
                syncToClient();
            }
        } catch (IOException exception) {
            synchronized (this) {
                status = "Log write failed: " + exception.getClass().getSimpleName();
                syncToClient();
            }
            CCSConnector.LOGGER.warn("Failed to write log output assistant file", exception);
        }
    }

    private boolean shouldStartLog() {
        return switch (createMode) {
            case IMMEDIATE -> true;
            case ANY_INPUT -> columns.values().stream().anyMatch(LogOutputColumn::activated);
            case SPECIFIC_INPUT -> {
                LogOutputColumn column = columns.get(createColumnId);
                yield column != null && column.activated();
            }
        };
    }

    private Path claimOutputFileForWrite(ServerLevel serverLevel) throws IOException {
        String desired;
        boolean avoidExisting;
        synchronized (this) {
            desired = customOutputFile.isBlank() ? outputFileName(logName) : customOutputFile;
            avoidExisting = customOutputFile.isBlank();
        }
        Path outputDir = outputDirectory(serverLevel);
        Path file = claimOutputFile(outputDir, desired, avoidExisting);
        synchronized (this) {
            customOutputFile = file.getFileName().toString();
            logName = fileStem(customOutputFile);
            setChanged();
        }
        return file;
    }

    private String importCsvContent(ServerLevel serverLevel, String fileName, String content, boolean sourceInOutputFolder)
            throws IOException {
        CsvImport csvImport = parseCsvImport(content);
        Path outputDir = outputDirectory(serverLevel);
        Path source = safeResolveOutputFile(outputDir, fileName);
        boolean duplicateSource = !sourceInOutputFolder || isClaimedByAnotherBlock(source);
        Path target = claimOutputFile(outputDir, fileName, duplicateSource);
        if (!sourceInOutputFolder || !target.equals(source)) {
            Files.writeString(
                    target,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        }

        synchronized (this) {
            columns.clear();
            rowIdMode = csvImport.rowIdMode();
            for (int i = 0; i < csvImport.columns().size(); i++) {
                String displayName = uniqueColumnName(sanitizeDisplayName(csvImport.columns().get(i), "column_" + (i + 1)), null);
                String portName = uniquePortName(sanitizeSignalName(displayName), null);
                String id = uniqueColumnId();
                columns.put(id, new LogOutputColumn(
                        id,
                        displayName,
                        portName,
                        csvImport.types().get(i),
                        null,
                        false,
                        -1L));
            }
            customOutputFile = target.getFileName().toString();
            logName = fileStem(customOutputFile);
            tableHasData = csvImport.hasData();
            rowIndex = csvImport.dataRows();
            logStarted = tableHasData;
            valuesDirty = false;
            lastWriteTick = Long.MIN_VALUE;
            lastWrittenHeaderSignature = headerSignature(List.copyOf(columns.values()), rowIdMode);
            endpointDirty = true;
            status = "Imported " + customOutputFile + " with " + columns.size() + " columns"
                    + (tableHasData ? "; locked" : "; editable");
            setChanged();
            syncToClient();
            return status;
        }
    }

    private void writeRow(Path file, List<LogOutputColumn> snapshot, LogOutputRowIdMode rowMode, long tick) throws IOException {
        String header = headerLine(snapshot, rowMode);
        String headerSignature = headerSignature(snapshot, rowMode);
        boolean needsHeader = !Files.exists(file) || Files.size(file) == 0L;
        boolean rewriteEmptyTable;
        synchronized (this) {
            needsHeader = needsHeader || !Objects.equals(lastWrittenHeaderSignature, headerSignature);
            rewriteEmptyTable = needsHeader && !tableHasData && Files.exists(file);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                rewriteEmptyTable ? StandardOpenOption.TRUNCATE_EXISTING : StandardOpenOption.APPEND)) {
            if (needsHeader) {
                writer.write("# ccsconnector log output assistant ");
                writer.write(Instant.now().toString());
                writer.newLine();
                writer.write(header);
                writer.newLine();
            }
            writer.write(rowLine(snapshot, rowMode, tick));
            writer.newLine();
        }
        synchronized (this) {
            lastWrittenHeaderSignature = headerSignature;
            rowIndex++;
            tableHasData = true;
        }
    }

    private String headerLine(List<LogOutputColumn> snapshot, LogOutputRowIdMode rowMode) {
        List<String> fields = new ArrayList<>();
        if (rowMode == LogOutputRowIdMode.TICK) {
            fields.add("tick");
        } else if (rowMode == LogOutputRowIdMode.ROW_INDEX) {
            fields.add("row");
        }
        snapshot.forEach(column -> fields.add(column.name()));
        return csvLine(fields);
    }

    private String rowLine(List<LogOutputColumn> snapshot, LogOutputRowIdMode rowMode, long tick) {
        List<String> fields = new ArrayList<>();
        if (rowMode == LogOutputRowIdMode.TICK) {
            fields.add(String.valueOf(tick));
        } else if (rowMode == LogOutputRowIdMode.ROW_INDEX) {
            synchronized (this) {
                fields.add(String.valueOf(rowIndex));
            }
        }
        snapshot.forEach(column -> fields.add(!column.activated() || column.value() == null
                ? ""
                : String.valueOf(column.value())));
        return csvLine(fields);
    }

    private static boolean isEmptySnapshot(List<LogOutputColumn> snapshot) {
        return snapshot.stream().noneMatch(LogOutputColumn::activated);
    }

    private String headerSignature(List<LogOutputColumn> snapshot, LogOutputRowIdMode rowMode) {
        StringBuilder builder = new StringBuilder(rowMode.serializedName());
        snapshot.forEach(column -> builder.append('|').append(column.name()).append(':').append(column.type().serializedName()));
        return builder.toString();
    }

    private Path outputDirectory(ServerLevel serverLevel) throws IOException {
        Path outputDir = serverLevel.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve(OUTPUT_DIRECTORY_NAME)
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(outputDir);
        return outputDir;
    }

    private Path claimOutputFile(Path outputDir, String desiredFileName, boolean avoidExisting) throws IOException {
        Path dir = outputDir.toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String safeName = safeOutputFileName(desiredFileName);
        Path candidate = safeResolveOutputFile(dir, safeName);
        int index = 2;
        boolean requireFreshFile = avoidExisting;
        while (true) {
            boolean claimedByAnother = isClaimedByAnotherBlock(candidate);
            boolean existingConflict = requireFreshFile && Files.exists(candidate) && !Objects.equals(claimedOutputFile, candidate);
            if (!claimedByAnother && !existingConflict) {
                UUID owner = endpointId.value();
                synchronized (CLAIMED_OUTPUT_FILES) {
                    if (claimedOutputFile != null && !Objects.equals(claimedOutputFile, candidate)) {
                        UUID currentOwner = CLAIMED_OUTPUT_FILES.get(claimedOutputFile);
                        if (Objects.equals(currentOwner, owner)) {
                            CLAIMED_OUTPUT_FILES.remove(claimedOutputFile);
                        }
                    }
                    CLAIMED_OUTPUT_FILES.put(candidate, owner);
                }
                synchronized (this) {
                    claimedOutputFile = candidate;
                }
                return candidate;
            }
            if (claimedByAnother) {
                requireFreshFile = true;
            }
            candidate = safeResolveOutputFile(dir, numberedFileName(safeName, index));
            index++;
        }
    }

    private boolean isClaimedByAnotherBlock(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        synchronized (CLAIMED_OUTPUT_FILES) {
            UUID owner = CLAIMED_OUTPUT_FILES.get(normalized);
            return owner != null && !Objects.equals(owner, endpointId.value());
        }
    }

    private void releaseOutputFileClaim() {
        Path file;
        synchronized (this) {
            file = claimedOutputFile;
            claimedOutputFile = null;
        }
        if (file == null) {
            return;
        }
        synchronized (CLAIMED_OUTPUT_FILES) {
            UUID owner = CLAIMED_OUTPUT_FILES.get(file);
            if (Objects.equals(owner, endpointId.value())) {
                CLAIMED_OUTPUT_FILES.remove(file);
            }
        }
    }

    private static Path safeResolveOutputFile(Path outputDir, String fileName) {
        Path dir = outputDir.toAbsolutePath().normalize();
        Path file = dir.resolve(safeOutputFileName(fileName)).normalize();
        if (!file.startsWith(dir)) {
            throw new IllegalArgumentException("Output file escapes output directory");
        }
        return file;
    }

    private static String safeOutputFileName(String fileName) {
        String sanitized = sanitizeFileName(fileName);
        if (!sanitized.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            sanitized = sanitized + ".csv";
        }
        return sanitized;
    }

    private static String numberedFileName(String fileName, int index) {
        int dot = fileName.toLowerCase(Locale.ROOT).endsWith(".csv") ? fileName.length() - 4 : -1;
        String base = dot <= 0 ? fileName : fileName.substring(0, dot);
        String ext = dot <= 0 ? "" : fileName.substring(dot);
        return base + "_" + index + ext;
    }

    private static String fileStem(String fileName) {
        String safe = safeOutputFileName(fileName);
        return safe.toLowerCase(Locale.ROOT).endsWith(".csv") ? safe.substring(0, safe.length() - 4) : safe;
    }

    private synchronized Map<String, String> portLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        columns.values().forEach(column -> labels.put(column.portName(), column.name()
                + " [in] "
                + column.type().serializedName()));
        return labels;
    }

    private synchronized LogOutputColumn columnByPort(String portName) {
        return columns.values().stream()
                .filter(column -> Objects.equals(column.portName(), portName))
                .findFirst()
                .orElse(null);
    }

    private synchronized String uniqueColumnId() {
        int index = columns.size() + 1;
        String id = "column_" + index;
        while (columns.containsKey(id)) {
            index++;
            id = "column_" + index;
        }
        return id;
    }

    private synchronized String uniqueColumnName(String base, String currentId) {
        String name = base;
        int index = 2;
        while (columnNameExists(name, currentId)) {
            name = base + "_" + index;
            index++;
        }
        return name;
    }

    private synchronized String uniquePortName(String base, String currentId) {
        String name = base;
        int index = 2;
        while (portNameExists(name, currentId)) {
            name = base + "_" + index;
            index++;
        }
        return name;
    }

    private synchronized boolean columnNameExists(String name, String currentId) {
        return columns.values().stream()
                .anyMatch(column -> !Objects.equals(column.id(), currentId) && Objects.equals(column.name(), name));
    }

    private synchronized boolean portNameExists(String name, String currentId) {
        return columns.values().stream()
                .anyMatch(column -> !Objects.equals(column.id(), currentId) && Objects.equals(column.portName(), name));
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

    private void unregisterEndpoint() {
        if (!endpointRegistered || !(level instanceof ServerLevel serverLevel)) {
            endpointRegistered = false;
            return;
        }
        CimulinkWorldRuntimes.forLevel(serverLevel).unregisterEndpoint(endpointId);
        endpointRegistered = false;
    }

    private static int parseInterval(String text, int fallback) {
        try {
            return Math.max(1, Math.min(72000, Integer.parseInt(text.trim())));
        } catch (NumberFormatException exception) {
            return Math.max(1, fallback);
        }
    }

    private static CsvImport parseCsvImport(String content) {
        String text = content == null ? "" : content;
        List<String> lines = text.lines().toList();
        int headerLineIndex = -1;
        List<String> header = List.of();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            headerLineIndex = i;
            header = parseCsvLine(line);
            break;
        }
        if (headerLineIndex < 0 || header.isEmpty()) {
            throw new IllegalArgumentException("CSV has no header");
        }

        LogOutputRowIdMode rowMode = LogOutputRowIdMode.NONE;
        int columnStart = 0;
        String firstHeader = header.getFirst().trim();
        if ("tick".equalsIgnoreCase(firstHeader)) {
            rowMode = LogOutputRowIdMode.TICK;
            columnStart = 1;
        } else if ("row".equalsIgnoreCase(firstHeader) || "row_index".equalsIgnoreCase(firstHeader)) {
            rowMode = LogOutputRowIdMode.ROW_INDEX;
            columnStart = 1;
        }

        List<String> columns = new ArrayList<>();
        for (int i = columnStart; i < header.size(); i++) {
            String name = sanitizeDisplayName(header.get(i), "column_" + (columns.size() + 1));
            columns.add(name);
        }
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("CSV has no data columns");
        }
        if (columns.size() > MAX_COLUMNS) {
            throw new IllegalArgumentException("CSV has more than 10 columns");
        }

        boolean[] sawValue = new boolean[columns.size()];
        boolean[] boolCandidate = new boolean[columns.size()];
        for (int i = 0; i < boolCandidate.length; i++) {
            boolCandidate[i] = true;
        }
        long dataRows = 0L;
        for (int lineIndex = headerLineIndex + 1; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex);
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            dataRows++;
            List<String> fields = parseCsvLine(line);
            for (int column = 0; column < columns.size(); column++) {
                int fieldIndex = columnStart + column;
                if (fieldIndex >= fields.size()) {
                    continue;
                }
                String value = fields.get(fieldIndex).trim();
                if (value.isEmpty()) {
                    continue;
                }
                sawValue[column] = true;
                String lower = value.toLowerCase(Locale.ROOT);
                if (!"true".equals(lower) && !"false".equals(lower)) {
                    boolCandidate[column] = false;
                }
            }
        }

        List<BridgeSignalType> types = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            types.add(sawValue[i] && boolCandidate[i] ? BridgeSignalType.BOOLEAN : BridgeSignalType.REAL);
        }
        return new CsvImport(columns, types, rowMode, dataRows > 0L, dataRows);
    }

    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    private record CsvImport(
            List<String> columns,
            List<BridgeSignalType> types,
            LogOutputRowIdMode rowIdMode,
            boolean hasData,
            long dataRows) {
    }

    private static String outputFileName(String logName) {
        return safeOutputFileName(logName);
    }

    private static String sanitizeDisplayName(String raw, String fallback) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            return fallback;
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private static String sanitizeSignalName(String raw) {
        StringBuilder builder = new StringBuilder();
        String value = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        if (builder.isEmpty() || Character.isDigit(builder.charAt(0))) {
            builder.insert(0, "column_");
        }
        return builder.toString();
    }

    private static String sanitizeFileName(String raw) {
        String value = sanitizeDisplayName(raw, "synaxis_log");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_'
                    || c == '-'
                    || c == '.') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        String result = builder.toString();
        while (result.startsWith(".")) {
            result = result.substring(1);
        }
        return result.isBlank() ? "synaxis_log" : result;
    }

    private static String csvLine(List<String> fields) {
        return fields.stream().map(LogOutputAssistantBlockEntity::csvEscape).reduce((a, b) -> a + "," + b).orElse("");
    }

    private static String csvEscape(String value) {
        String text = value == null ? "" : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
