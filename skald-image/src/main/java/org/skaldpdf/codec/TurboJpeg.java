package org.skaldpdf.codec;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** Thin FFM binding to TurboJPEG 3. */
final class TurboJpeg {
    private static final int TJINIT_COMPRESS = 0;
    private static final int TJINIT_DECOMPRESS = 1;
    private static final int TJPF_RGB = 0;
    private static final int TJSAMP_420 = 2;
    private static final int TJPARAM_NOREALLOC = 2;
    private static final int TJPARAM_QUALITY = 3;
    private static final int TJPARAM_SUBSAMP = 4;
    private static final int TJPARAM_JPEGWIDTH = 5;
    private static final int TJPARAM_JPEGHEIGHT = 6;
    private static final int TURBOJPEG_VERSION = 3_002_000;

    private static final Linker LINKER = Linker.nativeLinker();
    private static final MethodHandle INIT;
    private static final MethodHandle DESTROY;
    private static final MethodHandle SET;
    private static final MethodHandle GET;
    private static final MethodHandle ERROR;
    private static final MethodHandle JPEG_BUF_SIZE;
    private static final MethodHandle COMPRESS;
    private static final MethodHandle DECOMPRESS_HEADER;
    private static final MethodHandle DECOMPRESS;
    static final boolean AVAILABLE;

    static {
        var lookup = NativeLibraries.turboJpeg();
        if (lookup.isEmpty()) {
            INIT = DESTROY = SET = GET = ERROR = JPEG_BUF_SIZE = COMPRESS = DECOMPRESS_HEADER = DECOMPRESS = null;
            AVAILABLE = false;
        } else {
            var lib = lookup.get();
            INIT = downcall(lib, "tj3InitVersion",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            DESTROY = downcall(lib, "tj3Destroy", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            SET = downcall(lib, "tj3Set",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            GET = downcall(lib, "tj3Get",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            ERROR = downcall(lib, "tj3GetErrorStr", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            JPEG_BUF_SIZE = downcall(lib, "tj3JPEGBufSize",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            COMPRESS = downcall(lib, "tj3Compress8", FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            DECOMPRESS_HEADER = downcall(lib, "tj3DecompressHeader",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            DECOMPRESS = downcall(lib, "tj3Decompress8", FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            AVAILABLE = true;
        }
    }

    private TurboJpeg() {
    }

    static byte[] compress(Raster raster, int quality) {
        requireAvailable();
        Objects.requireNonNull(raster, "raster");
        if (quality < 1 || quality > 100) {
            throw new IllegalArgumentException("JPEG quality must be 1-100");
        }
        try (var arena = Arena.ofConfined()) {
            var handle = (MemorySegment) INIT.invokeExact(TJINIT_COMPRESS, TURBOJPEG_VERSION);
            if (handle.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("tj3InitVersion failed");
            }
            try {
                check((int) SET.invokeExact(handle, TJPARAM_NOREALLOC, 1), handle);
                check((int) SET.invokeExact(handle, TJPARAM_QUALITY, quality), handle);
                check((int) SET.invokeExact(handle, TJPARAM_SUBSAMP, TJSAMP_420), handle);
                var capacity = (long) JPEG_BUF_SIZE.invokeExact(raster.width(), raster.height(), TJSAMP_420);
                var jpeg = arena.allocate(capacity);
                var jpegPtr = arena.allocate(ValueLayout.ADDRESS);
                jpegPtr.set(ValueLayout.ADDRESS, 0, jpeg);
                var sizePtr = arena.allocate(ValueLayout.JAVA_LONG);
                sizePtr.set(ValueLayout.JAVA_LONG, 0, capacity);
                var src = arena.allocateFrom(ValueLayout.JAVA_BYTE, raster.rgb());
                check((int) COMPRESS.invokeExact(handle, src, raster.width(), 0, raster.height(), TJPF_RGB, jpegPtr, sizePtr),
                    handle);
                var size = sizePtr.get(ValueLayout.JAVA_LONG, 0);
                return jpeg.asSlice(0, size).toArray(ValueLayout.JAVA_BYTE);
            } finally {
                DESTROY.invokeExact(handle);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new IllegalStateException("TurboJPEG compress failed", exception);
        }
    }

    static Raster decompress(byte[] jpeg) {
        requireAvailable();
        Objects.requireNonNull(jpeg, "jpeg");
        try (var arena = Arena.ofConfined()) {
            var handle = (MemorySegment) INIT.invokeExact(TJINIT_DECOMPRESS, TURBOJPEG_VERSION);
            if (handle.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("tj3InitVersion failed");
            }
            try {
                var src = arena.allocateFrom(ValueLayout.JAVA_BYTE, jpeg);
                check((int) DECOMPRESS_HEADER.invokeExact(handle, src, (long) jpeg.length), handle);
                var width = (int) GET.invokeExact(handle, TJPARAM_JPEGWIDTH);
                var height = (int) GET.invokeExact(handle, TJPARAM_JPEGHEIGHT);
                if (width < 1 || height < 1) {
                    throw new IllegalStateException("TurboJPEG header has no dimensions");
                }
                var rgb = arena.allocate((long) width * height * 3);
                check((int) DECOMPRESS.invokeExact(handle, src, (long) jpeg.length, rgb, 0, TJPF_RGB), handle);
                return new Raster(width, height, rgb.toArray(ValueLayout.JAVA_BYTE));
            } finally {
                DESTROY.invokeExact(handle);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new IllegalStateException("TurboJPEG decompress failed", exception);
        }
    }

    private static void requireAvailable() {
        if (!AVAILABLE) {
            throw new IllegalStateException("libturbojpeg is not available");
        }
    }

    private static void check(int result, MemorySegment handle) throws Throwable {
        if (result != 0) {
            var message = (MemorySegment) ERROR.invokeExact(handle);
            throw new IllegalStateException(message.reinterpret(256).getString(0));
        }
    }

    private static MethodHandle downcall(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        var symbol = lookup.find(name)
            .orElseThrow(() -> new IllegalStateException("Missing TurboJPEG symbol " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }
}
