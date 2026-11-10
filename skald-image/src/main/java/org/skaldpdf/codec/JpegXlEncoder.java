package org.skaldpdf.codec;

import org.skaldpdf.image.ImageData;
import org.skaldpdf.image.ImageEncoding;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** Reference libjxl encoding; no CLI or JNI dependency. */
final class JpegXlEncoder {
    private static final int MAXIMUM_ENCODED_BYTES = 32 * 1024 * 1024;

    private static final class Library {
        private static final java.util.Optional<SymbolLookup> VALUE = NativeLibraries.jxl();
    }

    private JpegXlEncoder() {}

    static ImageData encode(ImageData image, JpegXlOptions options) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(options, "options");
        if (!NativeImages.jpegXlEncodingAvailable())
            throw new IllegalStateException("JPEG XL encoding requires libjxl 0.11+");
        if (image.alpha() != null || image.jpegXl()) {
            throw new IllegalArgumentException(
                    "Encode opaque raster/JPEG input; keep PDF alpha as a separate soft mask");
        }
        if ((long) image.width() * image.height() > 20_000_000L) {
            throw new IllegalArgumentException("JPEG XL encoding exceeds the 20 MP limit");
        }
        if (image.jpeg() && embeddedIcc(image.samples())) {
            throw new IllegalArgumentException(
                    "Experimental JPEG XL PDF encoding preserves JPEGs with embedded ICC profiles"
                            + " unchanged");
        }
        var jpeg = image.jpeg() && options.distance() == 0;
        if (image.jpeg() && !jpeg) {
            image = RasterImages.decodeSamples(image);
        }
        if (!jpeg && image.encoding() != ImageEncoding.RAW) {
            throw new IllegalArgumentException("Expected raster samples");
        }
        var lib =
                Library.VALUE.orElseThrow(() -> new IllegalStateException("libjxl is unavailable"));
        var create =
                call(
                        lib,
                        "JxlEncoderCreate",
                        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        var destroy =
                call(lib, "JxlEncoderDestroy", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        var settingsCreate =
                call(
                        lib,
                        "JxlEncoderFrameSettingsCreate",
                        FunctionDescriptor.of(
                                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        var effort =
                call(
                        lib,
                        "JxlEncoderFrameSettingsSetOption",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_LONG));
        var lossless =
                call(
                        lib,
                        "JxlEncoderSetFrameLossless",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        var distance =
                call(
                        lib,
                        "JxlEncoderSetFrameDistance",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_FLOAT));
        var addImage =
                call(
                        lib,
                        jpeg ? "JxlEncoderAddJPEGFrame" : "JxlEncoderAddImageFrame",
                        jpeg
                                ? FunctionDescriptor.of(
                                        ValueLayout.JAVA_INT,
                                        ValueLayout.ADDRESS,
                                        ValueLayout.ADDRESS,
                                        ValueLayout.JAVA_LONG)
                                : FunctionDescriptor.of(
                                        ValueLayout.JAVA_INT,
                                        ValueLayout.ADDRESS,
                                        ValueLayout.ADDRESS,
                                        ValueLayout.ADDRESS,
                                        ValueLayout.JAVA_LONG));
        var process =
                call(
                        lib,
                        "JxlEncoderProcessOutput",
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));
        var close =
                call(lib, "JxlEncoderCloseInput", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        byte[] encoded;
        try (var arena = Arena.ofConfined();
                var memory = new NativeAllocationBudget(arena, 512L * 1024 * 1024)) {
            var encoder = (MemorySegment) create.invokeExact(memory.manager());
            if (encoder.equals(MemorySegment.NULL))
                throw new IllegalStateException("Cannot allocate JPEG XL encoder");
            try {
                var settings =
                        (MemorySegment) settingsCreate.invokeExact(encoder, MemorySegment.NULL);
                if (settings.equals(MemorySegment.NULL))
                    throw new IllegalStateException("Cannot allocate JPEG XL settings");
                check((int) effort.invokeExact(settings, 0, (long) options.effort()));
                if (!jpeg) {
                    var initialize =
                            call(
                                    lib,
                                    "JxlEncoderInitBasicInfo",
                                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
                    var basic =
                            call(
                                    lib,
                                    "JxlEncoderSetBasicInfo",
                                    FunctionDescriptor.of(
                                            ValueLayout.JAVA_INT,
                                            ValueLayout.ADDRESS,
                                            ValueLayout.ADDRESS));
                    var srgb =
                            call(
                                    lib,
                                    "JxlColorEncodingSetToSRGB",
                                    FunctionDescriptor.ofVoid(
                                            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
                    var color =
                            call(
                                    lib,
                                    "JxlEncoderSetColorEncoding",
                                    FunctionDescriptor.of(
                                            ValueLayout.JAVA_INT,
                                            ValueLayout.ADDRESS,
                                            ValueLayout.ADDRESS));
                    var info = arena.allocate(256, 8);
                    initialize.invokeExact(info);
                    info.set(ValueLayout.JAVA_INT, 4, image.width());
                    info.set(ValueLayout.JAVA_INT, 8, image.height());
                    info.set(ValueLayout.JAVA_INT, 12, 8);
                    info.set(ValueLayout.JAVA_INT, 36, options.distance() == 0 ? 1 : 0);
                    info.set(ValueLayout.JAVA_INT, 52, image.components());
                    check((int) basic.invokeExact(encoder, info));
                    var encoding = arena.allocate(256, 8);
                    srgb.invokeExact(encoding, image.components() == 1 ? 1 : 0);
                    check((int) color.invokeExact(encoder, encoding));
                    check((int) distance.invokeExact(settings, options.distance()));
                    check((int) lossless.invokeExact(settings, options.distance() == 0 ? 1 : 0));
                } else {
                    // Preserve JPEG reconstruction metadata in the container so djxl can
                    // reconstruct the exact original JPEG without another lossy generation.
                    var metadata =
                            call(
                                    lib,
                                    "JxlEncoderStoreJPEGMetadata",
                                    FunctionDescriptor.of(
                                            ValueLayout.JAVA_INT,
                                            ValueLayout.ADDRESS,
                                            ValueLayout.JAVA_INT));
                    check((int) metadata.invokeExact(encoder, 1));
                }
                var samples = image.samples();
                var input = arena.allocateFrom(ValueLayout.JAVA_BYTE, samples);
                if (jpeg) {
                    check((int) addImage.invokeExact(settings, input, (long) samples.length));
                } else {
                    var format = arena.allocate(24, 8);
                    format.set(ValueLayout.JAVA_INT, 0, image.components());
                    format.set(ValueLayout.JAVA_INT, 4, 2); // JXL_TYPE_UINT8
                    check(
                            (int)
                                    addImage.invokeExact(
                                            settings, format, input, (long) samples.length));
                }
                close.invokeExact(encoder);
                var buffer = arena.allocate(8192);
                var next = arena.allocate(ValueLayout.ADDRESS);
                var available = arena.allocate(ValueLayout.JAVA_LONG);
                var output = new ByteArrayOutputStream();
                while (true) {
                    next.set(ValueLayout.ADDRESS, 0, buffer);
                    available.set(ValueLayout.JAVA_LONG, 0, 8192L);
                    var result = (int) process.invokeExact(encoder, next, available);
                    var written = 8192 - Math.toIntExact(available.get(ValueLayout.JAVA_LONG, 0));
                    if (written > MAXIMUM_ENCODED_BYTES - output.size()) {
                        throw new IllegalArgumentException("JPEG XL encoded-size limit exceeded");
                    }
                    output.writeBytes(buffer.asSlice(0, written).toArray(ValueLayout.JAVA_BYTE));
                    if (result == 0) break;
                    if (result != 2 || written == 0)
                        throw new IllegalStateException("JPEG XL encoding failed");
                }
                encoded = output.toByteArray();
            } finally {
                destroy.invokeExact(encoder);
            }
        } catch (IllegalArgumentException | IllegalStateException failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Native JPEG XL encoding failed", failure);
        }
        return ImageData.fromExperimentalJpegXl(encoded);
    }

    private static boolean embeddedIcc(byte[] jpeg) {
        var prefix = "ICC_PROFILE\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        for (int position = 2; position + 3 < jpeg.length; ) {
            if ((jpeg[position++] & 255) != 255) return true;
            while (position < jpeg.length && (jpeg[position] & 255) == 255) position++;
            if (position >= jpeg.length) return true;
            var marker = jpeg[position++] & 255;
            if (marker == 0xda || marker == 0xd9) return false;
            if (marker == 0x01 || marker >= 0xd0 && marker <= 0xd8) continue;
            if (position + 1 >= jpeg.length) return true;
            var length = (jpeg[position] & 255) * 256 + (jpeg[position + 1] & 255);
            if (length < 2 || length > jpeg.length - position) return true;
            if (marker == 0xe2 && length >= prefix.length + 2) {
                boolean matches = true;
                for (int i = 0; i < prefix.length; i++)
                    matches &= jpeg[position + 2 + i] == prefix[i];
                if (matches) return true;
            }
            position += length;
        }
        return false;
    }

    private static void check(int status) {
        if (status != 0) throw new IllegalStateException("libjxl encoder status " + status);
    }

    private static MethodHandle call(SymbolLookup lib, String name, FunctionDescriptor descriptor) {
        return Linker.nativeLinker()
                .downcallHandle(
                        lib.find(name)
                                .orElseThrow(() ->
                                        new IllegalStateException("Missing libjxl symbol " + name)),
                        descriptor);
    }
}
