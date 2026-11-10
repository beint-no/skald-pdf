package org.skaldpdf.codec;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfWriter;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpegXlFixtureTest {
    @Test
    void requiresNativeCodecWhenRequested() {
        if ("1".equals(System.getenv("SKALD_REQUIRE_JXL_TESTS"))) {
            assertTrue(NativeImages.jpegXlAvailable(), "CI requires libjxl for the fixture tests");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"rgb-raw.jxl", "rgb-boxed.jxl"})
    @EnabledIf("jxlPresent")
    void decodesExactSamplesAndRejectsTruncation(String name) throws Exception {
        var input = fixture(name);
        assertTrue(NativeImages.isJpegXl(input));
        var raster = NativeImages.jpegXlDecode(input);
        assertEquals(4, raster.width());
        assertEquals(4, raster.height());
        assertArrayEquals(expectedRgb(), raster.rgb());
        for (var length : new int[]{0, 1, input.length / 2}) {
            var truncated = Arrays.copyOf(input, length);
            assertThrows(RuntimeException.class, () -> NativeImages.jpegXlDecode(truncated));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"rgb-raw.jxl", "rgb-boxed.jxl"})
    @EnabledIf("jxlPresent")
    void preparesJpegAndRendersStandardPdf(String name) throws Exception {
        var prepared = NativeImages.prepare(fixture(name), new PrepareOptions(32, 95));
        assertTrue(prepared.jpeg());
        var expected = ImageIO.read(new ByteArrayInputStream(prepared.samples()));
        assertEquals(4, expected.getWidth());
        assertEquals(4, expected.getHeight());
        var output = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfWriter(output))) {
            prepared.drawOn(document, document.addNewPage(new PageSize(4, 4)), 0, 0, 4, 4);
        }
        try (var document = Loader.loadPDF(output.toByteArray())) {
            var page = document.getPage(0);
            var image = (PDImageXObject) page.getResources().getXObject(
                page.getResources().getXObjectNames().iterator().next());
            assertEquals(COSName.DCT_DECODE, image.getCOSObject().getDictionaryObject(COSName.FILTER));
            var rendered = new PDFRenderer(document).renderImage(0, 1, ImageType.RGB);
            assertArrayEquals(expected.getRGB(0, 0, 4, 4, null, 0, 4),
                rendered.getRGB(0, 0, 4, 4, null, 0, 4));
        }
    }

    static boolean jxlPresent() {
        return NativeImages.jpegXlAvailable();
    }

    private static byte[] fixture(String name) throws Exception {
        try (var input = Objects.requireNonNull(JpegXlFixtureTest.class.getResourceAsStream(
            "/codec/jpeg-xl/" + name))) {
            return input.readAllBytes();
        }
    }

    private static byte[] expectedRgb() {
        var colors = new int[][]{{255, 0, 0}, {0, 255, 0}, {0, 0, 255}, {255, 255, 0}};
        var pixels = new byte[4 * 4 * 3];
        var offset = 0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                for (var channel : colors[(x + y) % colors.length]) {
                    pixels[offset++] = (byte) channel;
                }
            }
        }
        return pixels;
    }
}
