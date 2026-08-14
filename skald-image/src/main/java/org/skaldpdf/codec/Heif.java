package org.skaldpdf.codec;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** Thin FFM binding to libheif decode (HEIC / AVIF). */
final class Heif {
    private static final int COLORSPACE_RGB = 1;
    private static final int CHROMA_INTERLEAVED_RGB = 10;
    private static final int CHANNEL_INTERLEAVED = 10;

    private static final GroupLayout ERROR = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("code"),
        ValueLayout.JAVA_INT.withName("subcode"),
        ValueLayout.ADDRESS.withName("message")
    ).withName("heif_error");

    private static final Linker LINKER = Linker.nativeLinker();
    private static final MethodHandle ALLOC;
    private static final MethodHandle FREE;
    private static final MethodHandle READ;
    private static final MethodHandle PRIMARY;
    private static final MethodHandle HANDLE_WIDTH;
    private static final MethodHandle HANDLE_HEIGHT;
    private static final MethodHandle HANDLE_RELEASE;
    private static final MethodHandle DECODE;
    private static final MethodHandle PLANE;
    private static final MethodHandle IMAGE_HEIGHT;
    private static final MethodHandle IMAGE_RELEASE;
    static final boolean AVAILABLE;

    static {
        var lookup = NativeLibraries.heif();
        if (lookup.isEmpty()) {
            ALLOC = FREE = READ = PRIMARY = HANDLE_WIDTH = HANDLE_HEIGHT = HANDLE_RELEASE =
                DECODE = PLANE = IMAGE_HEIGHT = IMAGE_RELEASE = null;
            AVAILABLE = false;
        } else {
            var lib = lookup.get();
            ALLOC = downcall(lib, "heif_context_alloc", FunctionDescriptor.of(ValueLayout.ADDRESS));
            FREE = downcall(lib, "heif_context_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            READ = downcall(lib, "heif_context_read_from_memory_without_copy",
                FunctionDescriptor.of(ERROR, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));
            PRIMARY = downcall(lib, "heif_context_get_primary_image_handle",
                FunctionDescriptor.of(ERROR, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            HANDLE_WIDTH = downcall(lib, "heif_image_handle_get_width",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            HANDLE_HEIGHT = downcall(lib, "heif_image_handle_get_height",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            HANDLE_RELEASE = downcall(lib, "heif_image_handle_release",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            DECODE = downcall(lib, "heif_decode_image",
                FunctionDescriptor.of(ERROR, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            PLANE = downcall(lib, "heif_image_get_plane_readonly",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
            IMAGE_HEIGHT = downcall(lib, "heif_image_get_height",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            IMAGE_RELEASE = downcall(lib, "heif_image_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            AVAILABLE = true;
        }
    }

    private Heif() {
    }

    static Raster decode(byte[] heif) {
        requireAvailable();
        Objects.requireNonNull(heif, "heif");
        if (heif.length < 12) {
            throw new IllegalArgumentException("HEIF buffer is too small");
        }
        try (var arena = Arena.ofConfined()) {
            var context = (MemorySegment) ALLOC.invokeExact();
            if (context.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("heif_context_alloc failed");
            }
            var handlePtr = arena.allocate(ValueLayout.ADDRESS);
            var imagePtr = arena.allocate(ValueLayout.ADDRESS);
            try {
                var data = arena.allocateFrom(ValueLayout.JAVA_BYTE, heif);
                check(READ.invoke(arena, context, data, (long) heif.length, MemorySegment.NULL));
                check(PRIMARY.invoke(arena, context, handlePtr));
                var handle = handlePtr.get(ValueLayout.ADDRESS, 0);
                var width = (int) HANDLE_WIDTH.invokeExact(handle);
                var height = (int) HANDLE_HEIGHT.invokeExact(handle);
                if (width < 1 || height < 1) {
                    throw new IllegalStateException("HEIF image has no dimensions");
                }
                if ((long) width * height > 100_000_000L) {
                    throw new IllegalArgumentException("HEIF image exceeds the safe pixel limit");
                }
                check(DECODE.invoke(arena, handle, imagePtr, COLORSPACE_RGB, CHROMA_INTERLEAVED_RGB,
                    MemorySegment.NULL));
                var image = imagePtr.get(ValueLayout.ADDRESS, 0);
                var stridePtr = arena.allocate(ValueLayout.JAVA_INT);
                var plane = (MemorySegment) PLANE.invokeExact(image, CHANNEL_INTERLEAVED, stridePtr);
                if (plane.equals(MemorySegment.NULL)) {
                    throw new IllegalStateException("HEIF interleaved plane is missing");
                }
                var stride = stridePtr.get(ValueLayout.JAVA_INT, 0);
                var rows = (int) IMAGE_HEIGHT.invokeExact(image, CHANNEL_INTERLEAVED);
                var rgb = packedRgb(plane.reinterpret((long) stride * Math.max(rows, height)), width, height, stride);
                return new Raster(width, height, rgb);
            } finally {
                var image = imagePtr.get(ValueLayout.ADDRESS, 0);
                if (!image.equals(MemorySegment.NULL)) {
                    IMAGE_RELEASE.invokeExact(image);
                }
                var handle = handlePtr.get(ValueLayout.ADDRESS, 0);
                if (!handle.equals(MemorySegment.NULL)) {
                    HANDLE_RELEASE.invokeExact(handle);
                }
                FREE.invokeExact(context);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new IllegalStateException("libheif decode failed", exception);
        }
    }

    private static byte[] packedRgb(MemorySegment plane, int width, int height, int stride) {
        var rgb = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 3)];
        var rowBytes = width * 3;
        if (stride == rowBytes) {
            MemorySegment.copy(plane, ValueLayout.JAVA_BYTE, 0, rgb, 0, rgb.length);
            return rgb;
        }
        for (int row = 0; row < height; row++) {
            MemorySegment.copy(plane, ValueLayout.JAVA_BYTE, (long) row * stride, rgb, row * rowBytes, rowBytes);
        }
        return rgb;
    }

    private static void requireAvailable() {
        if (!AVAILABLE) {
            throw new IllegalStateException("libheif is not available");
        }
    }

    private static void check(Object returned) {
        var error = (MemorySegment) returned;
        var code = error.get(ValueLayout.JAVA_INT, 0);
        if (code != 0) {
            var message = error.get(ValueLayout.ADDRESS, ERROR.byteOffset(MemoryLayout.PathElement.groupElement("message")));
            var text = message.equals(MemorySegment.NULL) ? "libheif error " + code
                : message.reinterpret(256).getString(0);
            throw new IllegalStateException(text);
        }
    }

    private static MethodHandle downcall(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        var symbol = lookup.find(name)
            .orElseThrow(() -> new IllegalStateException("Missing libheif symbol " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }
}
