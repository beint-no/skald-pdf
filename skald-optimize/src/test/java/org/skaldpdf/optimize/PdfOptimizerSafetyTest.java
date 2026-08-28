package org.skaldpdf.optimize;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.skaldpdf.codec.RasterImages;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.image.ImageData;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfReader;
import org.skaldpdf.pdf.PdfWriter;
import org.skaldpdf.pdf.SignatureField;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        try (PDDocument document = Loader.loadPDF(source)) {
            var image = firstImage(document);
            var decode = new COSArray();
            decode.add(new COSFloat(1));
            decode.add(new COSFloat(0));
            decode.add(new COSFloat(1));
            decode.add(new COSFloat(0));
            decode.add(new COSFloat(1));
            decode.add(new COSFloat(0));
            image.getCOSObject().setItem(COSName.DECODE, decode);
            var output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
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

    private static void assertImagePayloadsEqual(byte[] before, byte[] after) throws Exception {
        try (var first = Loader.loadPDF(before); var second = Loader.loadPDF(after)) {
            assertArrayEquals(raw(firstImage(first)), raw(firstImage(second)));
        }
    }

    private static PDImageXObject firstImage(PDDocument document) throws Exception {
        var page = document.getPage(0);
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
}
