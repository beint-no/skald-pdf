package org.skaldpdf.brotli;

import org.skaldpdf.pdf.spi.BrotliCodec;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Service implementation backed by the reference libbrotli C API on 64-bit systems. */
public final class NativeBrotliCodec implements BrotliCodec {
    private static final int MAXIMUM_BYTES = 128 * 1024 * 1024;
    private static final int CHUNK = 8192;

    /** Creates the service; native libraries are loaded lazily. */
    public NativeBrotliCodec() {
    }

    private static final class Decoder {
        private static final Optional<SymbolLookup> LIBRARY = library("dec");
    }

    private static final class Encoder {
        private static final Optional<SymbolLookup> LIBRARY = library("enc");
    }

    @Override
    public boolean canDecode() {
        return Decoder.LIBRARY.isPresent();
    }

    @Override
    public boolean canEncode() {
        return Encoder.LIBRARY.isPresent();
    }

    @Override
    public byte[] decode(byte[] encoded, int maximumBytes) {
        return decode(encoded, maximumBytes, true);
    }

    @Override
    public void validate(byte[] encoded, int maximumBytes) {
        decode(encoded, maximumBytes, false);
    }

    private byte[] decode(byte[] encoded, int maximumBytes, boolean collect) {
        Objects.requireNonNull(encoded, "encoded");
        if (maximumBytes < 0 || maximumBytes > MAXIMUM_BYTES) {
            throw new IllegalArgumentException("Brotli decoded limit must be between 0 and 128 MiB");
        }
        var lib = Decoder.LIBRARY.orElseThrow(() -> new IllegalStateException("libbrotlidec is unavailable"));
        var create = call(lib, "BrotliDecoderCreateInstance", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        var destroy = call(lib, "BrotliDecoderDestroyInstance", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        var parameter = call(lib, "BrotliDecoderSetParameter", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        var process = call(lib, "BrotliDecoderDecompressStream", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        // Bound decoder-owned native allocations as well as decoded Java output. Large-window
        // headers must not be able to reserve an unbounded ring buffer before producing bytes.
        try (var arena = Arena.ofConfined(); var budget = new MemoryBudget((long) maximumBytes + 32 * 1024 * 1024)) {
            var linker = Linker.nativeLinker();
            var allocate = MethodHandles.lookup().findVirtual(MemoryBudget.class, "allocate",
                MethodType.methodType(MemorySegment.class, MemorySegment.class, long.class)).bindTo(budget);
            var free = MethodHandles.lookup().findVirtual(MemoryBudget.class, "free",
                MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class)).bindTo(budget);
            var allocator = linker.upcallStub(allocate, FunctionDescriptor.of(ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, ValueLayout.JAVA_LONG), arena);
            var deallocator = linker.upcallStub(free, FunctionDescriptor.ofVoid(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS), arena);
            var state = (MemorySegment) create.invokeExact(allocator, deallocator, MemorySegment.NULL);
            if (state.equals(MemorySegment.NULL)) {
                throw new IllegalArgumentException("Unable to allocate bounded Brotli decoder");
            }
            try {
                // BROTLI_DECODER_PARAM_LARGE_WINDOW = 1. PDF EXTN-BROTLI-1 v1.3 requires it.
                if ((int) parameter.invokeExact(state, 1, 1) == 0) {
                    throw new IllegalStateException("libbrotlidec does not support large-window Brotli");
                }
                var input = arena.allocateFrom(ValueLayout.JAVA_BYTE, encoded);
                var availableIn = arena.allocate(ValueLayout.JAVA_LONG);
                availableIn.set(ValueLayout.JAVA_LONG, 0, (long) encoded.length);
                var nextIn = arena.allocate(ValueLayout.ADDRESS);
                nextIn.set(ValueLayout.ADDRESS, 0, input);
                var buffer = arena.allocate(CHUNK);
                var availableOut = arena.allocate(ValueLayout.JAVA_LONG);
                var nextOut = arena.allocate(ValueLayout.ADDRESS);
                var output = new ByteArrayOutputStream(collect ? Math.min(encoded.length, CHUNK) : 0);
                var total = 0;
                while (true) {
                    var capacity = Math.min(CHUNK, maximumBytes - total + 1);
                    availableOut.set(ValueLayout.JAVA_LONG, 0, (long) capacity);
                    nextOut.set(ValueLayout.ADDRESS, 0, buffer);
                    var previousIn = availableIn.get(ValueLayout.JAVA_LONG, 0);
                    var result = (int) process.invokeExact(state, availableIn, nextIn, availableOut,
                        nextOut, MemorySegment.NULL);
                    var produced = capacity - Math.toIntExact(availableOut.get(ValueLayout.JAVA_LONG, 0));
                    total = Math.addExact(total, produced);
                    if (total > maximumBytes) {
                        throw new IllegalArgumentException("Brotli stream exceeds the decoded size limit");
                    }
                    if (collect && produced > 0) {
                        output.writeBytes(buffer.asSlice(0, produced).toArray(ValueLayout.JAVA_BYTE));
                    }
                    if (result == 1) {
                        if (availableIn.get(ValueLayout.JAVA_LONG, 0) != 0) {
                            throw new IllegalArgumentException("Brotli stream has trailing data");
                        }
                        return output.toByteArray();
                    }
                    if (result == 0) {
                        throw new IllegalArgumentException("Invalid Brotli stream or native memory limit exceeded");
                    }
                    if (result == 2) {
                        throw new IllegalArgumentException("Truncated Brotli stream");
                    }
                    if (result != 3 || produced == 0 && previousIn == availableIn.get(ValueLayout.JAVA_LONG, 0)) {
                        throw new IllegalArgumentException("Brotli decoder made no progress");
                    }
                }
            } finally {
                destroy.invokeExact(state);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new IllegalStateException("Native Brotli decoding failed", exception);
        }
    }

    @Override
    public byte[] encode(byte[] decoded, int quality) {
        Objects.requireNonNull(decoded, "decoded");
        if (quality < 0 || quality > 11 || decoded.length > MAXIMUM_BYTES) {
            throw new IllegalArgumentException("Brotli encoding requires quality 0..11 and at most 128 MiB");
        }
        var lib = Encoder.LIBRARY.orElseThrow(() -> new IllegalStateException("libbrotlienc is unavailable"));
        var size = call(lib, "BrotliEncoderMaxCompressedSize",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        var compress = call(lib, "BrotliEncoderCompress", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        try (var arena = Arena.ofConfined()) {
            var capacity = (long) size.invokeExact((long) decoded.length);
            if (capacity <= 0 || capacity > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Brotli encoded size is too large");
            }
            var input = arena.allocateFrom(ValueLayout.JAVA_BYTE, decoded);
            var output = arena.allocate(capacity);
            var written = arena.allocate(ValueLayout.JAVA_LONG);
            written.set(ValueLayout.JAVA_LONG, 0, capacity);
            // Standard-window output maximizes interoperability; decoding also accepts large windows.
            if ((int) compress.invokeExact(quality, 22, 0, (long) decoded.length, input, written, output) == 0) {
                throw new IllegalStateException("Brotli compression failed");
            }
            return output.asSlice(0, written.get(ValueLayout.JAVA_LONG, 0)).toArray(ValueLayout.JAVA_BYTE);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new IllegalStateException("Native Brotli encoding failed", exception);
        }
    }

    private static MethodHandle call(SymbolLookup lib, String name, FunctionDescriptor descriptor) {
        return Linker.nativeLinker().downcallHandle(lib.find(name).orElseThrow(
            () -> new IllegalStateException("Missing libbrotli symbol " + name)), descriptor);
    }

    private static Optional<SymbolLookup> library(String suffix) {
        if (ValueLayout.ADDRESS.byteSize() != Long.BYTES) {
            return Optional.empty();
        }
        var name = "brotli" + suffix;
        var override = System.getenv("SKALD_BROTLI_" + suffix.toUpperCase(java.util.Locale.ROOT));
        var paths = override == null || override.isBlank() ? List.of(
            "/opt/homebrew/opt/brotli/lib/lib" + name + ".dylib",
            "/usr/local/lib/lib" + name + ".dylib",
            "/usr/lib/x86_64-linux-gnu/lib" + name + ".so.1",
            "/usr/lib/aarch64-linux-gnu/lib" + name + ".so.1",
            "/usr/lib64/lib" + name + ".so.1",
            "/usr/lib/lib" + name + ".so.1") : List.of(override);
        try {
            for (var candidate : paths) {
                if (Files.isRegularFile(Path.of(candidate))) {
                    return Optional.of(SymbolLookup.libraryLookup(Path.of(candidate), Arena.global()));
                }
            }
            if (override == null || override.isBlank()) {
                return Optional.of(SymbolLookup.libraryLookup(System.mapLibraryName(name), Arena.global()));
            }
        } catch (IllegalArgumentException | IllegalCallerException | UnsatisfiedLinkError unavailable) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /** No exception may escape an upcall into C. All allocations are explicitly freed. */
    private static final class MemoryBudget implements AutoCloseable {
        private final long maximum;
        private long used;
        private final Map<Long, Allocation> allocations = new HashMap<>();

        MemoryBudget(long maximum) {
            this.maximum = maximum;
        }

        MemorySegment allocate(MemorySegment ignored, long size) {
            try {
                if (size < 0 || size > maximum - used) {
                    return MemorySegment.NULL;
                }
                var arena = Arena.ofConfined();
                try {
                    var segment = arena.allocate(Math.max(1, size), 16);
                    allocations.put(segment.address(), new Allocation(arena, size));
                    used += size;
                    return segment;
                } catch (Throwable failure) {
                    arena.close();
                    return MemorySegment.NULL;
                }
            } catch (Throwable failure) {
                return MemorySegment.NULL;
            }
        }

        void free(MemorySegment ignored, MemorySegment address) {
            try {
                var allocation = allocations.remove(address.address());
                if (allocation != null) {
                    used -= allocation.size();
                    allocation.arena().close();
                }
            } catch (Throwable failure) {
                // close() below still releases any allocations retained in the map.
            }
        }

        @Override
        public void close() {
            allocations.values().forEach(allocation -> allocation.arena().close());
            allocations.clear();
        }

        private record Allocation(Arena arena, long size) {
        }
    }
}
