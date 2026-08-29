package org.skaldpdf.optimize;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.color.PDICCBased;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.skaldpdf.codec.RasterImages;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.image.ImageData;
import org.skaldpdf.pdf.CanonicalRewriteConstraint;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfReader;
import org.skaldpdf.pdf.PdfWriter;
import org.skaldpdf.pdf.SignatureField;

import javax.imageio.ImageIO;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfOptimizerSafetyTest {
    private static final OptimizeOptions TEST_OPTIONS = OptimizeOptions.builder()
        .maxEdge(320)
        .jpegQuality(0.72f)
        .losslessQuality(0.82f)
        .minimumLosslessBytes(0)
        .minimumSavingsBytes(1)
        .minimumSavingsPercent(0)
        .build();

    @Test
    void isIdempotentAfterTheFirstCanonicalRewrite() throws Exception {
        var source = pdfWith(RasterImages.decode(noisyPng(900, 700)));
        var once = PdfOptimizer.recompress(source, TEST_OPTIONS);
        var twice = PdfOptimizer.recompress(once, TEST_OPTIONS);

        assertTrue(once.length < source.length);
        assertArrayEquals(once, twice);
    }

    @Test
    void returnsTheOriginalArrayForMalformedOrProtectedDocuments() {
        var malformed = "%PDF-not-a-document".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertSame(malformed, PdfOptimizer.recompress(malformed, TEST_OPTIONS));

        var signed = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfWriter(signed))) {
            document.addNewPage(PageSize.A4);
            document.prepareSignature(SignatureField.invisible("PendingSeal"));
        }
        var signedBytes = signed.toByteArray();
        assertSame(signedBytes, PdfOptimizer.recompress(signedBytes, TEST_OPTIONS));
    }

    @Test
    void leavesDocumentsWithTrailingFlateDataByteForByteUntouched() throws Exception {
        var source = pdfWithTrailingFlateContent();
        try (var document = new PdfDocument(new PdfReader(source))) {
            assertTrue(document.canonicalRewriteConstraints()
                .contains(CanonicalRewriteConstraint.MALFORMED_STREAM));
        }

        assertSame(source, PdfOptimizer.recompress(source, TEST_OPTIONS));
    }

    @Test
    void leavesDocumentsWithTruncatedOrChecksumInvalidFlateByteForByteUntouched() throws Exception {
        var valid = flate("q Q\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        var truncated = java.util.Arrays.copyOf(valid, valid.length - 1);
        var checksumInvalid = valid.clone();
        checksumInvalid[checksumInvalid.length - 1] ^= 1;

        for (var encoded : java.util.List.of(truncated, checksumInvalid)) {
            var source = pdfWithRawFlateContent(encoded);
            try (var document = new PdfDocument(new PdfReader(source))) {
                assertTrue(document.canonicalRewriteConstraints()
                    .contains(CanonicalRewriteConstraint.MALFORMED_STREAM));
            }
            assertSame(source, PdfOptimizer.recompress(source, TEST_OPTIONS));
        }
    }

    @Test
    void leavesEmptyStreamsThatDeclareAFlateFilterByteForByteUntouched() throws Exception {
        var source = pdfWithRawFlateContent(new byte[0]);
        try (var document = new PdfDocument(new PdfReader(source))) {
            assertTrue(document.canonicalRewriteConstraints()
                .contains(CanonicalRewriteConstraint.MALFORMED_STREAM));
        }
        assertSame(source, PdfOptimizer.recompress(source, TEST_OPTIONS));
    }

    @Test
    void leavesDeclaredConformanceProfilesByteForByteUntouched() throws Exception {
        var source = pdfWith(RasterImages.decode(noisyPng(640, 480)));
        byte[] archival;
        try (var document = Loader.loadPDF(source)) {
            var metadata = new PDMetadata(document);
            metadata.importXMPMetadata(("""
                <?xpacket begin="\ufeff"?>
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/"
                      pdfaid:part="2" pdfaid:conformance="B"/>
                  </rdf:RDF>
                </x:xmpmeta>
                <?xpacket end="w"?>
                """).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            document.getDocumentCatalog().setMetadata(metadata);
            var output = new ByteArrayOutputStream();
            document.save(output);
            archival = output.toByteArray();
        }
        assertSame(archival, PdfOptimizer.recompress(archival, TEST_OPTIONS));
    }

    @Test
    void neverHandsUnsafeImageSemanticsToACustomEncoder() throws Exception {
        var source = pdfWith(RasterImages.decode(noisyPng(640, 480)));
        var protectedPdf = withDecodeArray(source);
        var calls = new AtomicInteger();

        var result = PdfOptimizer.recompress(protectedPdf, TEST_OPTIONS, (image, options) -> {
            calls.incrementAndGet();
            return image.decode();
        });

        assertEquals(0, calls.get());
        assertImagePayloadsEqual(protectedPdf, result);
    }

    @Test
    void acceptsOnlyIdentityImageDecodeArrays() throws Exception {
        var source = pdfWith(RasterImages.decode(noisyPng(640, 480)));
        var identity = withDecodeArray(source, false);
        var calls = new AtomicInteger();

        var result = PdfOptimizer.recompress(identity, TEST_OPTIONS, (image, options) -> {
            calls.incrementAndGet();
            return image.decode().map(decoded -> RasterImages.asJpeg(
                decoded, options.maxEdge(), options.maxEdge(), options.losslessQuality()));
        });

        assertEquals(1, calls.get());
        assertTrue(result.length < identity.length);
    }

    @Test
    void recompressesIccBasedRgbSamplesAndPreservesTheirProfile() throws Exception {
        var profile = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
        var source = pdfWithIccBasedRgb(640, 480, profile);
        var options = OptimizeOptions.builder().maxEdge(800).losslessQuality(0.90f)
            .minimumLosslessBytes(0).minimumSavingsBytes(1).minimumSavingsPercent(0).build();
        var calls = new AtomicInteger();

        var result = PdfOptimizer.recompress(source, options, (image, policy) -> {
            calls.incrementAndGet();
            return image.decode().map(decoded -> RasterImages.asJpeg(
                decoded, policy.maxEdge(), policy.maxEdge(), policy.losslessQuality()));
        });

        assertEquals(1, calls.get());
        assertTrue(result.length < source.length, "optimized=" + result.length + " source=" + source.length);
        try (var document = Loader.loadPDF(result)) {
            var image = firstImage(document);
            var colorSpace = assertInstanceOf(PDICCBased.class, image.getColorSpace());
            try (var input = colorSpace.getPDStream().createInputStream()) {
                assertArrayEquals(profile, input.readAllBytes());
            }
            assertEquals(COSName.DCT_DECODE, image.getCOSObject().getCOSName(COSName.FILTER));
        }
    }

    @Test
    void recompressesAColourImageWithASimpleSoftMaskWithoutResamplingEitherPlane() throws Exception {
        var source = pdfWithSoftMask(false, false);
        try (var document = new PdfDocument(new PdfReader(source))) {
            var image = document.importedImages().getFirst();
            assertTrue(image.safeToRecompress());
            assertTrue(image.requiresOriginalDimensions());
        }

        var optimized = PdfOptimizer.recompress(source, TEST_OPTIONS);

        assertTrue(optimized.length < source.length);
        try (var before = Loader.loadPDF(source); var after = Loader.loadPDF(optimized)) {
            var original = firstImage(before);
            var replacement = firstImage(after);
            assertEquals(original.getWidth(), replacement.getWidth());
            assertEquals(original.getHeight(), replacement.getHeight());
            assertEquals(COSName.DCT_DECODE, replacement.getCOSObject().getCOSName(COSName.FILTER));
            assertArrayEquals(decodedSoftMask(original), decodedSoftMask(replacement));
            assertArrayEquals(alpha(original.getImage()), alpha(replacement.getImage()));
        }
        assertArrayEquals(optimized, PdfOptimizer.recompress(optimized, TEST_OPTIONS));
    }

    @Test
    void acceptsASoftMaskWithoutItsOptionalTypeEntry() throws Exception {
        var source = withoutSoftMaskType(pdfWithSoftMask(false, false));
        try (var document = new PdfDocument(new PdfReader(source))) {
            assertTrue(document.importedImages().getFirst().safeToRecompress());
        }

        var optimized = PdfOptimizer.recompress(source, TEST_OPTIONS);

        assertTrue(optimized.length < source.length);
        try (var before = Loader.loadPDF(source); var after = Loader.loadPDF(optimized)) {
            assertArrayEquals(decodedSoftMask(firstImage(before)), decodedSoftMask(firstImage(after)));
        }
    }

    @Test
    void rejectsAResizedReplacementWhenTheImageHasASoftMask() throws Exception {
        var source = pdfWithSoftMask(false, false);
        var calls = new AtomicInteger();

        var result = PdfOptimizer.recompress(source, TEST_OPTIONS, (image, options) -> {
            calls.incrementAndGet();
            return image.decode().map(decoded -> RasterImages.asJpeg(
                decoded, options.maxEdge(), options.maxEdge(), options.losslessQuality()));
        });

        assertEquals(1, calls.get());
        try (var document = Loader.loadPDF(result)) {
            var image = firstImage(document);
            assertEquals(COSName.FLATE_DECODE, image.getCOSObject().getCOSName(COSName.FILTER));
            assertEquals(900, image.getWidth());
            assertEquals(700, image.getHeight());
        }
    }

    @Test
    void neverRecompressesPreblendedOrNonGraySoftMasks() throws Exception {
        for (var source : java.util.List.of(
            pdfWithSoftMask(true, false), pdfWithSoftMask(false, true))) {
            var calls = new AtomicInteger();
            try (var document = new PdfDocument(new PdfReader(source))) {
                assertFalse(document.importedImages().getFirst().safeToRecompress());
            }

            var result = PdfOptimizer.recompress(source, TEST_OPTIONS, (image, options) -> {
                calls.incrementAndGet();
                return image.decode();
            });

            assertEquals(0, calls.get());
            try (var document = Loader.loadPDF(result)) {
                assertEquals(COSName.FLATE_DECODE,
                    firstImage(document).getCOSObject().getCOSName(COSName.FILTER));
            }
        }
    }

    @Test
    void rejectsIccBasedImagesWhoseComponentCountCannotBePreservedByJpeg() throws Exception {
        var profile = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
        for (var components : java.util.List.of(1, 2, 4)) {
            var source = pdfWithIccBasedSamples(64, 48, profile, components);
            try (var document = new PdfDocument(new PdfReader(source))) {
                var image = document.importedImages().getFirst();
                assertEquals("ICCBased", image.colorSpace());
                assertFalse(image.safeToRecompress(), "components=" + components);
            }
        }
    }

    @Test
    void rejectsCustomIccReplacementWithADifferentComponentCount() throws Exception {
        var profile = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
        var source = pdfWithIccBasedRgb(640, 480, profile);
        var gray = ImageData.fromJpeg(grayJpeg(640, 480));
        assertEquals(1, gray.components());

        var result = PdfOptimizer.recompress(source, TEST_OPTIONS,
            (image, options) -> java.util.Optional.of(gray));

        assertSame(source, result);
    }

    @Test
    void decodesAscii85AndFlateWrappersAroundJpegImages() throws Exception {
        var jpeg = jpeg(640, 480);
        var source = withAscii85FlateJpegWrappers(pdfWith(ImageData.fromJpeg(jpeg)));
        var calls = new AtomicInteger();

        var result = PdfOptimizer.recompress(source, TEST_OPTIONS, (image, options) -> {
            calls.incrementAndGet();
            return image.decode();
        });

        assertEquals(1, calls.get());
        assertTrue(result.length < source.length);
        try (var document = Loader.loadPDF(result)) {
            var image = firstImage(document);
            assertEquals(COSName.DCT_DECODE, image.getCOSObject().getCOSName(COSName.FILTER));
            assertArrayEquals(ImageIO.read(new ByteArrayInputStream(jpeg)).getRGB(
                    0, 0, 640, 480, null, 0, 640),
                image.getImage().getRGB(0, 0, 640, 480, null, 0, 640));
        }
    }

    @Test
    void keepsLosslessImagesBelowTheAttachmentWorkFloorAwayFromTheCodec() throws Exception {
        var source = pdfWith(RasterImages.decode(noisyPng(64, 64)));
        var options = OptimizeOptions.attachments();
        try (var document = new PdfDocument(new PdfReader(source))) {
            assertTrue(document.importedImages().getFirst().encodedLength()
                < options.minimumLosslessBytes());
        }
        var calls = new AtomicInteger();

        PdfOptimizer.recompress(source, options, (image, policy) -> {
            calls.incrementAndGet();
            return image.decode();
        });

        assertEquals(0, calls.get());
    }

    @Test
    void customEncoderKeepsSkaldGraphAndSavingsGuards() throws Exception {
        var source = pdfWith(RasterImages.decode(noisyPng(800, 600)));
        var calls = new AtomicInteger();
        var optimized = PdfOptimizer.recompress(source, TEST_OPTIONS, (image, options) -> {
            calls.incrementAndGet();
            return image.decode().map(decoded -> RasterImages.asJpeg(
                decoded, options.maxEdge(), options.maxEdge(), options.losslessQuality()));
        });

        assertEquals(1, calls.get());
        assertTrue(optimized.length < source.length);
        try (var box = Loader.loadPDF(optimized)) {
            assertEquals(1, box.getNumberOfPages());
        }
    }

    @Test
    void recompressesASharedImageStreamOnlyOnce() throws Exception {
        var source = pdfWith(RasterImages.decode(noisyPng(800, 600)));
        byte[] shared;
        try (var document = Loader.loadPDF(source)) {
            var image = firstImage(document);
            document.getPage(0).getResources().put(COSName.getPDFName("Alias"), image);
            var output = new ByteArrayOutputStream();
            document.save(output);
            shared = output.toByteArray();
        }
        var calls = new AtomicInteger();
        var optimized = PdfOptimizer.recompress(shared, TEST_OPTIONS, (image, options) -> {
            calls.incrementAndGet();
            return image.decode().map(decoded -> RasterImages.asJpeg(
                decoded, options.maxEdge(), options.maxEdge(), options.losslessQuality()));
        });
        assertEquals(1, calls.get());
        try (var document = new PdfDocument(new PdfReader(optimized))) {
            assertEquals(1, document.importedImages().size());
        }
    }

    @Test
    void sharesByteIdenticalSimpleImageObjectsWithoutChangingPageResources() throws Exception {
        var source = pdfWithDuplicateJpegObjects(24);
        var options = OptimizeOptions.builder().recompressJpeg(false).convertLosslessRaster(false)
            .compressStreamsLosslessly(false).minimumSavingsBytes(1).minimumSavingsPercent(0).build();

        var optimized = PdfOptimizer.recompress(source, options);

        assertTrue(optimized.length < source.length);
        try (var before = Loader.loadPDF(source); var after = Loader.loadPDF(optimized)) {
            assertEquals(24, distinctPageImageObjects(before));
            assertEquals(1, distinctPageImageObjects(after));
            assertEquals(before.getNumberOfPages(), after.getNumberOfPages());
            for (var page = 0; page < before.getNumberOfPages(); page++) {
                assertArrayEquals(raw(firstImage(before, page)), raw(firstImage(after, page)));
            }
        }
        assertArrayEquals(optimized, PdfOptimizer.recompress(optimized, options));

        var disabled = OptimizeOptions.builder().recompressJpeg(false).convertLosslessRaster(false)
            .compressStreamsLosslessly(false).deduplicateImagesLosslessly(false)
            .minimumSavingsBytes(1).minimumSavingsPercent(0).build();
        try (var document = Loader.loadPDF(PdfOptimizer.recompress(source, disabled))) {
            assertEquals(24, distinctPageImageObjects(document));
        }
    }

    @Test
    void sharesByteIdenticalFontProgramsWithoutChangingTextOrFontDictionaries() throws Exception {
        var source = pdfWithDuplicateFontPrograms();
        var options = OptimizeOptions.builder().recompressJpeg(false).convertLosslessRaster(false)
            .compressStreamsLosslessly(false).deduplicateImagesLosslessly(false)
            .minimumSavingsBytes(1).minimumSavingsPercent(0).build();

        var optimized = PdfOptimizer.recompress(source, options);

        assertTrue(optimized.length < source.length);
        try (var before = Loader.loadPDF(source); var after = Loader.loadPDF(optimized)) {
            assertEquals(2, distinctFontPrograms(before));
            assertEquals(1, distinctFontPrograms(after));
            assertEquals(new PDFTextStripper().getText(before), new PDFTextStripper().getText(after));
            assertEquals(2, fontResourceCount(before.getPage(0).getResources()));
            assertEquals(2, fontResourceCount(after.getPage(0).getResources()));
        }
        assertArrayEquals(optimized, PdfOptimizer.recompress(optimized, options));

        var disabled = OptimizeOptions.builder().recompressJpeg(false).convertLosslessRaster(false)
            .compressStreamsLosslessly(false).deduplicateImagesLosslessly(false)
            .deduplicateFontProgramsLosslessly(false)
            .minimumSavingsBytes(1).minimumSavingsPercent(0).build();
        assertSame(source, PdfOptimizer.recompress(source, disabled));
    }

    @Test
    void keepsIdenticalFontBytesSeparateWhenTheirStreamDictionariesDiffer() throws Exception {
        var source = pdfWithDuplicateFontPrograms(true);
        var options = OptimizeOptions.builder().recompressJpeg(false).convertLosslessRaster(false)
            .compressStreamsLosslessly(false).deduplicateImagesLosslessly(false)
            .minimumSavingsBytes(1).minimumSavingsPercent(0).build();

        var optimized = PdfOptimizer.recompress(source, options);

        try (var document = Loader.loadPDF(optimized)) {
            assertEquals(2, distinctFontPrograms(document));
            var guarded = 0;
            for (var name : document.getPage(0).getResources().getFontNames()) {
                var stream = document.getPage(0).getResources().getFont(name)
                    .getFontDescriptor().getFontFile2();
                if (stream.getCOSObject().getBoolean(COSName.getPDFName("SkaldGuard"), false)) {
                    guarded++;
                }
            }
            assertEquals(1, guarded);
        }
    }

    @Test
    void findsAndReplacesImagesInsideNestedFormXObjects() throws Exception {
        var source = nestedFormPdf();
        var optimized = PdfOptimizer.recompress(source, TEST_OPTIONS);

        assertTrue(optimized.length < source.length);
        try (var document = new PdfDocument(new PdfReader(optimized))) {
            var image = document.importedImages().getFirst();
            assertTrue(image.resourceName().contains("/"));
            assertTrue(image.jpeg());
            assertTrue(Math.max(image.width(), image.height()) <= TEST_OPTIONS.maxEdge());
        }
    }

    @Test
    void separateOptimizationsAreSafeOnVirtualThreads() throws Exception {
        var source = pdfWith(RasterImages.decode(noisyPng(700, 500)));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.IntStream.range(0, 24)
                .mapToObj(ignored -> executor.submit(() -> PdfOptimizer.recompress(source, TEST_OPTIONS)))
                .toList();
            for (var task : tasks) {
                assertArrayEquals(tasks.getFirst().get(), task.get());
            }
        }
    }

    @Test
    void compressesRawStreamsWithoutChangingTheirDecodedBytes() throws Exception {
        var content = "q Q\n".repeat(200_000).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var source = pdfWithContent(content, false);
        var options = OptimizeOptions.builder().recompressJpeg(false).convertLosslessRaster(false)
            .minimumSavingsBytes(1).minimumSavingsPercent(0).build();

        var optimized = PdfOptimizer.recompress(source, options);

        assertTrue(optimized.length < source.length / 10);
        assertArrayEquals(content, pageContent(optimized));
        try (var document = Loader.loadPDF(optimized)) {
            assertEquals(COSName.FLATE_DECODE,
                document.getPage(0).getContentStreams().next().getFilters().getFirst());
        }
        assertArrayEquals(optimized, PdfOptimizer.recompress(optimized, options));
    }

    @Test
    void recompressesExistingFlateStreamsAndKeepsDecodedBytesExact() throws Exception {
        var content = java.util.stream.IntStream.range(0, 80_000)
            .mapToObj(index -> "q 1 0 0 1 " + index % 997 + " " + index % 991 + " cm Q\n")
            .collect(java.util.stream.Collectors.joining())
            .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var source = pdfWithContent(content, true);
        var options = OptimizeOptions.builder().recompressJpeg(false).convertLosslessRaster(false)
            .minimumSavingsBytes(1).minimumSavingsPercent(0).build();

        var optimized = PdfOptimizer.recompress(source, options);

        assertTrue(optimized.length < source.length);
        assertArrayEquals(content, pageContent(optimized));
        assertArrayEquals(optimized, PdfOptimizer.recompress(optimized, options));
    }

    @Test
    void losslessStreamCompressionCanBeDisabled() throws Exception {
        var content = "q Q\n".repeat(100_000).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var source = pdfWithContent(content, false);
        var options = OptimizeOptions.builder().recompressJpeg(false).convertLosslessRaster(false)
            .compressStreamsLosslessly(false).minimumSavingsBytes(1).minimumSavingsPercent(0).build();

        var optimized = PdfOptimizer.recompress(source, options);

        assertArrayEquals(content, pageContent(optimized));
        try (var document = Loader.loadPDF(optimized)) {
            assertTrue(document.getPage(0).getContentStreams().next().getFilters().isEmpty());
        }
    }

    @Test
    void replacesAscii85WithLosslessFlateCompression() throws Exception {
        var content = "q 1 0 0 1 0 0 cm Q\n".repeat(30_000)
            .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var source = pdfWithAscii85Content(content);
        var options = OptimizeOptions.builder().recompressJpeg(false).convertLosslessRaster(false)
            .minimumSavingsBytes(1).minimumSavingsPercent(0).build();

        var optimized = PdfOptimizer.recompress(source, options);

        assertTrue(optimized.length < source.length / 10);
        assertArrayEquals(content, pageContent(optimized));
        try (var document = Loader.loadPDF(optimized)) {
            assertEquals(COSName.FLATE_DECODE,
                document.getPage(0).getContentStreams().next().getFilters().getFirst());
        }
        assertArrayEquals(optimized, PdfOptimizer.recompress(optimized, options));
    }

    @Test
    void malformedAndMutatedInputsNeverProduceANewUnparseablePdf() throws Exception {
        var source = pdfWith(RasterImages.decode(noisyPng(420, 300)));
        var random = new java.util.Random(73);
        for (int iteration = 0; iteration < 250; iteration++) {
            var mutated = source.clone();
            var changes = 1 + random.nextInt(4);
            for (int change = 0; change < changes; change++) {
                var index = 8 + random.nextInt(mutated.length - 8);
                mutated[index] ^= (byte) (1 << random.nextInt(8));
            }
            var result = PdfOptimizer.recompress(mutated, TEST_OPTIONS);
            if (result != mutated) {
                try (var skald = new PdfDocument(new PdfReader(result)); var box = Loader.loadPDF(result)) {
                    assertEquals(skald.getNumberOfPages(), box.getNumberOfPages());
                }
            }
        }
        for (int length = 0; length < source.length; length += Math.max(1, source.length / 97)) {
            var truncated = Arrays.copyOf(source, length);
            var result = PdfOptimizer.recompress(truncated, TEST_OPTIONS);
            if (result != truncated) {
                try (var box = Loader.loadPDF(result)) {
                    assertTrue(box.getNumberOfPages() > 0);
                }
            }
        }
    }

    private static byte[] withDecodeArray(byte[] source) throws Exception {
        return withDecodeArray(source, true);
    }

    private static byte[] withDecodeArray(byte[] source, boolean inverted) throws Exception {
        try (PDDocument document = Loader.loadPDF(source)) {
            var image = firstImage(document);
            var decode = new COSArray();
            for (int component = 0; component < 3; component++) {
                decode.add(new COSFloat(inverted ? 1 : 0));
                decode.add(new COSFloat(inverted ? 0 : 1));
            }
            image.getCOSObject().setItem(COSName.DECODE, decode);
            var output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] withAscii85FlateJpegWrappers(byte[] source) throws Exception {
        try (var document = Loader.loadPDF(source)) {
            var image = firstImage(document);
            var encoded = new ByteArrayOutputStream();
            try (var deflater = new Deflater(Deflater.BEST_SPEED);
                 var output = new DeflaterOutputStream(encoded, deflater)) {
                output.write(raw(image));
            }
            var filters = new COSArray();
            filters.add(COSName.ASCII85_DECODE);
            filters.add(COSName.FLATE_DECODE);
            filters.add(COSName.DCT_DECODE);
            image.getCOSObject().setItem(COSName.FILTER, filters);
            try (var output = image.getCOSObject().createRawOutputStream()) {
                output.write(ascii85(encoded.toByteArray()));
            }
            var result = new ByteArrayOutputStream();
            document.save(result);
            return result.toByteArray();
        }
    }

    private static byte[] pdfWithContent(byte[] content, boolean poorlyDeflated) throws Exception {
        try (var document = new PDDocument()) {
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDStream stream;
            if (poorlyDeflated) {
                var encoded = new ByteArrayOutputStream();
                try (var deflater = new Deflater(Deflater.BEST_SPEED);
                     var output = new DeflaterOutputStream(encoded, deflater)) {
                    output.write(content);
                }
                var cos = document.getDocument().createCOSStream();
                cos.setItem(COSName.FILTER, COSName.FLATE_DECODE);
                try (var output = cos.createRawOutputStream()) {
                    output.write(encoded.toByteArray());
                }
                stream = new PDStream(cos);
            } else {
                stream = new PDStream(document, new ByteArrayInputStream(content));
            }
            page.setContents(stream);
            var output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdfWithAscii85Content(byte[] content) throws Exception {
        try (var document = new PDDocument()) {
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            var cos = document.getDocument().createCOSStream();
            cos.setItem(COSName.FILTER, COSName.ASCII85_DECODE);
            try (var output = cos.createRawOutputStream()) {
                output.write(ascii85(content));
            }
            page.setContents(new PDStream(cos));
            var output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] ascii85(byte[] source) {
        var output = new ByteArrayOutputStream(source.length * 5 / 4 + 2);
        for (int offset = 0; offset < source.length; offset += 4) {
            var count = Math.min(4, source.length - offset);
            long value = 0;
            for (int index = 0; index < 4; index++) {
                value = value << 8 | (index < count ? source[offset + index] & 0xffL : 0);
            }
            if (count == 4 && value == 0) {
                output.write('z');
                continue;
            }
            var digits = new byte[5];
            for (int index = 4; index >= 0; index--) {
                digits[index] = (byte) (value % 85 + '!');
                value /= 85;
            }
            output.write(digits, 0, count + 1);
        }
        output.write('~');
        output.write('>');
        return output.toByteArray();
    }

    private static byte[] pageContent(byte[] pdf) throws Exception {
        try (var document = Loader.loadPDF(pdf); var content = document.getPage(0).getContents()) {
            return content.readAllBytes();
        }
    }

    private static byte[] nestedFormPdf() throws Exception {
        try (var document = new PDDocument()) {
            var page = new PDPage(new PDRectangle(400, 300));
            document.addPage(page);
            var raster = ImageIO.read(new java.io.ByteArrayInputStream(noisyPng(800, 600)));
            var image = LosslessFactory.createFromImage(document, raster);
            var form = new PDFormXObject(document);
            form.setBBox(new PDRectangle(400, 300));
            var resources = new PDResources();
            var imageName = resources.add(image);
            form.setResources(resources);
            try (var content = form.getCOSObject().createOutputStream()) {
                content.write(("q 400 0 0 300 0 0 cm /" + imageName.getName() + " Do Q\n")
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }
            try (var content = new PDPageContentStream(document, page)) {
                content.drawForm(form);
            }
            var output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdfWithDuplicateJpegObjects(int pages) throws Exception {
        var encoded = jpeg(300, 136);
        try (var document = new PDDocument()) {
            for (var pageNumber = 0; pageNumber < pages; pageNumber++) {
                var page = new PDPage(new PDRectangle(400, 300));
                document.addPage(page);
                var image = PDImageXObject.createFromByteArray(document, encoded, "repeated");
                try (var content = new PDPageContentStream(document, page)) {
                    content.drawImage(image, 50, 70, 300, 136);
                }
            }
            var output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdfWithTrailingFlateContent() throws Exception {
        var encoded = new ByteArrayOutputStream();
        encoded.write(flate("q Q\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        encoded.write(0x42);
        return pdfWithRawFlateContent(encoded.toByteArray());
    }

    private static byte[] flate(byte[] source) throws Exception {
        var encoded = new ByteArrayOutputStream();
        try (var deflater = new Deflater(Deflater.BEST_SPEED);
             var output = new DeflaterOutputStream(encoded, deflater)) {
            output.write(source);
        }
        return encoded.toByteArray();
    }

    private static byte[] pdfWithRawFlateContent(byte[] encoded) throws Exception {
        try (var document = new PDDocument()) {
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            var stream = new PDStream(document);
            stream.getCOSObject().setItem(COSName.FILTER, COSName.FLATE_DECODE);
            try (var output = stream.getCOSObject().createRawOutputStream()) {
                output.write(encoded);
            }
            page.setContents(stream);
            var output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdfWithIccBasedRgb(int width, int height, byte[] profile) throws Exception {
        return pdfWithIccBasedSamples(width, height, profile, 3);
    }

    private static byte[] pdfWithSoftMask(boolean matte, boolean rgbMask) throws Exception {
        var width = 900;
        var height = 700;
        var colour = ImageIO.read(new ByteArrayInputStream(noisyPng(width, height)));
        var alpha = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var value = (x * 255 / (width - 1)) << 16;
                alpha.setRGB(x, y, value | value >>> 8 | value >>> 16);
            }
        }
        try (var document = new PDDocument()) {
            var page = new PDPage(new PDRectangle(width, height));
            document.addPage(page);
            var image = LosslessFactory.createFromImage(document, colour);
            var mask = LosslessFactory.createFromImage(document, alpha);
            if (matte) {
                var values = new COSArray();
                values.add(new COSFloat(1));
                values.add(new COSFloat(1));
                values.add(new COSFloat(1));
                mask.getCOSObject().setItem(COSName.getPDFName("Matte"), values);
            }
            if (rgbMask) {
                mask.getCOSObject().setItem(COSName.COLORSPACE, COSName.DEVICERGB);
            }
            image.getCOSObject().setItem(COSName.SMASK, mask.getCOSObject());
            try (var content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 0, 0, width, height);
            }
            var output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] decodedSoftMask(PDImageXObject image) throws Exception {
        var mask = assertInstanceOf(COSStream.class,
            image.getCOSObject().getDictionaryObject(COSName.SMASK));
        try (var input = mask.createInputStream()) {
            return input.readAllBytes();
        }
    }

    private static byte[] withoutSoftMaskType(byte[] source) throws Exception {
        try (var document = Loader.loadPDF(source)) {
            var mask = assertInstanceOf(COSStream.class,
                firstImage(document).getCOSObject().getDictionaryObject(COSName.SMASK));
            mask.removeItem(COSName.TYPE);
            var output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private static int[] alpha(BufferedImage image) {
        var pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(),
            null, 0, image.getWidth());
        Arrays.setAll(pixels, index -> pixels[index] >>> 24);
        return pixels;
    }

    private static byte[] pdfWithIccBasedSamples(int width, int height, byte[] profile, int components) throws Exception {
        var samples = new byte[Math.multiplyExact(Math.multiplyExact(width, height), components)];
        new java.util.Random(73).nextBytes(samples);
        try (var document = new PDDocument()) {
            var page = new PDPage(new PDRectangle(width, height));
            document.addPage(page);
            var icc = new PDICCBased(document);
            icc.getPDStream().getCOSObject().setInt(COSName.N, components);
            icc.setAlternateColorSpaces(java.util.List.of(PDDeviceRGB.INSTANCE));
            try (var output = icc.getPDStream().createOutputStream(COSName.FLATE_DECODE)) {
                output.write(profile);
            }
            var encoded = new ByteArrayOutputStream();
            try (var deflater = new Deflater(Deflater.BEST_SPEED);
                 var output = new DeflaterOutputStream(encoded, deflater)) {
                output.write(samples);
            }
            var image = new PDImageXObject(document, new ByteArrayInputStream(encoded.toByteArray()), COSName.FLATE_DECODE,
                width, height, 8, icc);
            try (var content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 0, 0, width, height);
            }
            var output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private static int distinctPageImageObjects(PDDocument document) throws Exception {
        var images = java.util.Collections.newSetFromMap(
            new java.util.IdentityHashMap<org.apache.pdfbox.cos.COSStream, Boolean>());
        for (var page = 0; page < document.getNumberOfPages(); page++) {
            images.add(firstImage(document, page).getCOSObject());
        }
        return images.size();
    }

    private static int distinctFontPrograms(PDDocument document) throws Exception {
        var programs = java.util.Collections.newSetFromMap(
            new java.util.IdentityHashMap<org.apache.pdfbox.cos.COSStream, Boolean>());
        for (var page : document.getPages()) {
            for (var name : page.getResources().getFontNames()) {
                var descriptor = page.getResources().getFont(name).getFontDescriptor();
                var stream = descriptor.getFontFile2();
                if (stream == null) {
                    stream = descriptor.getFontFile3();
                }
                if (stream == null) {
                    stream = descriptor.getFontFile();
                }
                if (stream != null) {
                    programs.add(stream.getCOSObject());
                }
            }
        }
        return programs.size();
    }

    private static int fontResourceCount(PDResources resources) {
        var count = 0;
        for (var ignored : resources.getFontNames()) {
            count++;
        }
        return count;
    }

    private static byte[] pdfWithDuplicateFontPrograms() throws Exception {
        return pdfWithDuplicateFontPrograms(false);
    }

    private static byte[] pdfWithDuplicateFontPrograms(boolean distinguishSecond) throws Exception {
        var fontBytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
            "skald-fonts/src/main/resources/org/skaldpdf/fonts/SkaldSans-Regular.ttf"));
        byte[] source;
        try (var document = new PDDocument()) {
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            var first = PDType0Font.load(document, new ByteArrayInputStream(fontBytes));
            var second = PDType0Font.load(document, new ByteArrayInputStream(fontBytes));
            try (var content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.newLineAtOffset(72, 740);
                content.setFont(first, 12);
                content.showText("Identical font program");
                content.newLineAtOffset(0, -24);
                content.setFont(second, 12);
                content.showText("Identical font program");
                content.endText();
            }
            var output = new ByteArrayOutputStream();
            document.save(output);
            source = output.toByteArray();
        }
        if (!distinguishSecond) {
            return source;
        }
        try (var document = Loader.loadPDF(source)) {
            var names = new java.util.ArrayList<COSName>();
            document.getPage(0).getResources().getFontNames().forEach(names::add);
            var stream = document.getPage(0).getResources().getFont(names.getLast())
                .getFontDescriptor().getFontFile2();
            stream.getCOSObject().setBoolean(COSName.getPDFName("SkaldGuard"), true);
            var output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private static void assertImagePayloadsEqual(byte[] before, byte[] after) throws Exception {
        try (var first = Loader.loadPDF(before); var second = Loader.loadPDF(after)) {
            assertArrayEquals(raw(firstImage(first)), raw(firstImage(second)));
        }
    }

    private static PDImageXObject firstImage(PDDocument document) throws Exception {
        return firstImage(document, 0);
    }

    private static PDImageXObject firstImage(PDDocument document, int pageNumber) throws Exception {
        var page = document.getPage(pageNumber);
        for (var name : page.getResources().getXObjectNames()) {
            var object = page.getResources().getXObject(name);
            if (object instanceof PDImageXObject image) {
                return image;
            }
        }
        throw new AssertionError("fixture image missing");
    }

    private static byte[] raw(PDImageXObject image) throws Exception {
        try (var input = image.getCOSObject().createRawInputStream()) {
            return input.readAllBytes();
        }
    }

    private static byte[] pdfWith(ImageData image) {
        var output = new ByteArrayOutputStream();
        try (var pdf = new PdfDocument(new PdfWriter(output))) {
            image.drawOn(pdf, pdf.addNewPage(new PageSize(400, 300)), 10, 10, 380, 280);
        }
        return output.toByteArray();
    }

    private static byte[] noisyPng(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var random = new java.util.Random(19);
        var pixels = new int[width * height];
        Arrays.setAll(pixels, ignored -> random.nextInt(0x0100_0000));
        image.setRGB(0, 0, width, height, pixels, 0, width);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static byte[] grayJpeg(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        return output.toByteArray();
    }

    private static byte[] jpeg(int width, int height) throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(noisyPng(width, height)));
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        return output.toByteArray();
    }
}
