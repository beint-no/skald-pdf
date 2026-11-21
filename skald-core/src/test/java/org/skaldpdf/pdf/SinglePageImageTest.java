package org.skaldpdf.pdf;

import org.junit.jupiter.api.Test;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.geom.Rectangle;
import org.skaldpdf.image.ImageData;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SinglePageImageTest {

    @Test
    void detectsPureSinglePageJpegAndLosslessScans() throws Exception {
        var jpeg = jpeg(1200, 1600, new Color(40, 100, 170));
        var rgb = ImageData.fromRgb(800, 1000, new byte[800 * 1000 * 3]);

        for (var image : new ImageData[] {jpeg, rgb}) {
            var bytes = singlePagePdf(image);
            try (var doc = new PdfDocument(new PdfReader(bytes))) {
                var detected = doc.singlePageImage();
                assertTrue(detected.isPresent());
                assertEquals(1, detected.get().exifOrientation());
                assertEquals(image.width(), detected.get().image().width());
                assertEquals(image.height(), detected.get().image().height());
            }
        }
    }

    @Test
    void detectsOpenPdfEmptyTextBlockFullClipAndVerticalFlipOrientation() throws Exception {
        var jpeg = jpeg(1200, 1600, new Color(30, 80, 120));
        var openPdfStream = "q\nBT\n36 806 Td\nET\nQ\nq 595 0 0 842 0 0 cm /Im1 Do Q";
        try (var doc = new PdfDocument(new PdfReader(rawSinglePagePdf(jpeg.samples(), 1200, 1600, openPdfStream, 0, "")))) {
            var detected = doc.singlePageImage();
            assertTrue(detected.isPresent());
            assertEquals(1, detected.get().exifOrientation());
        }

        var clippedStream = "q 0 0 595 842 re h W n /Perceptual ri q 595 0 0 842 0 0 cm /Im1 Do Q Q";
        try (var doc = new PdfDocument(new PdfReader(rawSinglePagePdf(jpeg.samples(), 1200, 1600, clippedStream, 0, "")))) {
            var detected = doc.singlePageImage();
            assertTrue(detected.isPresent());
            assertEquals(1, detected.get().exifOrientation());
        }

        var flippedStream = "q 1 0 0 -1 0 842 cm q 595 0 0 842 0 0 cm /Im1 Do Q Q";
        try (var doc = new PdfDocument(new PdfReader(rawSinglePagePdf(jpeg.samples(), 1200, 1600, flippedStream, 0, "")))) {
            var detected = doc.singlePageImage();
            assertTrue(detected.isPresent());
            assertEquals(4, detected.get().exifOrientation());
        }

        var rotated90Stream = "q 595 0 0 842 0 0 cm /Im1 Do Q";
        try (var doc = new PdfDocument(new PdfReader(rawSinglePagePdf(jpeg.samples(), 1200, 1600, rotated90Stream, 90, "")))) {
            var detected = doc.singlePageImage();
            assertTrue(detected.isPresent());
            assertEquals(6, detected.get().exifOrientation());
        }
    }

    @Test
    void computesAllEightExifOrientationsAndRejectsSkewedMatrices() {
        assertEquals(1, SinglePageImageAnalysis.exifOrientation(new double[] {595, 0, 0, 842, 0, 0}, 0));
        assertEquals(2, SinglePageImageAnalysis.exifOrientation(new double[] {-595, 0, 0, 842, 595, 0}, 0));
        assertEquals(3, SinglePageImageAnalysis.exifOrientation(new double[] {-595, 0, 0, -842, 595, 842}, 0));
        assertEquals(4, SinglePageImageAnalysis.exifOrientation(new double[] {595, 0, 0, -842, 0, 842}, 0));
        assertEquals(5, SinglePageImageAnalysis.exifOrientation(new double[] {0, -595, -842, 0, 842, 595}, 0));
        assertEquals(6, SinglePageImageAnalysis.exifOrientation(new double[] {595, 0, 0, 842, 0, 0}, 90));
        assertEquals(7, SinglePageImageAnalysis.exifOrientation(new double[] {0, 595, 842, 0, 0, 0}, 0));
        assertEquals(8, SinglePageImageAnalysis.exifOrientation(new double[] {595, 0, 0, 842, 0, 0}, 270));
        assertEquals(0, SinglePageImageAnalysis.exifOrientation(new double[] {400, 400, -400, 400, 0, 0}, 0));
    }

    @Test
    void rejectsMultiPageTextVectorPathsCroppingAnnotationsOutlinesAndForms() throws Exception {
        var jpeg = jpeg(1200, 1600, new Color(50, 90, 140));

        // 1. Multi-page PDF
        var multiPage = new ByteArrayOutputStream();
        try (var doc = new PdfDocument(new PdfWriter(multiPage))) {
            jpeg.drawOn(doc, doc.addNewPage(PageSize.A4), 0, 0, PageSize.A4.getWidth(), PageSize.A4.getHeight());
            jpeg.drawOn(doc, doc.addNewPage(PageSize.A4), 0, 0, PageSize.A4.getWidth(), PageSize.A4.getHeight());
        }
        try (var doc = new PdfDocument(new PdfReader(multiPage.toByteArray()))) {
            assertTrue(doc.singlePageImage().isEmpty());
        }

        // 2. Single-page image with text drawn on top
        var textStream = "q 595 0 0 842 0 0 cm /Im1 Do Q BT /F1 12 Tf 36 700 Td (Invoice) Tj ET";
        try (var doc = new PdfDocument(new PdfReader(rawSinglePagePdf(jpeg.samples(), 1200, 1600, textStream, 0, "")))) {
            assertTrue(doc.singlePageImage().isEmpty());
        }

        // 3. Single-page image with vector path painting (e.g. Print-to-PDF outlined text)
        var vectorStream = "0 0 0 rg 10 10 100 20 re f q 595 0 0 842 0 0 cm /Im1 Do Q";
        try (var doc = new PdfDocument(new PdfReader(rawSinglePagePdf(jpeg.samples(), 1200, 1600, vectorStream, 0, "")))) {
            assertTrue(doc.singlePageImage().isEmpty());
        }

        // 4. Cropping clip path smaller than the image
        var croppedStream = "q 0 0 300 400 re W n q 595 0 0 842 0 0 cm /Im1 Do Q Q";
        try (var doc = new PdfDocument(new PdfReader(rawSinglePagePdf(jpeg.samples(), 1200, 1600, croppedStream, 0, "")))) {
            assertTrue(doc.singlePageImage().isEmpty());
        }

        // 5. Link annotation
        var withLink = new ByteArrayOutputStream();
        try (var doc = new PdfDocument(new PdfWriter(withLink))) {
            var page = doc.addNewPage(PageSize.A4);
            jpeg.drawOn(doc, page, 0, 0, PageSize.A4.getWidth(), PageSize.A4.getHeight());
            page.addUriLink(new Rectangle(10, 10, 100, 20), "https://example.com");
        }
        try (var doc = new PdfDocument(new PdfReader(withLink.toByteArray()))) {
            assertTrue(doc.singlePageImage().isEmpty());
        }

        // 6. Document outline
        var withOutline = new ByteArrayOutputStream();
        try (var doc = new PdfDocument(new PdfWriter(withOutline))) {
            var page = doc.addNewPage(PageSize.A4);
            jpeg.drawOn(doc, page, 0, 0, PageSize.A4.getWidth(), PageSize.A4.getHeight());
            doc.addOutline("Cover", 1);
        }
        try (var doc = new PdfDocument(new PdfReader(withOutline.toByteArray()))) {
            assertTrue(doc.singlePageImage().isEmpty());
        }

        // 7. Associated embedded file (/AF)
        try (var doc = new PdfDocument(new PdfReader(
            rawSinglePagePdf(jpeg.samples(), 1200, 1600, "q 595 0 0 842 0 0 cm /Im1 Do Q", 0, "/AF [ ]")))) {
            assertTrue(doc.singlePageImage().isEmpty());
        }
    }

    @Test
    void parsesScannerQuirksWithZeroPrevAndOffByOneClassicXref() throws Exception {
        var jpeg = jpeg(1200, 1600, new Color(80, 110, 60));
        var stream = "q 595 0 0 842 0 0 cm /Im1 Do Q";
        var withPrevZero = rawSinglePagePdf(jpeg.samples(), 1200, 1600, stream, 0, "", true, false);
        try (var doc = new PdfDocument(new PdfReader(withPrevZero))) {
            assertTrue(doc.singlePageImage().isPresent());
        }

        var withOffByOneXref = rawSinglePagePdf(jpeg.samples(), 1200, 1600, stream, 0, "", false, true);
        try (var doc = new PdfDocument(new PdfReader(withOffByOneXref))) {
            assertTrue(doc.singlePageImage().isPresent());
        }
    }

    private static byte[] singlePagePdf(ImageData image) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var doc = new PdfDocument(new PdfWriter(out))) {
            var page = doc.addNewPage(PageSize.A4);
            image.drawOn(doc, page, 0, 0, PageSize.A4.getWidth(), PageSize.A4.getHeight());
        }
        return out.toByteArray();
    }

    private static byte[] rawSinglePagePdf(
        byte[] jpegBytes, int width, int height, String contentStream, int rotate, String extraCatalogEntries
    ) throws Exception {
        return rawSinglePagePdf(jpegBytes, width, height, contentStream, rotate, extraCatalogEntries, false, false);
    }

    private static byte[] rawSinglePagePdf(
        byte[] jpegBytes, int width, int height, String contentStream, int rotate, String extraCatalogEntries,
        boolean prevZero, boolean offByOneXref
    ) throws Exception {
        var out = new ByteArrayOutputStream();
        out.write("%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1));
        int[] offsets = new int[6];

        offsets[1] = out.size();
        out.write(("1 0 obj\n<< /Type /Catalog /Pages 2 0 R " + extraCatalogEntries + " >>\nendobj\n")
            .getBytes(StandardCharsets.ISO_8859_1));

        offsets[2] = out.size();
        out.write("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
            .getBytes(StandardCharsets.ISO_8859_1));

        offsets[3] = out.size();
        out.write(("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Rotate " + rotate
            + " /Resources << /XObject << /Im1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n")
            .getBytes(StandardCharsets.ISO_8859_1));

        offsets[4] = out.size();
        out.write(("4 0 obj\n<< /Type /XObject /Subtype /Image /Width " + width + " /Height " + height
            + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + jpegBytes.length
            + " >>\nstream\n").getBytes(StandardCharsets.ISO_8859_1));
        out.write(jpegBytes);
        out.write("\nendstream\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

        var contentBytes = contentStream.getBytes(StandardCharsets.ISO_8859_1);
        offsets[5] = out.size();
        out.write(("5 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n")
            .getBytes(StandardCharsets.ISO_8859_1));
        out.write(contentBytes);
        out.write("\nendstream\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));

        int xrefOffset = out.size();
        out.write(("xref\n" + (offByOneXref ? "1 6\n" : "0 6\n")).getBytes(StandardCharsets.ISO_8859_1));
        out.write("0000000000 65535 f \n".getBytes(StandardCharsets.ISO_8859_1));
        for (int i = 1; i <= 5; i++) {
            out.write(String.format("%010d 00000 n \n", offsets[i]).getBytes(StandardCharsets.ISO_8859_1));
        }
        out.write(("trailer\n<< /Size 6 /Root 1 0 R" + (prevZero ? " /Prev 0" : "")
            + " >>\nstartxref\n" + xrefOffset + "\n%%EOF\n").getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    private static ImageData jpeg(int width, int height, Color color) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", out);
        return ImageData.fromJpeg(out.toByteArray());
    }
}
