package org.skaldpdf.pdf;

import org.junit.jupiter.api.Test;
import org.skaldpdf.geom.PageSize;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreHardeningTest {
    @Test
    void doesNotAllocateOrphanContentStreamWhenImportingUnmodifiedPages() {
        var source = new ByteArrayOutputStream();
        try (var doc = new PdfDocument(new PdfWriter(source))) {
            doc.addNewPage(PageSize.A4).append("0 0 100 100 re f\n");
            doc.addNewPage(PageSize.A4).append("10 10 50 50 re f\n");
        }
        var merged = new ByteArrayOutputStream();
        try (var reader = new PdfReader(source.toByteArray());
             var input = new PdfDocument(reader);
             var output = new PdfDocument(new PdfWriter(merged))) {
            output.copyPagesFrom(input, 1, 2);
        }
        var parser = new NativePdfParser(merged.toByteArray());
        // Collect all indirect object numbers referenced from trailer and verify no extra unreferenced Flate streams exist.
        var referenced = new java.util.HashSet<Integer>();
        var pending = new java.util.ArrayDeque<CosValue>();
        pending.add(parser.trailer());
        while (!pending.isEmpty()) {
            var value = pending.removeLast();
            if (value instanceof CosValue.CosReference ref) {
                if (referenced.add(ref.objectNumber())) {
                    pending.add(parser.resolve(ref));
                }
            } else if (value instanceof CosValue.CosArray array) {
                pending.addAll(array.values());
            } else if (value instanceof CosValue.CosDictionary dict) {
                pending.addAll(dict.values().values());
            } else if (value instanceof CosValue.CosStream stream) {
                pending.addAll(stream.dictionary().values().values());
            }
        }
        // Every object number 1..Size-1 except the xref stream itself and ObjStm containers should be reachable.
        var size = Integer.parseInt(((CosValue.CosNumber) parser.trailer().get("Size")).lexicalValue());
        var orphanStreams = 0;
        for (int obj = 1; obj < size; obj++) {
            if (referenced.contains(obj)) {
                continue;
            }
            var resolved = parser.resolve(new CosValue.CosReference(obj, 0));
            if (resolved instanceof CosValue.CosStream stream) {
                var type = stream.dictionary().get("Type");
                if (!(type instanceof CosValue.CosName name
                    && (name.value().equals("XRef") || name.value().equals("ObjStm")))) {
                    orphanStreams++;
                }
            }
        }
        assertEquals(0, orphanStreams, "Unmodified imported pages must not allocate orphan content streams");
    }

    @Test
    void stripsIllegalXmlControlCharactersFromXmpMetadata() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var doc = new PdfDocument(new PdfWriter(output))) {
            doc.getDocumentInfo()
                .setTitle("Invoice\u0000 #1\u0007\t\n<A & B>")
                .setAuthor("Alice\u000B\u000C\u001F")
                .setSubject("Sub\u0001ject")
                .setKeywords("k1\u0002, k2");
            doc.addNewPage(PageSize.A4);
        }
        var parser = new NativePdfParser(output.toByteArray());
        var catalog = (CosValue.CosDictionary) parser.resolve(parser.catalogReference());
        var metadata = (CosValue.CosStream) parser.resolve(catalog.get("Metadata"));
        var xmp = new String(parser.decodedStream(metadata, "XMP"), StandardCharsets.UTF_8);

        // Must parse cleanly as strict XML 1.0 without throwing SAXParseException.
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var xmlDoc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmp)));
        assertEquals("xmpmeta", xmlDoc.getDocumentElement().getLocalName());
        assertTrue(xmp.contains("Invoice #1\t\n&lt;A &amp; B&gt;"), xmp);
        assertTrue(xmp.contains(">Alice<"), xmp);
        assertTrue(xmp.contains(">Subject<"), xmp);
        assertTrue(xmp.contains(">k1, k2<"), xmp);
    }

    @Test
    void scansByteRangeDirectlyAndGuardsAgainstOverflowingNumbers() {
        assertTrue(NativePdfParser.containsSealedSignature(
            "prefix /ByteRange [0 100 200 300] suffix".getBytes(StandardCharsets.ISO_8859_1)));
        assertTrue(NativePdfParser.containsSealedSignature(
            "/ByteRange\r\n[\t0\n10\r\n20\t30 ]".getBytes(StandardCharsets.ISO_8859_1)));
        assertFalse(NativePdfParser.containsSealedSignature(
            "/ByteRange [0 0000000000 0000000000 0000000000]".getBytes(StandardCharsets.ISO_8859_1)));
        assertFalse(NativePdfParser.containsSealedSignature(
            "/ByteRange [0 999999999999999999999999999 0 999999999999999999999999999]"
                .getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    void skipsInflateWhenFlateStreamAlreadyDeclaresMaximumDeflateLevelHint() {
        var minimal = minimalPdf(0, "[0 0 600 800]", "[0 0 600 800]");
        var parser = new NativePdfParser(minimal);
        // Header 0x78 0xDA has FLEVEL=3 (valid zlib header checksum: (0x78 << 8 | 0xDA) % 31 == 0)
        // followed by arbitrary bytes that would fail inflate if inflate were mistakenly called first.
        var payload = new byte[300];
        payload[0] = 0x78;
        payload[1] = (byte) 0xDA;
        var stream = new CosValue.CosStream(
            new CosValue.CosDictionary(Map.of("Filter", new CosValue.CosName("FlateDecode"))),
            payload);
        assertSame(stream, parser.compressStreamLosslessly(stream, 9));
    }

    @Test
    void normalizesInvertedMediaAndCropBoxCoordinates() {
        var pdf = minimalPdf(0, "[595 842 0 0]", "[550 720 50 20]");
        try (var doc = new PdfDocument(new PdfReader(pdf))) {
            var page = doc.getPage(1);
            assertEquals(595f, page.getPageSize().getWidth(), 0.01f);
            assertEquals(842f, page.getPageSize().getHeight(), 0.01f);
            var crop = page.getCropBox();
            assertEquals(50f, crop.getLeft(), 0.01f);
            assertEquals(20f, crop.getBottom(), 0.01f);
            assertEquals(500f, crop.getWidth(), 0.01f);
            assertEquals(700f, crop.getHeight(), 0.01f);
        }
    }

    @Test
    void wrapsHugeStartXrefOverflowAsIllegalArgumentException() {
        var malformed = "%PDF-1.7\nstartxref\n999999999999999\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        assertThrows(IllegalArgumentException.class, () -> new NativePdfParser(malformed));
    }

    @Test
    void visualCropBoxReflectsPageRotation() {
        // MediaBox [0 0 600 800], CropBox [50 20 550 720] -> left=50, bottom=20, width=500, height=700.
        try (var doc = new PdfDocument(new PdfReader(minimalPdf(0, "[0 0 600 800]", "[50 20 550 720]")))) {
            var box = doc.getPage(1).visualCropBox();
            assertEquals(50f, box.getLeft(), 0.01f);
            assertEquals(20f, box.getBottom(), 0.01f);
            assertEquals(500f, box.getWidth(), 0.01f);
            assertEquals(700f, box.getHeight(), 0.01f);
        }
        try (var doc = new PdfDocument(new PdfReader(minimalPdf(90, "[0 0 600 800]", "[50 20 550 720]")))) {
            var box = doc.getPage(1).visualCropBox();
            assertEquals(20f, box.getLeft(), 0.01f);
            assertEquals(50f, box.getBottom(), 0.01f);
            assertEquals(700f, box.getWidth(), 0.01f);
            assertEquals(500f, box.getHeight(), 0.01f);
        }
        try (var doc = new PdfDocument(new PdfReader(minimalPdf(180, "[0 0 600 800]", "[50 20 550 720]")))) {
            var box = doc.getPage(1).visualCropBox();
            assertEquals(50f, box.getLeft(), 0.01f);
            assertEquals(80f, box.getBottom(), 0.01f);
            assertEquals(500f, box.getWidth(), 0.01f);
            assertEquals(700f, box.getHeight(), 0.01f);
        }
        try (var doc = new PdfDocument(new PdfReader(minimalPdf(270, "[0 0 600 800]", "[50 20 550 720]")))) {
            var box = doc.getPage(1).visualCropBox();
            assertEquals(80f, box.getLeft(), 0.01f);
            assertEquals(50f, box.getBottom(), 0.01f);
            assertEquals(700f, box.getWidth(), 0.01f);
            assertEquals(500f, box.getHeight(), 0.01f);
        }
    }

    @Test
    void pdfReaderDefensivelyCopiesCallerByteArrayOnce() {
        var bytes = minimalPdf(0, "[0 0 600 800]", "[0 0 600 800]");
        try (var reader = new PdfReader(bytes)) {
            bytes[0] = 'X';
            try (var doc = new PdfDocument(reader)) {
                assertEquals(1, doc.getNumberOfPages());
            }
        }
    }

    private static byte[] minimalPdf(int rotate, String mediaBox, String cropBox) {
        var out = new StringBuilder();
        out.append("%PDF-1.7\n");
        var offsets = new int[4];
        offsets[1] = out.length();
        out.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        offsets[2] = out.length();
        out.append("2 0 obj\n<< /Type /Pages /Count 1 /Kids [3 0 R] >>\nendobj\n");
        offsets[3] = out.length();
        out.append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox ").append(mediaBox)
            .append(" /CropBox ").append(cropBox)
            .append(" /Rotate ").append(rotate)
            .append(" /Resources << >> >>\nendobj\n");
        var xref = out.length();
        out.append("xref\n0 4\n0000000000 65535 f \n");
        for (int i = 1; i <= 3; i++) {
            out.append(String.format("%010d 00000 n \n", offsets[i]));
        }
        out.append("trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return out.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
