package org.skaldpdf.pdf;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalRewriteTest {
    @Test
    void preservesTheCompleteReachableGraphIncludingUnknownAndCyclicObjects() {
        var source = featureRichPdf();
        var output = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfReader(source), new PdfWriter(output))) {
            assertTrue(document.isSafeForCanonicalOptimization());
        }

        var before = new NativePdfParser(source);
        var after = new NativePdfParser(output.toByteArray());
        assertArrayEquals(before.semanticDigest(new java.util.IdentityHashMap<>()),
            after.semanticDigest(new java.util.IdentityHashMap<>()));
        assertEquals("1.7", new String(output.toByteArray(), 5, 3, StandardCharsets.US_ASCII));
        assertTrue(output.size() < source.length);

        var catalog = (CosValue.CosDictionary) after.resolve(after.catalogReference());
        assertTrue(catalog.values().containsKey("Outlines"));
        assertTrue(catalog.values().containsKey("Names"));
        assertTrue(catalog.values().containsKey("Metadata"));
        assertTrue(catalog.values().containsKey("StructTreeRoot"));
        assertTrue(catalog.values().containsKey("OCProperties"));
        assertTrue(catalog.values().containsKey("Custom"));
        assertTrue(after.trailer().values().containsKey("CustomTrailer"));
    }

    @Test
    void usesTheMinimumVersionThatSupportsObjectAndXrefStreams() {
        var source = featureRichPdf().clone();
        source[5] = '1';
        source[6] = '.';
        source[7] = '4';
        var output = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfReader(source), new PdfWriter(output))) {
            assertEquals(1, document.getNumberOfPages());
        }
        assertEquals("1.5", new String(output.toByteArray(), 5, 3, StandardCharsets.US_ASCII));
    }

    @Test
    void recognizesProtectedRewriteClasses() {
        var incremental = incrementalPdf();
        try (var document = new PdfDocument(new PdfReader(incremental))) {
            assertFalse(document.isSafeForCanonicalOptimization());
            assertEquals(java.util.Set.of(CanonicalRewriteConstraint.INCREMENTAL_HISTORY),
                document.canonicalRewriteConstraints());
        }
        var conformance = featureRichPdf().clone();
        var marker = "pdfaid:part".getBytes(StandardCharsets.US_ASCII);
        var position = indexOf(conformance, "ordinary-xmp".getBytes(StandardCharsets.US_ASCII));
        System.arraycopy(marker, 0, conformance, position, marker.length);
        try (var document = new PdfDocument(new PdfReader(conformance))) {
            assertFalse(document.isSafeForCanonicalOptimization());
            assertEquals(java.util.Set.of(CanonicalRewriteConstraint.CONFORMANCE_PROFILE),
                document.canonicalRewriteConstraints());
        }
    }

    @Test
    void acceptsAConvergingHybridXrefHistoryAndProtectsIt() {
        try (var document = new PdfDocument(new PdfReader(hybridIncrementalPdf()))) {
            assertFalse(document.isSafeForCanonicalOptimization());
            assertEquals(java.util.Set.of(CanonicalRewriteConstraint.INCREMENTAL_HISTORY),
                document.canonicalRewriteConstraints());
        }
    }

    @Test
    void traversesDeepIndirectGraphsAndTreatsUndefinedReferencesAsNull() {
        var source = deepIndirectPdf(300);
        var output = new ByteArrayOutputStream();
        try (var document = new PdfDocument(new PdfReader(source), new PdfWriter(output))) {
            assertTrue(document.canonicalRewriteConstraints().isEmpty());
        }
        var before = new NativePdfParser(source);
        var after = new NativePdfParser(output.toByteArray());
        assertArrayEquals(before.semanticDigest(new java.util.IdentityHashMap<>()),
            after.semanticDigest(new java.util.IdentityHashMap<>()));
    }

    private static byte[] featureRichPdf() {
        return featureRichPdf("");
    }

    private static byte[] deepIndirectPdf(int depth) {
        var objects = new ArrayList<String>();
        objects.add("<< /Type /Catalog /Pages 2 0 R /Deep 5 0 R /Undefined 9999 0 R >>");
        objects.add("<< /Type /Pages /Count 1 /Kids [3 0 R] >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Contents 4 0 R >>");
        objects.add(stream("q Q\n"));
        for (int index = 0; index < depth; index++) {
            var next = index + 1 == depth ? "null" : (index + 6) + " 0 R";
            objects.add("<< /Next " + next + " >>");
        }
        var output = new ByteArrayOutputStream();
        write(output, "%PDF-1.7\n%âãÏÓ\n");
        var offsets = new ArrayList<Integer>();
        offsets.add(0);
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(output.size());
            write(output, (index + 1) + " 0 obj\n" + objects.get(index) + "\nendobj\n");
        }
        var xref = output.size();
        write(output, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int index = 1; index < offsets.size(); index++) {
            write(output, "%010d 00000 n \n".formatted(offsets.get(index)));
        }
        write(output, "trailer\n<< /Size " + (objects.size() + 1)
            + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return output.toByteArray();
    }

    private static byte[] featureRichPdf(String extraTrailer) {
        var objects = new ArrayList<String>();
        objects.add("<< /Type /Catalog /Pages 2 0 R /Outlines 6 0 R "
            + "/Names << /JavaScript << /Names [(boot) 7 0 R] >> >> /Metadata 8 0 R "
            + "/StructTreeRoot 9 0 R /MarkInfo << /Marked true >> "
            + "/OCProperties 10 0 R /Custom 11 0 R >>");
        objects.add("<< /Type /Pages /Count 1 /Kids [3 0 R] >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] "
            + "/CropBox [5 5 295 195] /Contents 4 0 R /Annots [5 0 R] /Resources << >> >>");
        objects.add(stream("q 1 0 0 1 0 0 cm Q\n"));
        objects.add("<< /Type /Annot /Subtype /Text /Rect [10 10 30 30] /Contents (preserved note) >>");
        objects.add("<< /Type /Outlines /Count 0 >>");
        objects.add("<< /S /JavaScript /JS (app.alert\\(\\\"inert\\\"\\)) >>");
        objects.add(stream("<x:xmpmeta>ordinary-xmp</x:xmpmeta>"));
        objects.add("<< /Type /StructTreeRoot /K [] >>");
        objects.add("<< /OCGs [] /D << /Order [] >> >>");
        objects.add("<< /Label /Preserved /Cycle 11 0 R >>");
        objects.add("<< /Title (Graph fixture) /CustomInfo (kept) >>");
        objects.add("(unreachable bytes that a canonical rewrite may discard)");

        var output = new ByteArrayOutputStream();
        write(output, "%PDF-1.7\n%\u00e2\u00e3\u00cf\u00d3\n");
        var offsets = new ArrayList<Integer>();
        offsets.add(0);
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(output.size());
            write(output, (index + 1) + " 0 obj\n" + objects.get(index) + "\nendobj\n");
        }
        var xref = output.size();
        write(output, "xref\n0 " + (objects.size() + 1) + "\n");
        write(output, "0000000000 65535 f \n");
        for (int index = 1; index < offsets.size(); index++) {
            write(output, "%010d 00000 n \n".formatted(offsets.get(index)));
        }
        write(output, "trailer\n<< /Size " + (objects.size() + 1)
            + " /Root 1 0 R /Info 12 0 R /ID [<00112233> <44556677>]"
            + " /CustomTrailer 11 0 R" + extraTrailer + " >>\nstartxref\n" + xref + "\n%%EOF\n");
        return output.toByteArray();
    }

    private static byte[] incrementalPdf() {
        var base = featureRichPdf();
        var text = new String(base, StandardCharsets.ISO_8859_1);
        var marker = text.lastIndexOf("startxref\n") + "startxref\n".length();
        var previous = Integer.parseInt(text.substring(marker, text.indexOf('\n', marker)));
        var output = new ByteArrayOutputStream();
        output.writeBytes(base);
        var xref = output.size();
        write(output, "xref\n0 1\n0000000000 65535 f \ntrailer\n"
            + "<< /Size 14 /Root 1 0 R /Prev " + previous + " >>\nstartxref\n" + xref + "\n%%EOF\n");
        return output.toByteArray();
    }

    private static byte[] hybridIncrementalPdf() {
        var base = featureRichPdf();
        var text = new String(base, StandardCharsets.ISO_8859_1);
        var marker = text.lastIndexOf("startxref\n") + "startxref\n".length();
        var previous = Integer.parseInt(text.substring(marker, text.indexOf('\n', marker)));
        var output = new ByteArrayOutputStream();
        output.writeBytes(base);
        var xrefStream = output.size();
        write(output, "14 0 obj\n<< /Type /XRef /Size 15 /Root 1 0 R /Prev " + previous
            + " /W [1 4 1] /Index [14 1] /Length 6 >>\nstream\n");
        output.write(1);
        output.write((xrefStream >>> 24) & 0xff);
        output.write((xrefStream >>> 16) & 0xff);
        output.write((xrefStream >>> 8) & 0xff);
        output.write(xrefStream & 0xff);
        output.write(0);
        write(output, "\nendstream\nendobj\n");
        var xref = output.size();
        write(output, "xref\n14 1\n%010d 00000 n \n".formatted(xrefStream));
        write(output, "trailer\n<< /Size 15 /Root 1 0 R /Prev " + previous
            + " /XRefStm " + xrefStream + " >>\nstartxref\n" + xref + "\n%%EOF\n");
        return output.toByteArray();
    }

    private static String stream(String value) {
        var length = value.getBytes(StandardCharsets.ISO_8859_1).length;
        return "<< /Length " + length + " >>\nstream\n" + value + "\nendstream";
    }

    private static void write(ByteArrayOutputStream output, String value) {
        output.writeBytes(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static int indexOf(byte[] bytes, byte[] pattern) {
        outer:
        for (int offset = 0; offset <= bytes.length - pattern.length; offset++) {
            for (int index = 0; index < pattern.length; index++) {
                if (bytes[offset + index] != pattern[index]) {
                    continue outer;
                }
            }
            return offset;
        }
        throw new AssertionError("fixture marker missing: " + HexFormat.of().formatHex(pattern));
    }
}
