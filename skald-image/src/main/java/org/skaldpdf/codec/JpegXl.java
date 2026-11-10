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

/** Bounded FFM decoding of JPEG XL; PDF decoding uses the restricted static RGB8/Gray8 path. */
final class JpegXl {
    private static final int SUCCESS = 0;
    private static final int ERROR = 1;
    private static final int NEED_MORE_INPUT = 2;
    private static final int NEED_IMAGE_OUT_BUFFER = 5;
    private static final int BASIC_INFO = 0x40;
    private static final int FULL_IMAGE = 0x1000;
    private static final int TYPE_UINT8 = 2;

    private static final GroupLayout PIXEL_FORMAT = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("num_channels"),
        ValueLayout.JAVA_INT.withName("data_type"),
        ValueLayout.JAVA_INT.withName("endianness"),
        MemoryLayout.paddingLayout(4),
        ValueLayout.JAVA_LONG.withName("align")
    ).withName("JxlPixelFormat");

    private static final java.util.Optional<SymbolLookup> LIBRARY = NativeLibraries.jxl();
    private static final MethodHandle CREATE;
    private static final MethodHandle DESTROY;
    private static final MethodHandle SUBSCRIBE;
    private static final MethodHandle SET_INPUT;
    private static final MethodHandle CLOSE_INPUT;
    private static final MethodHandle PROCESS;
    private static final MethodHandle BASIC;
    private static final MethodHandle BUFFER_SIZE;
    private static final MethodHandle SET_BUFFER;
    static final boolean AVAILABLE;

    static {
        var lookup = LIBRARY;
        if (lookup.isEmpty()) {
            CREATE = DESTROY = SUBSCRIBE = SET_INPUT = CLOSE_INPUT = PROCESS = BASIC = BUFFER_SIZE = SET_BUFFER = null;
            AVAILABLE = false;
        } else {
            var lib = lookup.get();
            CREATE = downcall(lib, "JxlDecoderCreate", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            DESTROY = downcall(lib, "JxlDecoderDestroy", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            SUBSCRIBE = downcall(lib, "JxlDecoderSubscribeEvents",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            SET_INPUT = downcall(lib, "JxlDecoderSetInput",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            CLOSE_INPUT = downcall(lib, "JxlDecoderCloseInput", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            PROCESS = downcall(lib, "JxlDecoderProcessInput",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            BASIC = downcall(lib, "JxlDecoderGetBasicInfo",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            BUFFER_SIZE = downcall(lib, "JxlDecoderImageOutBufferSize",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            SET_BUFFER = downcall(lib, "JxlDecoderSetImageOutBuffer",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            AVAILABLE = true;
        }
    }

    private JpegXl() {
    }

    static boolean pdfAvailable() {
        if (!AVAILABLE) return false;
        try {
            var lib = LIBRARY.orElseThrow();
            var version = downcall(lib, "JxlDecoderVersion", FunctionDescriptor.of(ValueLayout.JAVA_INT));
            return (int) version.invokeExact() >= 11000;
        } catch (Throwable unavailable) { return false; }
    }

    static Raster decode(byte[] jxl) {
        var image = decodeImage(jxl, false);
        return new Raster(image.width(), image.height(), image.samples());
    }

    static org.skaldpdf.image.ImageData decodePdf(byte[] jxl) {
        return decodeImage(jxl, true);
    }

    private static org.skaldpdf.image.ImageData decodeImage(byte[] jxl, boolean pdf) {
        requireAvailable();
        if (pdf && !pdfAvailable()) throw new IllegalStateException("Experimental JPEG XL PDF support requires libjxl 0.11+");
        Objects.requireNonNull(jxl, "jxl");
        if (jxl.length < 2) {
            throw new IllegalArgumentException("JPEG XL buffer is too small");
        }
        try (var arena = Arena.ofConfined(); var memory = new NativeAllocationBudget(arena, (pdf ? 512L : 1024L) * 1024 * 1024)) {
            var decoder = (MemorySegment) CREATE.invokeExact(memory.manager());
            if (decoder.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("JxlDecoderCreate failed");
            }
            try {
                check((int) SUBSCRIBE.invokeExact(decoder, BASIC_INFO | FULL_IMAGE));
                var input = arena.allocateFrom(ValueLayout.JAVA_BYTE, jxl);
                check((int) SET_INPUT.invokeExact(decoder, input, (long) jxl.length));
                CLOSE_INPUT.invokeExact(decoder);

                var format = arena.allocate(PIXEL_FORMAT);
                format.set(ValueLayout.JAVA_INT, PIXEL_FORMAT.byteOffset(
                    MemoryLayout.PathElement.groupElement("num_channels")), 3);
                format.set(ValueLayout.JAVA_INT, PIXEL_FORMAT.byteOffset(
                    MemoryLayout.PathElement.groupElement("data_type")), TYPE_UINT8);
                format.set(ValueLayout.JAVA_INT, PIXEL_FORMAT.byteOffset(
                    MemoryLayout.PathElement.groupElement("endianness")), 0);
                format.set(ValueLayout.JAVA_LONG, PIXEL_FORMAT.byteOffset(
                    MemoryLayout.PathElement.groupElement("align")), 0L);

                var info = arena.allocate(256);
                MemorySegment pixels = null;
                var width = 0;
                var height = 0;
                var channels = 3;
                var sizePtr = arena.allocate(ValueLayout.JAVA_LONG);

                while (true) {
                    var status = (int) PROCESS.invokeExact(decoder);
                    if (status == SUCCESS) {
                        break;
                    }
                    if (status == ERROR) {
                        throw new IllegalStateException("libjxl rejected the JPEG XL codestream");
                    }
                    if (status == NEED_MORE_INPUT) {
                        throw new IllegalArgumentException("Truncated JPEG XL input");
                    }
                    if (status == BASIC_INFO) {
                        check((int) BASIC.invokeExact(decoder, info));
                        width = info.get(ValueLayout.JAVA_INT, 4);
                        height = info.get(ValueLayout.JAVA_INT, 8);
                        if (pdf) {
                            channels = info.get(ValueLayout.JAVA_INT, 52);
                            if ((long) width * height > 20_000_000L || info.get(ValueLayout.JAVA_INT, 48) != 1
                                || info.get(ValueLayout.JAVA_INT, 12) != 8 || info.get(ValueLayout.JAVA_INT, 16) != 0
                                || info.get(ValueLayout.JAVA_INT, 44) != 0 || info.get(ValueLayout.JAVA_INT, 56) != 0
                                || channels != 1 && channels != 3) {
                                throw new IllegalArgumentException("Experimental PDF JPEG XL requires static opaque RGB8/Gray8");
                            }
                            format.set(ValueLayout.JAVA_INT, 0, channels);
                        }
                        if (width < 1 || height < 1) {
                            throw new IllegalStateException("JPEG XL image has no dimensions");
                        }
                        if ((long) width * height > 100_000_000L) {
                            throw new IllegalArgumentException("JPEG XL image exceeds the safe pixel limit");
                        }
                    } else if (status == NEED_IMAGE_OUT_BUFFER) {
                        if (width < 1 || height < 1) {
                            throw new IllegalStateException("JPEG XL requested pixels before basic info");
                        }
                        check((int) BUFFER_SIZE.invokeExact(decoder, format, sizePtr));
                        var size = sizePtr.get(ValueLayout.JAVA_LONG, 0);
                        var expected = (long) width * height * channels;
                        if (size < expected) {
                            throw new IllegalStateException("JPEG XL output buffer is smaller than RGB");
                        }
                        pixels = arena.allocate(size);
                        check((int) SET_BUFFER.invokeExact(decoder, format, pixels, size));
                    }
                }
                if (pixels == null || width < 1 || height < 1) {
                    throw new IllegalStateException("JPEG XL decode produced no pixels");
                }
                if (pdf) {
                    var profile = arena.allocate(256, 8);
                    var lib = LIBRARY.orElseThrow();
                    var color = downcall(lib, "JxlDecoderGetColorAsEncodedProfile", FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
                    for (int target = 0;
                            target <= 1;
                            target++) { // original and decoded pixel profiles
                        check((int) color.invokeExact(decoder, target, profile));
                        if (profile.get(ValueLayout.JAVA_INT, 0) != (channels == 1 ? 1 : 0)
                                || profile.get(ValueLayout.JAVA_INT, 4) != 1
                                || channels == 3 && profile.get(ValueLayout.JAVA_INT, 24) != 1
                                || profile.get(ValueLayout.JAVA_INT, 80) != 13) {
                            throw new IllegalArgumentException(
                                    "Experimental PDF JPEG XL requires sRGB/D65 pixel encoding");
                        }
                    }
                }
                var expected = Math.multiplyExact(Math.multiplyExact(width, height), channels);
                var samples = pixels.asSlice(0, expected).toArray(ValueLayout.JAVA_BYTE);
                return channels == 1 ? org.skaldpdf.image.ImageData.fromGray(width, height, samples)
                    : org.skaldpdf.image.ImageData.fromRgb(width, height, samples);
            } finally {
                DESTROY.invokeExact(decoder);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new IllegalStateException("libjxl decode failed", exception);
        }
    }

    private static void requireAvailable() {
        if (!AVAILABLE) {
            throw new IllegalStateException("libjxl is not available");
        }
    }

    private static void check(int status) {
        if (status != SUCCESS) {
            throw new IllegalStateException("libjxl status " + status);
        }
    }

    private static MethodHandle downcall(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        var symbol = lookup.find(name)
            .orElseThrow(() -> new IllegalStateException("Missing libjxl symbol " + name));
        return Linker.nativeLinker().downcallHandle(symbol, descriptor);
    }
}
