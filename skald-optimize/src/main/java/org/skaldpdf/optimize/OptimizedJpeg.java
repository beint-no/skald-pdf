package org.skaldpdf.optimize;

import org.skaldpdf.image.ImageData;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Private APP15 marker that prevents repeated lossy recompression. */
final class OptimizedJpeg {
    private static final byte[] SIGNATURE = "SkaldPDF\0".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1;
    private static final int PAYLOAD_BYTES = SIGNATURE.length + 1 + Integer.BYTES + 1;

    private OptimizedJpeg() {
    }

    static boolean alreadySatisfies(byte[] jpeg, OptimizeOptions options, int width, int height,
                                    boolean requiresOriginalDimensions) {
        var marker = marker(jpeg);
        if (marker == null || !requiresOriginalDimensions && Math.max(width, height) > options.maxEdge()) {
            return false;
        }
        return marker.fingerprint() == options.markerFingerprint()
            || marker.quality() <= Math.round(options.jpegQuality() * 100);
    }

    static ImageData mark(ImageData jpeg, OptimizeOptions options, float quality) {
        var bytes = jpeg.samples();
        if (bytes.length < 2 || bytes[0] != (byte) 0xff || bytes[1] != (byte) 0xd8) {
            throw new IllegalArgumentException("Image recompressor did not return a JPEG bitstream");
        }
        var existing = markerSegmentLength(bytes);
        var offset = existing == 0 ? 2 : 2 + existing;
        var payload = ByteBuffer.allocate(PAYLOAD_BYTES);
        payload.put(SIGNATURE).put((byte) VERSION).putInt(options.markerFingerprint())
            .put((byte) Math.round(quality * 100));
        var segmentLength = PAYLOAD_BYTES + 2;
        var output = new byte[bytes.length - (offset - 2) + PAYLOAD_BYTES + 4];
        output[0] = (byte) 0xff;
        output[1] = (byte) 0xd8;
        output[2] = (byte) 0xff;
        output[3] = (byte) 0xef;
        output[4] = (byte) (segmentLength >>> 8);
        output[5] = (byte) segmentLength;
        System.arraycopy(payload.array(), 0, output, 6, PAYLOAD_BYTES);
        System.arraycopy(bytes, offset, output, 6 + PAYLOAD_BYTES, bytes.length - offset);
        return ImageData.fromJpeg(output);
    }

    private static Marker marker(byte[] jpeg) {
        if (markerSegmentLength(jpeg) == 0) {
            return null;
        }
        var payload = ByteBuffer.wrap(jpeg, 6, PAYLOAD_BYTES);
        var signature = new byte[SIGNATURE.length];
        payload.get(signature);
        if (!Arrays.equals(signature, SIGNATURE) || Byte.toUnsignedInt(payload.get()) != VERSION) {
            return null;
        }
        return new Marker(payload.getInt(), Byte.toUnsignedInt(payload.get()));
    }

    private static int markerSegmentLength(byte[] jpeg) {
        if (jpeg.length < 6 + PAYLOAD_BYTES || jpeg[0] != (byte) 0xff || jpeg[1] != (byte) 0xd8
            || jpeg[2] != (byte) 0xff || jpeg[3] != (byte) 0xef) {
            return 0;
        }
        var length = (jpeg[4] & 0xff) << 8 | jpeg[5] & 0xff;
        if (length != PAYLOAD_BYTES + 2 || 2 + length + 2 > jpeg.length) {
            return 0;
        }
        for (int index = 0; index < SIGNATURE.length; index++) {
            if (jpeg[6 + index] != SIGNATURE[index]) {
                return 0;
            }
        }
        return length + 2;
    }

    private record Marker(int fingerprint, int quality) {
    }
}
