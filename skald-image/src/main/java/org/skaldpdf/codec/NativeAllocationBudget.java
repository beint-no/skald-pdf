package org.skaldpdf.codec;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;

/** Bounded JxlMemoryManager. No exception may escape a callback into C. */
final class NativeAllocationBudget implements AutoCloseable {
    private final long limit;
    private long used;
    private final Map<Long, Allocation> allocations = new HashMap<>();
    private final MemorySegment manager;

    NativeAllocationBudget(Arena scope, long limit) throws ReflectiveOperationException {
        this.limit = limit;
        var lookup = MethodHandles.lookup();
        var alloc =
                lookup.findVirtual(
                                NativeAllocationBudget.class,
                                "allocate",
                                MethodType.methodType(
                                        MemorySegment.class, MemorySegment.class, long.class))
                        .bindTo(this);
        var free =
                lookup.findVirtual(
                                NativeAllocationBudget.class,
                                "free",
                                MethodType.methodType(
                                        void.class, MemorySegment.class, MemorySegment.class))
                        .bindTo(this);
        var linker = Linker.nativeLinker();
        manager = scope.allocate(24, 8);
        manager.set(
                ValueLayout.ADDRESS,
                8,
                linker.upcallStub(
                        alloc,
                        FunctionDescriptor.of(
                                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
                        scope));
        manager.set(
                ValueLayout.ADDRESS,
                16,
                linker.upcallStub(
                        free,
                        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                        scope));
    }

    MemorySegment manager() {
        return manager;
    }

    MemorySegment allocate(MemorySegment opaque, long size) {
        try {
            var charged = Math.max(16, size);
            if (size < 0 || charged > limit - used) return MemorySegment.NULL;
            var arena = Arena.ofConfined();
            try {
                var memory = arena.allocate(charged, 16);
                allocations.put(memory.address(), new Allocation(arena, charged));
                used += charged;
                return memory;
            } catch (Throwable failure) {
                arena.close();
                return MemorySegment.NULL;
            }
        } catch (Throwable failure) {
            return MemorySegment.NULL;
        }
    }

    void free(MemorySegment opaque, MemorySegment address) {
        try {
            var allocation = allocations.get(address.address());
            if (allocation != null) {
                allocation.arena().close();
                allocations.remove(address.address());
                used -= allocation.size();
            }
        } catch (Throwable failure) {
            /* close() still releases retained allocations. */
        }
    }

    @Override
    public void close() {
        allocations.values().forEach(allocation -> allocation.arena().close());
        allocations.clear();
    }

    private record Allocation(Arena arena, long size) {}
}
