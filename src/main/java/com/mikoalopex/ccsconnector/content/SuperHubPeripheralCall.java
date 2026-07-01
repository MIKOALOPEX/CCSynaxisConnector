package com.mikoalopex.ccsconnector.content;

import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaTask;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.WorkMonitor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

final class SuperHubPeripheralCall {
    static final IArguments EMPTY_ARGUMENTS = new EmptyArguments();
    private static final DirectLuaContext DIRECT_CONTEXT_IMPL = new DirectLuaContext();
    static final ILuaContext DIRECT_CONTEXT = DIRECT_CONTEXT_IMPL;
    static final WorkMonitor WORK_MONITOR = new AlwaysWorkMonitor();

    private SuperHubPeripheralCall() {
    }

    static IComputerAccess computerAccess(String attachmentName, Map<String, IPeripheral> peripherals) {
        return new HubComputerAccess(attachmentName, peripherals);
    }

    static IArguments arguments(Object... values) {
        if (values == null || values.length == 0) {
            return EMPTY_ARGUMENTS;
        }
        return new ArrayArguments(values, 0);
    }

    static MethodResult resolveCompletedTask(MethodResult result) throws LuaException {
        if (result.getCallback() == null) {
            return result;
        }
        Object[] yielded = result.getResult();
        if (yielded == null || yielded.length == 0 || !"task_complete".equals(yielded[0])) {
            return result;
        }
        return result.getCallback().resume(new Object[]{
                "task_complete",
                DIRECT_CONTEXT_IMPL.lastTaskId(),
                true
        });
    }

    private static final class EmptyArguments implements IArguments {
        @Override
        public int count() {
            return 0;
        }

        @Override
        public Object get(int index) throws LuaException {
            throw new LuaException("Missing argument #" + (index + 1));
        }

        @Override
        public String getType(int index) {
            return "nil";
        }

        @Override
        public IArguments drop(int count) {
            return this;
        }
    }

    private record ArrayArguments(Object[] values, int offset) implements IArguments {
        @Override
        public int count() {
            return Math.max(0, values.length - offset);
        }

        @Override
        public Object get(int index) throws LuaException {
            int actualIndex = offset + index;
            if (index < 0 || actualIndex >= values.length) {
                throw new LuaException("Missing argument #" + (index + 1));
            }
            return values[actualIndex];
        }

        @Override
        public String getType(int index) {
            int actualIndex = offset + index;
            if (index < 0 || actualIndex >= values.length || values[actualIndex] == null) {
                return "nil";
            }
            Object value = values[actualIndex];
            if (value instanceof Number) {
                return "number";
            }
            if (value instanceof Boolean) {
                return "boolean";
            }
            if (value instanceof String) {
                return "string";
            }
            if (value instanceof Map<?, ?>) {
                return "table";
            }
            return value.getClass().getSimpleName();
        }

        @Override
        public IArguments drop(int count) {
            if (count <= 0) {
                return this;
            }
            int nextOffset = Math.min(values.length, offset + count);
            return nextOffset >= values.length ? EMPTY_ARGUMENTS : new ArrayArguments(values, nextOffset);
        }
    }

    private static final class DirectLuaContext implements ILuaContext {
        private long taskId;

        @Override
        public long issueMainThreadTask(LuaTask task) throws LuaException {
            task.execute();
            return ++taskId;
        }

        private long lastTaskId() {
            return taskId;
        }

        @Override
        public MethodResult executeMainThreadTask(LuaTask task) throws LuaException {
            return MethodResult.of(task.execute());
        }
    }

    private static final class HubComputerAccess implements IComputerAccess {
        private final String attachmentName;
        private final Map<String, IPeripheral> peripherals;

        private HubComputerAccess(String attachmentName, Map<String, IPeripheral> peripherals) {
            this.attachmentName = attachmentName;
            this.peripherals = peripherals;
        }

        @Override
        public String mount(String desiredLocation, Mount mount, String driveName) {
            return null;
        }

        @Override
        public String mountWritable(String desiredLocation, WritableMount mount, String driveName) {
            return null;
        }

        @Override
        public void unmount(String location) {
        }

        @Override
        public int getID() {
            return -1;
        }

        @Override
        public void queueEvent(String event, Object... arguments) {
        }

        @Override
        public String getAttachmentName() {
            return attachmentName;
        }

        @Override
        public Map<String, IPeripheral> getAvailablePeripherals() {
            return peripherals;
        }

        @Override
        public IPeripheral getAvailablePeripheral(String name) {
            return peripherals.get(name);
        }

        @Override
        public WorkMonitor getMainThreadMonitor() {
            return WORK_MONITOR;
        }
    }

    private static final class AlwaysWorkMonitor implements WorkMonitor {
        @Override
        public boolean canWork() {
            return true;
        }

        @Override
        public boolean shouldWork() {
            return true;
        }

        @Override
        public void trackWork(long time, TimeUnit unit) {
        }
    }
}
