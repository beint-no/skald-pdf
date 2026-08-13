package org.skaldpdf.pdf;

import static org.skaldpdf.pdf.CosValue.*;

import org.skaldpdf.font.PdfFont;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** Writes the compact PDF 2.0 subset emitted by Skald. */
final class NativePdfWriter {
    private static final byte[] HEADER = "%PDF-2.0\n%\u00e2\u00e3\u00cf\u00d3\n"
        .getBytes(StandardCharsets.ISO_8859_1);
    private final int compressionLevel;

    NativePdfWriter(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    void write(PdfDocument document, OutputStream target) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(target, "target");
        try {
            writeObjects(buildObjects(document), target);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write PDF", exception);
        }
    }

    private ObjectStore buildObjects(PdfDocument document) {
        var objects = new ObjectStore();
        var fontUsage = collectFonts(document.pages());
        var fontObjects = new IdentityHashMap<PdfFont, Integer>();
        fontUsage.forEach((font, usage) -> fontObjects.put(font, addFont(objects, font, usage)));
        var sharedImages = new IdentityHashMap<org.skaldpdf.image.ImageData, Integer>();
        var sharedOpacities = new LinkedHashMap<Float, Integer>();

        var pagesObject = objects.reserve();
        var pageObjects = new ArrayList<Integer>(document.pages().size());
        document.pages().forEach(ignored -> pageObjects.add(objects.reserve()));
        var importers = new IdentityHashMap<NativePdfParser, ImportContext>();
        for (int index = 0; index < document.pages().size(); index++) {
            var imported = document.pages().get(index).importedPage();
            if (imported != null) {
                importers.computeIfAbsent(imported.source(), source -> new ImportContext(source, objects))
                    .map(imported.reference(), pageObjects.get(index));
            }
        }

        for (int index = 0; index < document.pages().size(); index++) {
            var page = document.pages().get(index);
            var contentObject = objects.add(stream("", ascii(page.content()), true));
            var imageObjects = addImages(objects, page.images(), sharedImages);
            var opacityObjects = addOpacities(objects, page.opacities(), sharedOpacities);
            var shadingObjects = addShadings(objects, page.shadings());
            var linkObjects = addLinks(objects, page.links(), pageObjects, document.pages());
            var imported = page.importedPage();
            byte[] pageBody;
            if (imported == null) {
                var resources = resources(page, fontObjects, imageObjects, opacityObjects, shadingObjects);
                var media = rectangle(0, 0, page.getPageSize().getWidth(), page.getPageSize().getHeight());
                var crop = rectangle(page.getCropBox().getLeft(), page.getCropBox().getBottom(),
                    page.getCropBox().getWidth(), page.getCropBox().getHeight());
                var body = new StringBuilder("<< /Type /Page /Parent ").append(pagesObject)
                    .append(" 0 R /MediaBox ").append(media)
                    .append(" /CropBox ").append(crop)
                    .append(" /Resources ").append(resources)
                    .append(" /Contents ").append(contentObject).append(" 0 R");
                appendAnnots(body, linkObjects);
                pageBody = ascii(body.append(" >>").toString());
            } else {
                pageBody = importedPage(imported, page, pagesObject, contentObject, fontObjects,
                    imageObjects, opacityObjects, shadingObjects, linkObjects, importers.get(imported.source()));
            }
            objects.set(pageObjects.get(index), pageBody);
        }
        objects.set(pagesObject, ascii(format("<< /Type /Pages /Count %d /Kids [%s] >>",
            pageObjects.size(), references(pageObjects))));

        var metadataObject = objects.add(stream("/Type /Metadata /Subtype /XML",
            xmp(document.getDocumentInfo(), document.language()).getBytes(StandardCharsets.UTF_8), false));
        var catalog = new StringBuilder("<< /Type /Catalog /Version /2.0 /Pages ")
            .append(pagesObject).append(" 0 R /Metadata ").append(metadataObject)
            .append(" 0 R /ViewerPreferences << /DisplayDocTitle true >>");
        if (document.language() != null) {
            catalog.append(" /Lang ").append(literalString(document.language()));
        }
        if (!document.outlines().isEmpty()) {
            catalog.append(" /Outlines ")
                .append(addOutlines(objects, document, pageObjects)).append(" 0 R");
        }
        var catalogObject = objects.add(ascii(catalog.append(" >>").toString()));
        objects.rootObject = catalogObject;
        return objects;
    }

    private static byte[] importedPage(ImportedPage imported, PdfPage page, int pagesObject,
                                       int overlayContentObject, Map<PdfFont, Integer> fonts,
                                       Map<String, Integer> images, Map<String, Integer> opacities,
                                       Map<String, Integer> shadings, List<Integer> links,
                                       ImportContext importer) {
        var dictionary = imported.dictionary();
        var result = new StringBuilder("<< /Type /Page /Parent ").append(pagesObject).append(" 0 R");
        dictionary.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (!Set.of("Type", "Parent", "MediaBox", "CropBox", "Resources", "Contents",
                    "StructParents", "Annots", "AA", "JS", "PresSteps")
                .contains(entry.getKey())) {
                result.append(" /").append(name(entry.getKey())).append(' ')
                    .append(importer.direct(entry.getValue()));
            }
        });
        var media = dictionary.get("MediaBox");
        result.append(" /MediaBox ").append(media == null
            ? rectangle(0, 0, page.getPageSize().getWidth(), page.getPageSize().getHeight())
            : importer.direct(media));
        var crop = dictionary.get("CropBox");
        if (crop != null) {
            result.append(" /CropBox ").append(importer.direct(crop));
        }
        result.append(" /Resources ")
            .append(importedResources(imported, page, fonts, images, opacities, shadings, importer));
        var rawContents = dictionary.get("Contents");
        var contents = importer.resolve(rawContents);
        if (page.content().isBlank()) {
            if (rawContents != null) {
                result.append(" /Contents ").append(importer.direct(rawContents));
            }
        } else if (contents instanceof CosArray array) {
            result.append(" /Contents [");
            array.values().forEach(value -> result.append(importer.direct(value)).append(' '));
            result.append(overlayContentObject).append(" 0 R]");
        } else if (rawContents != null) {
            result.append(" /Contents [").append(importer.direct(rawContents)).append(' ')
                .append(overlayContentObject).append(" 0 R]");
        } else {
            result.append(" /Contents ").append(overlayContentObject).append(" 0 R");
        }
        var existingAnnots = importer.resolve(dictionary.get("Annots"));
        if (existingAnnots != null || !links.isEmpty()) {
            result.append(" /Annots [");
            if (existingAnnots instanceof CosArray array) {
                array.values().forEach(value -> {
                    var sanitized = importer.sanitizedAnnotation(value);
                    if (sanitized != null) {
                        result.append(sanitized).append(' ');
                    }
                });
            } else if (existingAnnots != null) {
                var sanitized = importer.sanitizedAnnotation(existingAnnots);
                if (sanitized != null) {
                    result.append(sanitized).append(' ');
                }
            }
            links.forEach(object -> result.append(object).append(" 0 R "));
            result.append(']');
        }
        return ascii(result.append(" >>").toString());
    }

    private static String importedResources(ImportedPage imported, PdfPage page, Map<PdfFont, Integer> fonts,
                                            Map<String, Integer> images, Map<String, Integer> opacities,
                                            Map<String, Integer> shadings, ImportContext importer) {
        var resourcesValue = imported.dictionary().get("Resources");
        var resources = resourcesValue == null
            ? Map.<String, CosValue>of()
            : importer.dictionary(resourcesValue, "page Resources").values();
        var result = new StringBuilder("<<");
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (!Set.of("Font", "XObject", "ExtGState", "Shading").contains(entry.getKey())) {
                result.append(" /").append(name(entry.getKey())).append(' ')
                    .append(importer.direct(entry.getValue()));
            }
        });
        var fontAdditions = new LinkedHashMap<String, Integer>();
        page.fonts().forEach((font, resourceName) -> fontAdditions.put(resourceName, fonts.get(font)));
        appendImportedResourceCategory(result, "Font", resources.get("Font"), fontAdditions, importer);
        appendImportedResourceCategory(result, "XObject", resources.get("XObject"), images, importer);
        appendImportedResourceCategory(result, "ExtGState", resources.get("ExtGState"), opacities, importer);
        appendImportedResourceCategory(result, "Shading", resources.get("Shading"), shadings, importer);
        return result.append(" >>").toString();
    }

    private static void appendImportedResourceCategory(StringBuilder result, String category,
                                                       CosValue existingValue, Map<String, Integer> additions,
                                                       ImportContext importer) {
        if (existingValue == null && additions.isEmpty()) {
            return;
        }
        result.append(" /").append(category).append(" <<");
        if (existingValue != null) {
            var existing = importer.dictionary(existingValue, category + " resources");
            existing.values().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                result.append(" /").append(name(entry.getKey())).append(' ')
                    .append(importer.direct(entry.getValue())));
        }
        additions.forEach((resourceName, object) -> result.append(" /").append(resourceName).append(' ')
            .append(object).append(" 0 R"));
        result.append(" >>");
    }

    private int addFont(ObjectStore objects, PdfFont font, FontAggregate usage) {
        var program = font.subsetProgram(usage.glyphs);
        var fontFile = objects.add(stream("/Length1 " + program.length, program, true));
        var metrics = font.metrics();
        var postScriptName = subsetTag(font, usage.glyphs) + "+" + pdfFontName(font);
        var flags = 32 | (metrics.fixedPitch() ? 1 : 0)
            | (metrics.italicAngle() != 0 ? 64 : 0)
            | (font.bold() ? 262_144 : 0);
        var descriptor = objects.add(ascii(format(
            "<< /Type /FontDescriptor /FontName /%s /Flags %d /FontBBox [%d %d %d %d] "
                + "/ItalicAngle %s /Ascent %d /Descent %d /CapHeight %d /StemV %d /FontFile2 %d 0 R >>",
            postScriptName, flags,
            metrics.pdfUnit(metrics.xMin()), metrics.pdfUnit(metrics.yMin()),
            metrics.pdfUnit(metrics.xMax()), metrics.pdfUnit(metrics.yMax()),
            number(metrics.italicAngle()), metrics.pdfUnit(metrics.ascent()),
            metrics.pdfUnit(metrics.descent()), metrics.pdfUnit(metrics.capHeight()),
            font.bold() ? 120 : 80, fontFile)));
        var widths = widths(font.widths(usage.glyphs));
        var cidFont = objects.add(ascii(format(
            "<< /Type /Font /Subtype /CIDFontType2 /BaseFont /%s "
                + "/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> "
                + "/FontDescriptor %d 0 R /CIDToGIDMap /Identity /DW 1000 /W %s >>",
            postScriptName, descriptor, widths)));
        var toUnicode = objects.add(stream("", toUnicode(usage.unicodeByGlyph), true));
        return objects.add(ascii(format(
            "<< /Type /Font /Subtype /Type0 /BaseFont /%s /Encoding /Identity-H "
                + "/DescendantFonts [%d 0 R] /ToUnicode %d 0 R >>",
            postScriptName, cidFont, toUnicode)));
    }

    private Map<String, Integer> addImages(ObjectStore objects, List<PdfPage.ImageResource> images,
                                           IdentityHashMap<org.skaldpdf.image.ImageData, Integer> shared) {
        var result = new LinkedHashMap<String, Integer>();
        for (var resource : images) {
            var image = resource.image();
            var existing = shared.get(image);
            if (existing != null) {
                result.put(resource.name(), existing);
                continue;
            }
            var alpha = image.alpha();
            var softMask = alpha == null ? null : objects.add(stream(
                format("/Type /XObject /Subtype /Image /Width %d /Height %d /ColorSpace /DeviceGray "
                    + "/BitsPerComponent 8 /DecodeParms << /Predictor 15 /Colors 1 /BitsPerComponent 8 /Columns %d >>",
                    image.width(), image.height(), image.width()),
                pngPredictor(alpha, image.width(), image.height(), 1), true));
            var dictionary = new StringBuilder("/Type /XObject /Subtype /Image")
                .append(" /Width ").append(image.width())
                .append(" /Height ").append(image.height())
                .append(" /ColorSpace ").append(image.components() == 1 ? "/DeviceGray" : "/DeviceRGB")
                .append(" /BitsPerComponent 8");
            if (softMask != null) {
                dictionary.append(" /SMask ").append(softMask).append(" 0 R");
            }
            if (image.jpeg()) {
                result.put(resource.name(), objects.add(stream(
                    dictionary.toString(), image.samples(), false, "/DCTDecode"
                )));
            } else {
                dictionary.append(" /DecodeParms << /Predictor 15 /Colors ").append(image.components())
                    .append(" /BitsPerComponent 8 /Columns ").append(image.width()).append(" >>");
                result.put(resource.name(), objects.add(stream(dictionary.toString(),
                    pngPredictor(image.samples(), image.width(), image.height(), image.components()), true)));
            }
            shared.put(image, result.get(resource.name()));
        }
        return result;
    }

    private static byte[] pngPredictor(byte[] samples, int width, int height, int components) {
        var rowBytes = Math.multiplyExact(width, components);
        if (samples.length != Math.multiplyExact(rowBytes, height)) {
            throw new IllegalArgumentException("Raster sample length does not match its dimensions");
        }
        var result = new byte[Math.addExact(samples.length, height)];
        var candidates = new byte[5][rowBytes];
        for (int row = 0; row < height; row++) {
            var sourceOffset = row * rowBytes;
            var bestFilter = 0;
            var bestScore = Long.MAX_VALUE;
            for (int filter = 0; filter <= 4; filter++) {
                var score = 0L;
                var candidate = candidates[filter];
                for (int column = 0; column < rowBytes; column++) {
                    var current = samples[sourceOffset + column] & 0xff;
                    var left = column >= components ? samples[sourceOffset + column - components] & 0xff : 0;
                    var up = row > 0 ? samples[sourceOffset - rowBytes + column] & 0xff : 0;
                    var upperLeft = row > 0 && column >= components
                        ? samples[sourceOffset - rowBytes + column - components] & 0xff : 0;
                    var prediction = switch (filter) {
                        case 0 -> 0;
                        case 1 -> left;
                        case 2 -> up;
                        case 3 -> (left + up) >>> 1;
                        case 4 -> paeth(left, up, upperLeft);
                        default -> throw new AssertionError();
                    };
                    candidate[column] = (byte) (current - prediction);
                    score += Math.abs((int) candidate[column]);
                }
                if (score < bestScore) {
                    bestScore = score;
                    bestFilter = filter;
                }
            }
            var targetOffset = row * (rowBytes + 1);
            result[targetOffset] = (byte) bestFilter;
            System.arraycopy(candidates[bestFilter], 0, result, targetOffset + 1, rowBytes);
        }
        return result;
    }

    private static int paeth(int left, int up, int upperLeft) {
        var prediction = left + up - upperLeft;
        var leftDistance = Math.abs(prediction - left);
        var upDistance = Math.abs(prediction - up);
        var upperLeftDistance = Math.abs(prediction - upperLeft);
        if (leftDistance <= upDistance && leftDistance <= upperLeftDistance) {
            return left;
        }
        return upDistance <= upperLeftDistance ? up : upperLeft;
    }

    private static Map<String, Integer> addOpacities(ObjectStore objects, Map<Float, String> opacities,
                                                     Map<Float, Integer> shared) {
        var result = new LinkedHashMap<String, Integer>();
        opacities.forEach((opacity, resourceName) -> result.put(resourceName,
            shared.computeIfAbsent(opacity, value -> objects.add(ascii(
                format("<< /Type /ExtGState /ca %s /CA %s >>", number(value), number(value)))))));
        return result;
    }

    private static String resources(PdfPage page, Map<PdfFont, Integer> fonts,
                                    Map<String, Integer> images, Map<String, Integer> opacities,
                                    Map<String, Integer> shadings) {
        var result = new StringBuilder("<<");
        if (!page.fonts().isEmpty()) {
            result.append(" /Font <<");
            page.fonts().forEach((font, resourceName) -> result.append(" /").append(resourceName).append(' ')
                .append(fonts.get(font)).append(" 0 R"));
            result.append(" >>");
        }
        appendResourceMap(result, "XObject", images);
        appendResourceMap(result, "ExtGState", opacities);
        appendResourceMap(result, "Shading", shadings);
        return result.append(" >>").toString();
    }

    private static Map<String, Integer> addShadings(ObjectStore objects, List<PdfPage.ShadingResource> shadings) {
        var result = new LinkedHashMap<String, Integer>();
        for (var shading : shadings) {
            var function = objects.add(ascii(format(
                "<< /FunctionType 2 /Domain [0 1] /C0 [%s %s %s] /C1 [%s %s %s] /N 1 >>",
                number(shading.start().red()), number(shading.start().green()), number(shading.start().blue()),
                number(shading.end().red()), number(shading.end().green()), number(shading.end().blue()))));
            result.put(shading.name(), objects.add(ascii(format(
                "<< /ShadingType 2 /ColorSpace /DeviceRGB /Coords [%s %s %s %s] /Function %d 0 R /Extend [true true] >>",
                number(shading.x0()), number(shading.y0()), number(shading.x1()), number(shading.y1()), function))));
        }
        return result;
    }

    private static List<Integer> addLinks(ObjectStore objects, List<PdfPage.LinkAnnotation> links,
                                          List<Integer> pageObjects, List<PdfPage> pages) {
        var result = new ArrayList<Integer>(links.size());
        for (var link : links) {
            var bounds = link.bounds();
            var action = link.uri() != null
                ? "<< /Type /Action /S /URI /URI " + literalString(link.uri()) + " >>"
                : goToAction(link.destinationPage(), pageObjects, pages);
            result.add(objects.add(ascii(format(
                "<< /Type /Annot /Subtype /Link /Rect [%s %s %s %s] /Border [0 0 0] /F 4 /H /N /A %s >>",
                number(bounds.getLeft()), number(bounds.getBottom()),
                number(bounds.getRight()), number(bounds.getTop()),
                action))));
        }
        return result;
    }

    private static String goToAction(int pageNumber, List<Integer> pageObjects, List<PdfPage> pages) {
        if (pageNumber < 1 || pageNumber > pageObjects.size()) {
            throw new IllegalArgumentException("Link targets a page that does not exist: " + pageNumber);
        }
        return format("<< /Type /Action /S /GoTo /D [%d 0 R /XYZ null %s null] >>",
            pageObjects.get(pageNumber - 1), number(pages.get(pageNumber - 1).getPageSize().getHeight()));
    }

    private static void appendAnnots(StringBuilder body, List<Integer> links) {
        if (links.isEmpty()) {
            return;
        }
        body.append(" /Annots [");
        links.forEach(object -> body.append(object).append(" 0 R "));
        body.append(']');
    }

    private static int addOutlines(ObjectStore objects, PdfDocument document, List<Integer> pageObjects) {
        var items = document.outlines();
        var root = objects.reserve();
        var itemObjects = new ArrayList<Integer>(items.size());
        items.forEach(ignored -> itemObjects.add(objects.reserve()));
        for (int index = 0; index < items.size(); index++) {
            var item = items.get(index);
            if (item.pageNumber() > pageObjects.size()) {
                throw new IllegalArgumentException("Outline targets a page that does not exist: " + item.pageNumber());
            }
            var page = document.pages().get(item.pageNumber() - 1);
            var body = new StringBuilder("<< /Title ").append(textString(item.title()))
                .append(" /Parent ").append(root).append(" 0 R");
            if (index > 0) {
                body.append(" /Prev ").append(itemObjects.get(index - 1)).append(" 0 R");
            }
            if (index + 1 < items.size()) {
                body.append(" /Next ").append(itemObjects.get(index + 1)).append(" 0 R");
            }
            body.append(" /Dest [").append(pageObjects.get(item.pageNumber() - 1)).append(" 0 R /XYZ null ")
                .append(number(page.getPageSize().getHeight())).append(" null] >>");
            objects.set(itemObjects.get(index), ascii(body.toString()));
        }
        objects.set(root, ascii(format("<< /Type /Outlines /First %d 0 R /Last %d 0 R /Count %d >>",
            itemObjects.getFirst(), itemObjects.getLast(), itemObjects.size())));
        return root;
    }

    private static void appendResourceMap(StringBuilder target, String type, Map<String, Integer> values) {
        if (values.isEmpty()) {
            return;
        }
        target.append(" /").append(type).append(" <<");
        values.forEach((name, object) -> target.append(" /").append(name).append(' ')
            .append(object).append(" 0 R"));
        target.append(" >>");
    }

    private void writeObjects(ObjectStore objects, OutputStream output) throws IOException {
        var packed = packSmallObjects(objects);
        var target = new CountingOutputStream(output);
        target.write(HEADER);
        var offsets = new long[objects.size() + 2];
        for (int number = 1; number <= objects.size(); number++) {
            if (packed.containsKey(number)) {
                continue;
            }
            offsets[number] = target.count();
            writeIndirect(target, number, objects.get(number));
        }
        var xrefNumber = objects.size() + 1;
        var xrefOffset = target.count();
        offsets[xrefNumber] = xrefOffset;
        var compressedXref = deflate(xref(offsets, packed, xrefNumber));
        var identifier = fileIdentifier(objects);
        var xrefDictionary = format(
            "/Type /XRef /Size %d /Root %d 0 R /ID [<%s> <%s>] /W [1 8 4] /Index [0 %d] ",
            xrefNumber + 1, objects.rootObject, identifier, identifier, xrefNumber + 1);
        writeIndirect(target, xrefNumber, stream(xrefDictionary, compressedXref, false, "/FlateDecode"));
        target.write(ascii(format("startxref\n%d\n%%%%EOF\n", xrefOffset)));
        target.flush();
    }

    private Map<Integer, PackedLocation> packSmallObjects(ObjectStore objects) {
        var candidates = new ArrayList<Integer>();
        for (int number = 1; number <= objects.size(); number++) {
            var body = objects.get(number);
            if (number != objects.rootObject && body.length <= 4_096 && !isStream(body)) {
                candidates.add(number);
            }
        }
        var result = new LinkedHashMap<Integer, PackedLocation>();
        for (int offset = 0; offset < candidates.size(); offset += 100) {
            var group = candidates.subList(offset, Math.min(offset + 100, candidates.size()));
            var header = new StringBuilder(group.size() * 10);
            var bodies = new ByteArrayOutputStream();
            for (int index = 0; index < group.size(); index++) {
                var objectNumber = group.get(index);
                header.append(objectNumber).append(' ').append(bodies.size()).append(' ');
                try {
                    bodies.write(objects.get(objectNumber));
                    bodies.write('\n');
                } catch (IOException impossible) {
                    throw new AssertionError(impossible);
                }
            }
            var headerBytes = ascii(header.toString());
            var payload = new ByteArrayOutputStream(headerBytes.length + bodies.size());
            try {
                payload.write(headerBytes);
                bodies.writeTo(payload);
            } catch (IOException impossible) {
                throw new AssertionError(impossible);
            }
            var objectStream = objects.add(stream(
                format("/Type /ObjStm /N %d /First %d", group.size(), headerBytes.length),
                payload.toByteArray(), true
            ));
            for (int index = 0; index < group.size(); index++) {
                result.put(group.get(index), new PackedLocation(objectStream, index));
            }
        }
        return Map.copyOf(result);
    }

    private static boolean isStream(byte[] body) {
        var marker = "\nstream\n".getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int offset = 0; offset <= body.length - marker.length; offset++) {
            for (int index = 0; index < marker.length; index++) {
                if (body[offset + index] != marker[index]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private byte[] stream(String dictionary, byte[] bytes, boolean compress) {
        return stream(dictionary, bytes, compress, null);
    }

    private byte[] stream(String dictionary, byte[] bytes, boolean compress, String explicitFilter) {
        var payload = compress ? deflate(bytes) : bytes;
        var filter = compress ? "/FlateDecode" : explicitFilter;
        var header = new StringBuilder("<< ").append(dictionary);
        if (filter != null) {
            header.append(" /Filter ").append(filter);
        }
        header.append(" /Length ").append(payload.length).append(" >>\nstream\n");
        var result = new ByteArrayOutputStream(header.length() + payload.length + 16);
        try {
            result.write(ascii(header.toString()));
            result.write(payload);
            result.write(ascii("\nendstream"));
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
        return result.toByteArray();
    }

    private byte[] deflate(byte[] bytes) {
        var output = new ByteArrayOutputStream(Math.max(64, bytes.length / 2));
        var deflater = new Deflater(compressionLevel);
        try (var compressed = new DeflaterOutputStream(output, deflater)) {
            compressed.write(bytes);
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        } finally {
            deflater.end();
        }
        return output.toByteArray();
    }

    private static void writeIndirect(OutputStream output, int number, byte[] body) throws IOException {
        output.write(ascii(number + " 0 obj\n"));
        output.write(body);
        output.write(ascii("\nendobj\n"));
    }

    private static byte[] xref(long[] offsets, Map<Integer, PackedLocation> packed, int lastObject) {
        var buffer = ByteBuffer.allocate((lastObject + 1) * 13);
        buffer.put((byte) 0).putLong(0).putInt(0xffff);
        for (int object = 1; object <= lastObject; object++) {
            var location = packed.get(object);
            if (location == null) {
                buffer.put((byte) 1).putLong(offsets[object]).putInt(0);
            } else {
                buffer.put((byte) 2).putLong(location.objectStream()).putInt(location.index());
            }
        }
        return buffer.array();
    }

    private static String fileIdentifier(ObjectStore objects) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(objects.rootObject).array());
            for (int number = 1; number <= objects.size(); number++) {
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(number).array());
                digest.update(objects.get(number));
            }
            return hex(digest.digest(), 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static Map<PdfFont, FontAggregate> collectFonts(List<PdfPage> pages) {
        var result = new IdentityHashMap<PdfFont, FontAggregate>();
        pages.forEach(page -> page.fontUsage().forEach((font, usage) -> {
            var aggregate = result.computeIfAbsent(font, ignored -> new FontAggregate());
            aggregate.glyphs.addAll(usage.glyphs());
            usage.unicodeByGlyph().forEach(aggregate.unicodeByGlyph::putIfAbsent);
        }));
        return result;
    }

    private static String widths(Map<Integer, Integer> values) {
        var entries = values.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        var result = new StringBuilder("[");
        var index = 0;
        while (index < entries.size()) {
            var startCid = entries.get(index).getKey();
            var width = entries.get(index).getValue();
            var sameWidthEnd = index + 1;
            while (sameWidthEnd < entries.size()
                && entries.get(sameWidthEnd).getKey() == entries.get(sameWidthEnd - 1).getKey() + 1
                && entries.get(sameWidthEnd).getValue().equals(width)) {
                sameWidthEnd++;
            }
            if (sameWidthEnd - index >= 3) {
                result.append(startCid).append(' ').append(entries.get(sameWidthEnd - 1).getKey())
                    .append(' ').append(width).append(' ');
                index = sameWidthEnd;
                continue;
            }
            var runEnd = index + 1;
            while (runEnd < entries.size()
                && entries.get(runEnd).getKey() == entries.get(runEnd - 1).getKey() + 1) {
                var nextSameWidth = 1;
                while (runEnd + nextSameWidth < entries.size()
                    && entries.get(runEnd + nextSameWidth).getKey()
                    == entries.get(runEnd + nextSameWidth - 1).getKey() + 1
                    && entries.get(runEnd + nextSameWidth).getValue()
                    .equals(entries.get(runEnd).getValue())) {
                    nextSameWidth++;
                }
                if (nextSameWidth >= 3) {
                    break;
                }
                runEnd++;
            }
            result.append(startCid).append(" [");
            for (int item = index; item < runEnd; item++) {
                result.append(entries.get(item).getValue()).append(' ');
            }
            result.append("] ");
            index = runEnd;
        }
        return result.append(']').toString();
    }

    private static String pdfFontName(PdfFont font) {
        var raw = font.postScriptName();
        if (raw == null || raw.isBlank()) {
            return font.bold() ? "SkaldSans-Bold" : "SkaldSans-Regular";
        }
        var result = new StringBuilder(raw.length());
        for (int index = 0; index < raw.length(); index++) {
            var character = raw.charAt(index);
            if (character > 32 && character < 127 && "()<>[]{}/%#".indexOf(character) < 0) {
                result.append(character);
            } else {
                result.append('-');
            }
        }
        return result.isEmpty() ? "Embedded" : result.toString();
    }

    private static String subsetTag(PdfFont font, Set<Integer> glyphs) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(font.postScriptName().getBytes(StandardCharsets.UTF_8));
            glyphs.stream().sorted().forEach(glyph ->
                digest.update(ByteBuffer.allocate(4).putInt(glyph).array()));
            var hash = digest.digest();
            var result = new StringBuilder(6);
            for (int index = 0; index < 6; index++) {
                result.append((char) ('A' + (hash[index] & 0xff) % 26));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static byte[] toUnicode(Map<Integer, Integer> mappings) {
        var entries = mappings.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        var cmap = new StringBuilder("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
            .append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n")
            .append("/CMapName /SkaldUnicode def\n/CMapType 2 def\n")
            .append("1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n");
        for (int offset = 0; offset < entries.size(); offset += 100) {
            var count = Math.min(100, entries.size() - offset);
            cmap.append(count).append(" beginbfchar\n");
            for (int index = offset; index < offset + count; index++) {
                var entry = entries.get(index);
                cmap.append('<').append(hex(entry.getKey(), 4)).append("> <")
                    .append(utf16Hex(entry.getValue())).append(">\n");
            }
            cmap.append("endbfchar\n");
        }
        return ascii(cmap.append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n").toString());
    }

    private static String utf16Hex(int codePoint) {
        var characters = Character.toChars(codePoint);
        var result = new StringBuilder(characters.length * 4);
        for (var character : characters) {
            result.append(hex(character, 4));
        }
        return result.toString();
    }

    private static String xmp(PdfDocumentInfo information, String language) {
        var title = xml(information.getTitle() == null ? "" : information.getTitle());
        var author = xml(information.getAuthor() == null ? "" : information.getAuthor());
        var subject = information.getSubject() == null ? "" : """
                  <dc:description><rdf:Alt><rdf:li xml:lang="x-default">%s</rdf:li></rdf:Alt></dc:description>
            """.formatted(xml(information.getSubject()));
        var keywords = information.getKeywords() == null ? "" : """
                  <pdf:Keywords>%s</pdf:Keywords>
            """.formatted(xml(information.getKeywords()));
        var languageTag = language == null ? "" : """
                  <dc:language><rdf:Bag><rdf:li>%s</rdf:li></rdf:Bag></dc:language>
            """.formatted(xml(language));
        return """
            <?xpacket begin="\uFEFF" id="W5M0MpCehiHzreSzNTczkc9d"?>
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about=""
                    xmlns:dc="http://purl.org/dc/elements/1.1/"
                    xmlns:pdf="http://ns.adobe.com/pdf/1.3/"
                    xmlns:xmp="http://ns.adobe.com/xap/1.0/">
                  <dc:format>application/pdf</dc:format>
                  <dc:title><rdf:Alt><rdf:li xml:lang="x-default">%s</rdf:li></rdf:Alt></dc:title>
                  <dc:creator><rdf:Seq><rdf:li>%s</rdf:li></rdf:Seq></dc:creator>
                  <pdf:Producer>Skald PDF</pdf:Producer>
                  <xmp:CreatorTool>Skald PDF</xmp:CreatorTool>
            %s%s%s                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
            <?xpacket end="w"?>
            """.formatted(title, author, subject, keywords, languageTag);
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String references(List<Integer> values) {
        var result = new StringBuilder();
        values.forEach(value -> result.append(value).append(" 0 R "));
        return result.toString();
    }

    private static String rectangle(float x, float y, float width, float height) {
        return format("[%s %s %s %s]", number(x), number(y), number(x + width), number(y + height));
    }

    static String number(float value) {
        return PdfNumbers.format(value);
    }

    private static String literalString(String value) {
        var result = new StringBuilder("(");
        for (int index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (character == '(' || character == ')' || character == '\\') {
                result.append('\\');
            }
            result.append(character);
        }
        return result.append(')').toString();
    }

    private static String textString(String value) {
        var result = new StringBuilder("<FEFF");
        for (var character : value.toCharArray()) {
            PdfNumbers.appendHex4(result, character);
        }
        return result.append('>').toString();
    }

    private static String hex(int value, int digits) {
        return format("%0" + digits + "X", value);
    }

    private static String hex(byte[] bytes, int length) {
        var result = new StringBuilder(length * 2);
        for (int index = 0; index < length; index++) {
            result.append(hex(bytes[index] & 0xff, 2));
        }
        return result.toString();
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static String format(String template, Object... arguments) {
        return String.format(Locale.ROOT, template, arguments);
    }

    private static String name(String value) {
        var bytes = value.getBytes(StandardCharsets.ISO_8859_1);
        var result = new StringBuilder(bytes.length);
        for (var item : bytes) {
            var unsigned = item & 0xff;
            if (unsigned >= 33 && unsigned <= 126 && "()<>[]{}/%#".indexOf(unsigned) < 0) {
                result.append((char) unsigned);
            } else {
                result.append('#').append(format("%02X", unsigned));
            }
        }
        return result.toString();
    }

    private static String hex(byte[] bytes) {
        var result = new StringBuilder(bytes.length * 2);
        for (var item : bytes) {
            result.append(format("%02X", item & 0xff));
        }
        return result.toString();
    }

    private static final class FontAggregate {
        private final Set<Integer> glyphs = new LinkedHashSet<>();
        private final Map<Integer, Integer> unicodeByGlyph = new LinkedHashMap<>();
    }

    private record PackedLocation(int objectStream, int index) {
    }

    private static final class ImportContext {
        private final NativePdfParser source;
        private final ObjectStore target;
        private final Map<CosReference, Integer> objects = new LinkedHashMap<>();

        ImportContext(NativePdfParser source, ObjectStore target) {
            this.source = source;
            this.target = target;
        }

        void map(CosReference sourceReference, int targetObject) {
            var previous = objects.putIfAbsent(sourceReference, targetObject);
            if (previous != null && previous != targetObject) {
                throw new IllegalStateException("Imported object was mapped twice");
            }
        }

        CosValue resolve(CosValue value) {
            return source.resolve(value);
        }

        CosDictionary dictionary(CosValue value, String description) {
            var resolved = source.resolve(value);
            if (resolved instanceof CosDictionary dictionary) {
                return dictionary;
            }
            throw new IllegalArgumentException(description + " is not a dictionary");
        }

        String sanitizedAnnotation(CosValue value) {
            var resolved = source.resolve(value);
            if (!(resolved instanceof CosDictionary dictionary)) {
                return null;
            }
            var cleaned = new LinkedHashMap<String, CosValue>();
            dictionary.values().forEach((key, item) -> {
                if (Set.of("AA", "JS", "OpenAction").contains(key)) {
                    return;
                }
                if ("A".equals(key) && isUnsafeAction(source.resolve(item))) {
                    return;
                }
                cleaned.put(key, item);
            });
            return direct(new CosDictionary(cleaned));
        }

        private static boolean isUnsafeAction(CosValue value) {
            if (!(value instanceof CosDictionary dictionary) || !(dictionary.get("S") instanceof CosName name)) {
                return true;
            }
            return !Set.of("URI", "GoTo").contains(name.value());
        }

        String direct(CosValue value) {
            return switch (value) {
                case CosNull ignored -> "null";
                case CosBoolean bool -> Boolean.toString(bool.value());
                case CosNumber number -> number.lexicalValue();
                case CosName pdfName -> "/" + name(pdfName.value());
                case CosString string -> "<" + hex(string.bytes()) + ">";
                case CosArray array -> {
                    var result = new StringBuilder("[");
                    array.values().forEach(item -> result.append(direct(item)).append(' '));
                    yield result.append(']').toString();
                }
                case CosDictionary dictionary -> directDictionary(dictionary);
                case CosStream ignored -> throw new IllegalArgumentException("A PDF stream cannot be a direct value");
                case CosReference reference -> importReference(reference) + " 0 R";
            };
        }

        private String directDictionary(CosDictionary dictionary) {
            var result = new StringBuilder("<<");
            dictionary.values().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                result.append(" /").append(name(entry.getKey())).append(' ')
                    .append(direct(entry.getValue())));
            return result.append(" >>").toString();
        }

        private int importReference(CosReference reference) {
            var existing = objects.get(reference);
            if (existing != null) {
                return existing;
            }
            var object = target.reserve();
            objects.put(reference, object);
            var value = source.resolve(reference);
            target.set(object, switch (value) {
                case CosStream stream -> rawStream(stream);
                default -> ascii(direct(value));
            });
            return object;
        }

        private byte[] rawStream(CosStream stream) {
            var dictionary = new StringBuilder("<<");
            stream.dictionary().values().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                if (!entry.getKey().equals("Length")) {
                    dictionary.append(" /").append(name(entry.getKey())).append(' ')
                        .append(direct(entry.getValue()));
                }
            });
            dictionary.append(" /Length ").append(stream.encodedBytes().length).append(" >>\nstream\n");
            var output = new ByteArrayOutputStream(dictionary.length() + stream.encodedBytes().length + 16);
            try {
                output.write(ascii(dictionary.toString()));
                output.write(stream.encodedBytes());
                output.write(ascii("\nendstream"));
            } catch (IOException impossible) {
                throw new AssertionError(impossible);
            }
            return output.toByteArray();
        }
    }

    private static final class ObjectStore {
        private final List<byte[]> objects = new ArrayList<>();
        private int rootObject;

        int reserve() {
            objects.add(null);
            return objects.size();
        }

        int add(byte[] body) {
            var number = reserve();
            set(number, body);
            return number;
        }

        void set(int number, byte[] body) {
            Objects.requireNonNull(body, "body");
            if (number < 1 || number > objects.size()) {
                throw new IndexOutOfBoundsException("Invalid PDF object number: " + number);
            }
            objects.set(number - 1, body);
        }

        byte[] get(int number) {
            var body = objects.get(number - 1);
            if (body == null) {
                throw new IllegalStateException("PDF object was reserved but never written: " + number);
            }
            return body;
        }

        int size() {
            return objects.size();
        }
    }

    private static final class CountingOutputStream extends OutputStream {
        private final OutputStream target;
        private long count;

        CountingOutputStream(OutputStream target) {
            this.target = target;
        }

        long count() {
            return count;
        }

        @Override
        public void write(int value) throws IOException {
            target.write(value);
            count++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            target.write(bytes, offset, length);
            count += length;
        }

        @Override
        public void flush() throws IOException {
            target.flush();
        }
    }
}
