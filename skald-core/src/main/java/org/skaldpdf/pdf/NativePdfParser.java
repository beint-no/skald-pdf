package org.skaldpdf.pdf;

import static org.skaldpdf.pdf.CosValue.*;

import org.skaldpdf.geom.PageSize;
import org.skaldpdf.geom.Rectangle;
import org.skaldpdf.image.ImageData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Bounded parser for the structural PDF subset needed by page composition. It
 * accepts classic xref tables, xref streams, object streams, and revision
 * chains, while treating all actions and page content as inert data.
 */
final class NativePdfParser {
    private static final int MAXIMUM_SOURCE_BYTES = 256 * 1024 * 1024;
    private static final int MAXIMUM_OBJECTS = 1_000_000;
    private static final int MAXIMUM_PAGES = 100_000;
    private static final int MAXIMUM_DEPTH = 128;
    private static final int MAXIMUM_DECODED_STRUCTURAL_BYTES = 128 * 1024 * 1024;
    private static final int STARTXREF_SEARCH_BYTES = 1024 * 1024;
    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)");
    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");

    private final byte[] source;
    private final String headerVersion;
    private final Map<Integer, ObjectLocation> locations = new LinkedHashMap<>();
    private final Map<CosReference, CosValue> objects = new HashMap<>();
    private final Set<CosReference> resolving = new HashSet<>();
    private CosReference root;
    private CosDictionary trailer;
    private boolean encrypted;
    private boolean hasPreviousRevision;
    private final int startXref;
    private final List<ImportedPage> pages;

    NativePdfParser(byte[] source) {
        if (source == null || source.length < 8 || source.length > MAXIMUM_SOURCE_BYTES) {
            throw new IllegalArgumentException("PDF input size is outside the supported range");
        }
        this.source = source.clone();
        require(startsWith(this.source, 0, "%PDF-"), "Input does not have a PDF header");
        headerVersion = new String(this.source, 5, 3, StandardCharsets.US_ASCII);
        require(headerVersion.matches("(?:1\\.[0-7]|2\\.0)"), "Unsupported PDF header version");
        startXref = Math.toIntExact(findStartXref());
        readCrossReferences(startXref);
        require(!encrypted, "Encrypted PDFs are not supported");
        require(root != null, "PDF trailer has no Root reference");
        pages = List.copyOf(readPages());
    }

    static boolean containsSealedSignature(byte[] pdf) {
        var matcher = java.util.regex.Pattern.compile(
            "/ByteRange\\s*\\[\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*]").matcher(
            new String(pdf, StandardCharsets.ISO_8859_1));
        while (matcher.find()) {
            if (Long.parseLong(matcher.group(2)) > 0 && Long.parseLong(matcher.group(4)) > 0) {
                return true;
            }
        }
        return false;
    }

    int startXref() {
        return startXref;
    }

    CosReference catalogReference() {
        return root;
    }

    CosDictionary trailer() {
        return trailer;
    }

    byte[] canonicalHeader() {
        var version = headerVersion.equals("2.0") || headerVersion.compareTo("1.5") >= 0
            ? headerVersion : "1.5";
        return ("%PDF-" + version + "\n%\u00e2\u00e3\u00cf\u00d3\n").getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * Hashes the complete object graph reachable from the final trailer without
     * depending on source object numbers or dictionary ordering. Stream bytes
     * are hashed as encoded data; callers may provide the exact image streams
     * that a canonical rewrite is expected to substitute.
     */
    byte[] semanticDigest(IdentityHashMap<CosStream, CosStream> replacements) {
        return semanticDigest(replacements, Map.of());
    }

    byte[] semanticDigest(IdentityHashMap<CosStream, CosStream> replacements,
                          Map<CosReference, CosReference> referenceAliases) {
        return new SemanticDigest(replacements, referenceAliases).digest();
    }

    boolean isSafeForCanonicalOptimization() {
        return canonicalRewriteConstraints().isEmpty();
    }

    Set<CanonicalRewriteConstraint> canonicalRewriteConstraints() {
        var constraints = EnumSet.noneOf(CanonicalRewriteConstraint.class);
        if (isLinearized()) {
            constraints.add(CanonicalRewriteConstraint.LINEARIZATION);
        }
        if (hasPreviousRevision) {
            constraints.add(CanonicalRewriteConstraint.INCREMENTAL_HISTORY);
        }
        if (hasConformanceProfile()) {
            constraints.add(CanonicalRewriteConstraint.CONFORMANCE_PROFILE);
        }
        if (hasSignatureDictionary()) {
            constraints.add(CanonicalRewriteConstraint.DIGITAL_SIGNATURE);
        }
        if (constraints.isEmpty() && hasMalformedFlateStream()) {
            constraints.add(CanonicalRewriteConstraint.MALFORMED_STREAM);
        }
        return Set.copyOf(constraints);
    }

    int maximumObjectNumber() {
        return locations.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    List<CosReference> acroFormFields() {
        var catalog = dictionary(resolve(root), "catalog");
        if (catalog.get("AcroForm") == null) {
            return List.of();
        }
        var acroForm = dictionary(resolve(catalog.get("AcroForm")), "AcroForm");
        if (acroForm.get("Fields") == null) {
            return List.of();
        }
        var fields = array(resolve(acroForm.get("Fields")), "AcroForm Fields");
        var result = new ArrayList<CosReference>();
        for (var item : fields.values()) {
            if (item instanceof CosReference reference) {
                result.add(reference);
            }
        }
        return List.copyOf(result);
    }

    List<ImportedPage> pages() {
        return pages;
    }

    List<EmbeddedImage> imageXObjects(ImportedPage page, int pageNumber) {
        var resourcesValue = page.dictionary().get("Resources");
        if (resourcesValue == null) {
            return List.of();
        }
        var result = new ArrayList<EmbeddedImage>();
        imageXObjects(resourcesValue, pageNumber, "", result,
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
        return List.copyOf(result);
    }

    private void imageXObjects(CosValue resourcesValue, int pageNumber, String path,
                               List<EmbeddedImage> result, Set<CosStream> visitedForms) {
        var resources = dictionary(resolve(resourcesValue), "page Resources");
        var xobjects = resources.get("XObject");
        if (xobjects == null) {
            return;
        }
        var dictionary = dictionary(resolve(xobjects), "XObject");
        for (var entry : dictionary.values().entrySet()) {
            var resolved = resolve(entry.getValue());
            if (!(resolved instanceof CosStream stream)) {
                continue;
            }
            var subtype = stream.dictionary().get("Subtype");
            if (!(resolve(subtype) instanceof CosName name)) {
                continue;
            }
            var resourceName = path.isEmpty() ? entry.getKey() : path + "/" + entry.getKey();
            if (name.value().equals("Image")) {
                result.add(embeddedImage(pageNumber, resourceName, stream));
            } else if (name.value().equals("Form") && visitedForms.add(stream)) {
                var nestedResources = stream.dictionary().get("Resources");
                if (nestedResources != null) {
                    imageXObjects(nestedResources, pageNumber, resourceName, result, visitedForms);
                }
            }
        }
    }

    byte[] contentBytes(ImportedPage page) {
        var contents = resolve(page.dictionary().get("Contents"));
        if (contents instanceof CosStream stream) {
            return decoded(stream, "page contents");
        }
        if (contents instanceof CosArray array) {
            var output = new ByteArrayOutputStream();
            for (var item : array.values()) {
                var resolved = resolve(item);
                if (resolved instanceof CosStream stream) {
                    try {
                        output.write(decoded(stream, "page contents"));
                        output.write('\n');
                    } catch (IOException impossible) {
                        throw new AssertionError(impossible);
                    }
                }
            }
            return output.toByteArray();
        }
        return new byte[0];
    }

    byte[] decodedStream(CosStream stream, String description) {
        return decoded(stream, description);
    }

    CosStream compressStreamLosslessly(CosStream stream, int compressionLevel) {
        var dictionary = stream.dictionary();
        if (dictionary.get("F") != null || dictionary.get("FFilter") != null
            || dictionary.get("FDecodeParms") != null) {
            return stream;
        }
        var filters = filterNames(dictionary.get("Filter"));
        var unfiltered = filters.isEmpty();
        if (!unfiltered && (filters.size() != 1 || !Set.of(
            "FlateDecode", "Fl", "ASCIIHexDecode", "AHx", "ASCII85Decode", "A85"
        ).contains(filters.getFirst()))) {
            return stream;
        }
        var flate = !unfiltered && Set.of("FlateDecode", "Fl").contains(filters.getFirst());
        if (!flate && !identityDecodeParameters(dictionary.get("DecodeParms"))) {
            return stream;
        }
        var original = stream.encodedBytes();
        if (original.length < 256) {
            return stream;
        }
        byte[] decoded;
        try {
            decoded = unfiltered ? original : switch (filters.getFirst()) {
                case "FlateDecode", "Fl" -> inflate(original, "stream recompression");
                case "ASCIIHexDecode", "AHx" -> asciiHex(original, "stream recompression");
                case "ASCII85Decode", "A85" -> ascii85(original, "stream recompression");
                default -> throw new AssertionError("Filtered stream was not screened");
            };
        } catch (RuntimeException malformed) {
            return stream;
        }
        var image = isName(dictionary.get("Subtype"), "Image");
        var sourceLevel = flate ? deflateLevelHint(original) : -1;
        if (sourceLevel == 3 || image && sourceLevel >= 2
            || !image && sourceLevel == 2 && !maximumCompressionPays(decoded)) {
            return stream;
        }
        var targetLevel = image ? Math.min(Compression.BALANCED.deflateLevel(), compressionLevel) : compressionLevel;
        var compressed = deflate(decoded, targetLevel);
        if (compressed.length + 64 >= original.length
            || !Arrays.equals(decoded, inflate(compressed, "stream recompression verification"))) {
            return stream;
        }
        if (flate) {
            return new CosStream(dictionary, compressed);
        }
        var values = new LinkedHashMap<String, CosValue>(dictionary.values());
        values.put("Filter", new CosName("FlateDecode"));
        values.remove("DecodeParms");
        return new CosStream(new CosDictionary(values), compressed);
    }

    private static int deflateLevelHint(byte[] encoded) {
        if (encoded.length < 2) {
            return -1;
        }
        var cmf = encoded[0] & 0xff;
        var flg = encoded[1] & 0xff;
        return (cmf & 0x0f) == 8 && ((cmf << 8) | flg) % 31 == 0 ? flg >>> 6 : -1;
    }

    private static boolean maximumCompressionPays(byte[] decoded) {
        var sample = decoded.length <= 32 * 1024 ? decoded : Arrays.copyOf(decoded, 32 * 1024);
        var balanced = deflate(sample, Compression.BALANCED.deflateLevel());
        var maximum = deflate(sample, Compression.MAXIMUM.deflateLevel());
        var saved = balanced.length - maximum.length;
        return saved >= 128 && saved * 100 >= balanced.length;
    }

    private boolean identityDecodeParameters(CosValue value) {
        if (value == null) {
            return true;
        }
        var resolved = resolve(value);
        if (resolved instanceof CosNull) {
            return true;
        }
        if (resolved instanceof CosArray array) {
            return array.values().isEmpty() || array.values().stream().allMatch(this::identityDecodeParameters);
        }
        if (!(resolved instanceof CosDictionary parameters)) {
            return false;
        }
        var predictor = parameters.get("Predictor");
        return predictor == null || resolve(predictor) instanceof CosNumber number
            && number.lexicalValue().equals("1");
    }

    private static byte[] deflate(byte[] bytes, int compressionLevel) {
        var output = new ByteArrayOutputStream(Math.max(64, bytes.length / 2));
        try (var deflater = new Deflater(compressionLevel);
             var compressed = new DeflaterOutputStream(output, deflater)) {
            compressed.write(bytes);
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
        return output.toByteArray();
    }

    ImageData decodeImage(CosStream stream) {
        var dictionary = stream.dictionary();
        var width = integer(resolve(dictionary.get("Width")), "image Width");
        var height = integer(resolve(dictionary.get("Height")), "image Height");
        var bits = dictionary.get("BitsPerComponent") == null ? 8
            : integer(resolve(dictionary.get("BitsPerComponent")), "image BitsPerComponent");
        require(width > 0 && height > 0, "Image dimensions must be positive");
        require(bits == 8, "Only 8-bit images can be decoded");
        var filters = filterNames(dictionary.get("Filter"));
        var data = stream.encodedBytes();
        var jpeg = false;
        for (var filter : filters) {
            switch (filter) {
                case "FlateDecode", "Fl" -> data = inflate(data, "image");
                case "ASCIIHexDecode", "AHx" -> data = asciiHex(data, "image");
                case "ASCII85Decode", "A85" -> data = ascii85(data, "image");
                case "DCTDecode", "DCT" -> jpeg = true;
                default -> throw new IllegalArgumentException("Unsupported image filter " + filter);
            }
        }
        if (jpeg) {
            return ImageData.fromJpeg(data);
        }
        data = applyPredictor(data, dictionary.get("DecodeParms"), "image");
        var colorSpaceValue = dictionary.get("ColorSpace");
        var colorSpace = colorSpaceName(colorSpaceValue);
        return switch (colorSpace) {
            case "DeviceRGB" -> ImageData.fromRgb(width, height, data);
            case "DeviceGray" -> ImageData.fromGray(width, height, data);
            case "ICCBased" -> switch (imageColorComponents(colorSpaceValue)) {
                case 3 -> ImageData.fromRgb(width, height, data);
                default -> throw new IllegalArgumentException("Unsupported ICC image component count");
            };
            default -> throw new IllegalArgumentException("Unsupported image color space " + colorSpace);
        };
    }

    private EmbeddedImage embeddedImage(int pageNumber, String resourceName, CosStream stream) {
        var dictionary = stream.dictionary();
        var width = integer(resolve(dictionary.get("Width")), "image Width");
        var height = integer(resolve(dictionary.get("Height")), "image Height");
        var bits = dictionary.get("BitsPerComponent") == null ? 8
            : integer(resolve(dictionary.get("BitsPerComponent")), "image BitsPerComponent");
        var filters = filterNames(dictionary.get("Filter"));
        var filter = filters.isEmpty() ? "None" : String.join("+", filters);
        var jpeg = filters.contains("DCTDecode") || filters.contains("DCT");
        var colorSpaceValue = dictionary.get("ColorSpace");
        var colorSpace = colorSpaceName(colorSpaceValue);
        var components = imageColorComponents(colorSpaceValue);
        var softMask = dictionary.get("SMask");
        var hasSoftMask = softMask != null && !isName(softMask, "None");
        var safe = supportedImageFilters(filters, jpeg)
            && bits == 8 && components > 0
            && identityImageDecode(dictionary.get("Decode"), components)
            && safeSoftMask(softMask)
            && !hasAny(dictionary, "Mask", "Metadata", "Alternates", "OPI", "Matte", "SMaskInData")
            && !booleanValue(dictionary.get("ImageMask"));
        return new EmbeddedImage(pageNumber, resourceName, width, height, filter,
            colorSpace, bits, jpeg, safe, hasSoftMask && safe, stream.encodedBytes(), this, stream);
    }

    /**
     * A preserved soft mask remains byte exact. Matte masks are excluded: the
     * parent colour samples are preblended and lossy recompression could alter
     * the unblending result at transparent edges.
     */
    private boolean safeSoftMask(CosValue value) {
        if (value == null || isName(value, "None")) {
            return true;
        }
        try {
            var resolved = resolve(value);
            if (!(resolved instanceof CosStream mask)) {
                return false;
            }
            var dictionary = mask.dictionary();
            var width = integer(resolve(dictionary.get("Width")), "soft-mask Width");
            var height = integer(resolve(dictionary.get("Height")), "soft-mask Height");
            var bits = integer(resolve(dictionary.get("BitsPerComponent")), "soft-mask BitsPerComponent");
            var type = dictionary.get("Type");
            return width > 0 && height > 0 && Set.of(1, 2, 4, 8, 16).contains(bits)
                && (type == null || isName(type, "XObject"))
                && isName(dictionary.get("Subtype"), "Image")
                && colorSpaceName(dictionary.get("ColorSpace")).equals("DeviceGray")
                && !booleanValue(dictionary.get("ImageMask"))
                && !hasAny(dictionary, "Mask", "SMask", "SMaskInData", "Matte", "Alternates", "OPI");
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    private static boolean hasAny(CosDictionary dictionary, String... names) {
        for (var name : names) {
            if (dictionary.get(name) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean supportedImageFilters(List<String> filters, boolean jpeg) {
        if (!jpeg) {
            return filters.size() == 1 && Set.of("FlateDecode", "Fl").contains(filters.getFirst());
        }
        if (filters.isEmpty() || !Set.of("DCTDecode", "DCT").contains(filters.getLast())) {
            return false;
        }
        return filters.subList(0, filters.size() - 1).stream()
            .allMatch(filter -> Set.of(
                "FlateDecode", "Fl", "ASCIIHexDecode", "AHx", "ASCII85Decode", "A85"
            ).contains(filter));
    }

    private boolean identityImageDecode(CosValue value, int components) {
        if (value == null) {
            return true;
        }
        var resolved = resolve(value);
        if (!(resolved instanceof CosArray decode)) {
            return false;
        }
        if (components <= 0 || decode.values().size() != components * 2) {
            return false;
        }
        for (int index = 0; index < components; index++) {
            if (number(resolve(decode.values().get(index * 2)), "image Decode") != 0
                || number(resolve(decode.values().get(index * 2 + 1)), "image Decode") != 1) {
                return false;
            }
        }
        return true;
    }

    int preservedImageColorComponents(CosStream stream) {
        return colorSpaceName(stream.dictionary().get("ColorSpace")).equals("ICCBased")
            ? imageColorComponents(stream.dictionary().get("ColorSpace")) : 0;
    }

    boolean preservesImageSoftMask(CosStream stream, int replacementWidth, int replacementHeight) {
        var embedded = embeddedImage(0, "", stream);
        if (!embedded.safeToRecompress() || !embedded.requiresOriginalDimensions()) {
            return false;
        }
        var width = integer(resolve(stream.dictionary().get("Width")), "image Width");
        var height = integer(resolve(stream.dictionary().get("Height")), "image Height");
        require(replacementWidth == width && replacementHeight == height,
            "An image with a soft mask must preserve its dimensions");
        return true;
    }

    private int imageColorComponents(CosValue value) {
        var colorSpace = colorSpaceName(value);
        if (colorSpace.equals("DeviceGray")) {
            return 1;
        }
        if (colorSpace.equals("DeviceRGB")) {
            return 3;
        }
        if (!colorSpace.equals("ICCBased")) {
            return 0;
        }
        var resolved = resolve(value);
        if (!(resolved instanceof CosArray array) || array.values().size() != 2) {
            return 0;
        }
        var profile = resolve(array.values().get(1));
        if (!(profile instanceof CosStream profileStream)) {
            return 0;
        }
        var countValue = profileStream.dictionary().get("N");
        if (countValue == null) {
            return 0;
        }
        var count = integer(resolve(countValue), "ICCBased N");
        return count == 3 ? count : 0;
    }

    boolean safeToDeduplicateImage(CosStream stream) {
        try {
            if (!embeddedImage(0, "", stream).safeToRecompress()) {
                return false;
            }
            return stream.dictionary().values().entrySet().stream()
                .filter(entry -> !entry.getKey().equals("Length"))
                .allMatch(entry -> containsNoIndirectValues(entry.getValue()));
        } catch (RuntimeException unsupported) {
            return false;
        }
    }

    Set<CosReference> fontProgramReferences() {
        var result = new LinkedHashSet<CosReference>();
        var references = new HashSet<CosReference>();
        var direct = java.util.Collections.newSetFromMap(new IdentityHashMap<CosValue, Boolean>());
        var pending = new ArrayDeque<CosValue>();
        pending.add(trailer);
        while (!pending.isEmpty()) {
            var value = pending.removeLast();
            if (value instanceof CosReference reference) {
                if (references.add(reference)) {
                    pending.add(resolve(reference));
                }
                continue;
            }
            if (!direct.add(value)) {
                continue;
            }
            if (value instanceof CosArray array) {
                pending.addAll(array.values());
                continue;
            }
            var dictionary = value instanceof CosStream stream ? stream.dictionary()
                : value instanceof CosDictionary candidate ? candidate : null;
            if (dictionary == null) {
                continue;
            }
            if (isName(dictionary.get("Type"), "FontDescriptor")) {
                for (var key : Set.of("FontFile", "FontFile2", "FontFile3")) {
                    if (dictionary.get(key) instanceof CosReference reference
                        && resolve(reference) instanceof CosStream) {
                        result.add(reference);
                    }
                }
            }
            pending.addAll(dictionary.values().values());
        }
        return Set.copyOf(result);
    }

    boolean safeToDeduplicateFontProgram(CosStream stream) {
        return stream.dictionary().values().entrySet().stream()
            .filter(entry -> !entry.getKey().equals("Length"))
            .allMatch(entry -> containsNoIndirectValues(entry.getValue()));
    }

    private static boolean containsNoIndirectValues(CosValue value) {
        return switch (value) {
            case CosReference ignored -> false;
            case CosStream ignored -> false;
            case CosArray array -> array.values().stream().allMatch(NativePdfParser::containsNoIndirectValues);
            case CosDictionary dictionary -> dictionary.values().values().stream()
                .allMatch(NativePdfParser::containsNoIndirectValues);
            default -> true;
        };
    }

    private boolean booleanValue(CosValue value) {
        return value != null && resolve(value) instanceof CosBoolean bool && bool.value();
    }

    private String colorSpaceName(CosValue value) {
        if (value == null) {
            return "DeviceGray";
        }
        var resolved = resolve(value);
        if (resolved instanceof CosName name) {
            return name.value();
        }
        if (resolved instanceof CosArray array && !array.values().isEmpty()
            && resolve(array.values().get(0)) instanceof CosName name) {
            return name.value();
        }
        return "Unknown";
    }

    CosValue resolve(CosValue value) {
        if (!(value instanceof CosReference reference)) {
            return value;
        }
        var cached = objects.get(reference);
        if (cached != null) {
            return cached;
        }
        require(resolving.add(reference), "Cycle while resolving PDF object " + reference.objectNumber());
        try {
            var location = locations.get(reference.objectNumber());
            if (location == null) {
                var result = new CosNull();
                objects.put(reference, result);
                return result;
            }
            CosValue result = switch (location) {
                case DirectLocation direct -> parseIndirect(direct.offset(), reference.objectNumber()).value();
                case CompressedLocation compressed -> resolveCompressed(reference, compressed);
            };
            objects.put(reference, result);
            return result;
        } finally {
            resolving.remove(reference);
        }
    }

    private CosValue resolveCompressed(CosReference reference, CompressedLocation location) {
        var streamReference = new CosReference(location.objectStream(), 0);
        var streamValue = resolve(streamReference);
        var stream = stream(streamValue, "object stream");
        var dictionary = stream.dictionary();
        require(name(dictionary.get("Type"), "object stream type").equals("ObjStm"),
            "Compressed object is not stored in an ObjStm");
        var count = integer(resolve(dictionary.get("N")), "object stream N");
        var first = integer(resolve(dictionary.get("First")), "object stream First");
        require(count >= 0 && count <= MAXIMUM_OBJECTS, "Invalid object stream count");
        var decoded = decoded(stream, "object stream");
        require(first >= 0 && first <= decoded.length, "Invalid object stream First offset");
        var header = new Cursor(decoded, 0);
        var objectNumbers = new int[count];
        var offsets = new int[count];
        for (int index = 0; index < count; index++) {
            objectNumbers[index] = header.readInteger("object stream object number");
            offsets[index] = header.readInteger("object stream object offset");
        }
        require(location.index() >= 0 && location.index() < count, "Object stream index is outside N");
        for (int index = 0; index < count; index++) {
            var offset = first + offsets[index];
            require(offset >= first && offset < decoded.length, "Object stream entry is outside the stream");
            var value = parseValue(new Cursor(decoded, offset), 0);
            objects.putIfAbsent(new CosReference(objectNumbers[index], 0), value);
        }
        var result = objects.get(new CosReference(reference.objectNumber(), 0));
        require(result != null, "Compressed object was not present in its object stream");
        return result;
    }

    private List<ImportedPage> readPages() {
        var catalog = dictionary(resolve(root), "catalog");
        require(name(catalog.get("Type"), "catalog type").equals("Catalog"), "Root is not a Catalog");
        var pagesValue = catalog.get("Pages");
        require(pagesValue != null, "Catalog has no Pages tree");
        var result = new ArrayList<ImportedPage>();
        walkPages(pagesValue, Map.of(), result, new HashSet<>(), 0);
        return result;
    }

    private void walkPages(CosValue value, Map<String, CosValue> inherited, List<ImportedPage> result,
                           Set<CosReference> visited, int depth) {
        require(depth <= MAXIMUM_DEPTH, "PDF page tree is too deep");
        if (value instanceof CosReference reference) {
            require(visited.add(reference), "Cycle in PDF page tree");
        }
        var node = dictionary(resolve(value), "page tree node");
        var merged = new LinkedHashMap<>(node.values());
        for (var key : List.of("Resources", "MediaBox", "CropBox", "Rotate", "UserUnit")) {
            if (!merged.containsKey(key) && inherited.containsKey(key)) {
                merged.put(key, inherited.get(key));
            }
        }
        var type = node.get("Type") == null ? "" : name(node.get("Type"), "page node type");
        if (type.equals("Pages") || node.get("Kids") != null) {
            var kids = array(resolve(node.get("Kids")), "Pages Kids");
            for (var kid : kids.values()) {
                walkPages(kid, merged, result, visited, depth + 1);
            }
            return;
        }
        require(type.isEmpty() || type.equals("Page"), "Page tree leaf is not a Page");
        require(result.size() < MAXIMUM_PAGES, "PDF has too many pages");
        var media = rectangle(merged.get("MediaBox"), "MediaBox");
        var crop = merged.get("CropBox") == null ? media : rectangle(merged.get("CropBox"), "CropBox");
        var rotation = merged.get("Rotate") == null ? 0
            : integer(resolve(merged.get("Rotate")), "page Rotate");
        require(rotation % 90 == 0, "Page Rotate must be a multiple of 90 degrees");
        rotation = Math.floorMod(rotation, 360);
        var reference = value instanceof CosReference pageReference
            ? pageReference : new CosReference(findObjectNumber(node), 0);
        var resourceNames = resourceNames(merged.get("Resources"));
        result.add(new ImportedPage(this, reference, Map.copyOf(merged),
            new PageSize(media.getWidth(), media.getHeight()), crop, rotation, resourceNames));
    }

    private int findObjectNumber(CosDictionary dictionary) {
        for (var entry : objects.entrySet()) {
            if (entry.getValue() == dictionary) {
                return entry.getKey().objectNumber();
            }
        }
        throw new IllegalArgumentException("Direct Page dictionaries are not supported");
    }

    private Set<String> resourceNames(CosValue resourcesValue) {
        if (resourcesValue == null) {
            return Set.of();
        }
        var resources = dictionary(resolve(resourcesValue), "page Resources");
        var result = new LinkedHashSet<String>();
        for (var category : List.of("Font", "XObject", "ExtGState")) {
            var value = resources.get(category);
            if (value != null) {
                result.addAll(dictionary(resolve(value), category + " resources").values().keySet());
            }
        }
        return Set.copyOf(result);
    }

    private Rectangle rectangle(CosValue value, String description) {
        var values = array(resolve(value), description).values();
        require(values.size() == 4, description + " must have four numbers");
        var left = number(resolve(values.get(0)), description);
        var bottom = number(resolve(values.get(1)), description);
        var right = number(resolve(values.get(2)), description);
        var top = number(resolve(values.get(3)), description);
        require(right >= left && top >= bottom, description + " has negative dimensions");
        return new Rectangle(left, bottom, right - left, top - bottom);
    }

    private void readCrossReferences(long startOffset) {
        var pending = new ArrayDeque<Long>();
        var visited = new HashSet<Long>();
        pending.add(startOffset);
        while (!pending.isEmpty()) {
            var offset = pending.removeFirst();
            if (!visited.add(offset)) {
                continue;
            }
            require(offset >= 0 && offset < source.length, "xref offset is outside the PDF");
            var cursor = new Cursor(source, Math.toIntExact(offset));
            CosDictionary trailer;
            if (cursor.peekKeyword("xref")) {
                trailer = parseClassicXref(cursor);
            } else {
                trailer = parseXrefStream(offset);
            }
            if (this.trailer == null) {
                this.trailer = trailer;
            }
            if (root == null && trailer.get("Root") != null) {
                root = reference(trailer.get("Root"), "trailer Root");
            }
            encrypted |= trailer.get("Encrypt") != null;
            if (trailer.get("XRefStm") != null) {
                pending.addFirst(numberLong(resolveForTrailer(trailer.get("XRefStm")), "hybrid xref stream"));
            }
            if (trailer.get("Prev") != null) {
                hasPreviousRevision = true;
                pending.addLast(numberLong(resolveForTrailer(trailer.get("Prev")), "xref Prev"));
            }
            require(locations.size() <= MAXIMUM_OBJECTS, "PDF has too many objects");
        }
    }

    private CosValue resolveForTrailer(CosValue value) {
        return value instanceof CosReference ? resolve(value) : value;
    }

    private CosDictionary parseClassicXref(Cursor cursor) {
        cursor.expectKeyword("xref");
        while (!cursor.peekKeyword("trailer")) {
            var firstObject = cursor.readInteger("xref subsection start");
            var count = cursor.readInteger("xref subsection count");
            require(firstObject >= 0 && count >= 0 && (long) firstObject + count <= MAXIMUM_OBJECTS,
                "Invalid classic xref subsection");
            for (int index = 0; index < count; index++) {
                var offset = cursor.readLong("xref object offset");
                var generation = cursor.readInteger("xref generation");
                var state = cursor.readToken();
                if (state.equals("n")) {
                    var object = firstObject + index;
                    if (object > 0) {
                        locations.putIfAbsent(object, new DirectLocation(offset, generation));
                    }
                } else {
                    require(state.equals("f"), "Invalid classic xref entry state");
                }
            }
        }
        cursor.expectKeyword("trailer");
        return dictionary(parseValue(cursor, 0), "classic xref trailer");
    }

    private CosDictionary parseXrefStream(long offset) {
        var indirect = parseIndirect(offset, null);
        var stream = stream(indirect.value(), "xref stream");
        var dictionary = stream.dictionary();
        require(name(dictionary.get("Type"), "xref stream type").equals("XRef"),
            "Object at startxref is not an xref stream");
        var widths = numberArray(dictionary.get("W"), "xref W");
        require(widths.size() == 3 && widths.stream().allMatch(width -> width >= 0 && width <= 8),
            "Invalid xref field widths");
        var size = integer(resolveForTrailer(dictionary.get("Size")), "xref Size");
        var indexes = dictionary.get("Index") == null
            ? List.of(0, size) : numberArray(dictionary.get("Index"), "xref Index");
        require(indexes.size() % 2 == 0, "xref Index must contain pairs");
        var decoded = decoded(stream, "xref stream");
        var entryWidth = widths.stream().mapToInt(Integer::intValue).sum();
        require(entryWidth > 0, "xref entries have zero width");
        var cursor = 0;
        for (int pair = 0; pair < indexes.size(); pair += 2) {
            var first = indexes.get(pair);
            var count = indexes.get(pair + 1);
            require(first >= 0 && count >= 0 && (long) first + count <= MAXIMUM_OBJECTS,
                "Invalid xref Index range");
            require((long) cursor + (long) count * entryWidth <= decoded.length, "Truncated xref stream");
            for (int index = 0; index < count; index++) {
                var type = widths.get(0) == 0 ? 1 : readField(decoded, cursor, widths.get(0));
                cursor += widths.get(0);
                var field2 = readField(decoded, cursor, widths.get(1));
                cursor += widths.get(1);
                var field3 = readField(decoded, cursor, widths.get(2));
                cursor += widths.get(2);
                var object = first + index;
                if (object == 0) {
                    continue;
                }
                if (type == 1) {
                    locations.putIfAbsent(object, new DirectLocation(field2, Math.toIntExact(field3)));
                } else if (type == 2) {
                    locations.putIfAbsent(object,
                        new CompressedLocation(Math.toIntExact(field2), Math.toIntExact(field3)));
                } else {
                    require(type == 0, "Unknown xref stream entry type");
                }
            }
        }
        objects.putIfAbsent(new CosReference(indirect.objectNumber(), indirect.generation()), stream);
        return dictionary;
    }

    private IndirectObject parseIndirect(long offset, Integer expectedObject) {
        require(offset >= 0 && offset < source.length, "Indirect object offset is outside the PDF");
        var cursor = new Cursor(source, Math.toIntExact(offset));
        var object = cursor.readInteger("object number");
        var generation = cursor.readInteger("object generation");
        if (expectedObject != null) {
            require(object == expectedObject, "xref points to the wrong indirect object");
        }
        cursor.expectKeyword("obj");
        var value = parseValue(cursor, 0);
        if (value instanceof CosDictionary dictionary && cursor.peekKeyword("stream")) {
            cursor.expectKeyword("stream");
            cursor.consumeStreamLineEnd();
            var lengthValue = dictionary.get("Length");
            var length = directStreamLength(lengthValue);
            byte[] bytes;
            if (length >= 0 && cursor.position() + (long) length <= source.length) {
                bytes = Arrays.copyOfRange(source, cursor.position(), cursor.position() + length);
                cursor.position(cursor.position() + length);
                cursor.skipWhitespace();
                cursor.expectKeyword("endstream");
            } else {
                var end = indexOf(source, ascii("endstream"), cursor.position());
                require(end >= cursor.position(), "PDF stream has no endstream marker");
                var actualEnd = end;
                while (actualEnd > cursor.position()
                    && (source[actualEnd - 1] == '\n' || source[actualEnd - 1] == '\r')) {
                    actualEnd--;
                }
                bytes = Arrays.copyOfRange(source, cursor.position(), actualEnd);
                cursor.position(end);
                cursor.expectKeyword("endstream");
            }
            value = new CosStream(dictionary, bytes);
        }
        return new IndirectObject(object, generation, value);
    }

    private int directStreamLength(CosValue value) {
        if (value instanceof CosNumber number) {
            try {
                return Integer.parseInt(number.lexicalValue());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        if (value instanceof CosReference reference && locations.containsKey(reference.objectNumber())) {
            try {
                return integer(resolve(reference), "stream Length");
            } catch (IllegalArgumentException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private CosValue parseValue(Cursor cursor, int depth) {
        require(depth <= MAXIMUM_DEPTH, "PDF object nesting is too deep");
        cursor.skipWhitespace();
        if (cursor.consume("<<")) {
            var values = new LinkedHashMap<String, CosValue>();
            while (!cursor.consume(">>")) {
                var key = cursor.readName();
                values.put(key, parseValue(cursor, depth + 1));
            }
            return new CosDictionary(values);
        }
        if (cursor.consume("[")) {
            var values = new ArrayList<CosValue>();
            while (!cursor.consume("]")) {
                values.add(parseValue(cursor, depth + 1));
            }
            return new CosArray(values);
        }
        if (cursor.peek('/')) {
            return new CosName(cursor.readName());
        }
        if (cursor.peek('(')) {
            return new CosString(cursor.readLiteralString());
        }
        if (cursor.peek('<')) {
            return new CosString(cursor.readHexString());
        }
        var token = cursor.readToken();
        return switch (token) {
            case "null" -> new CosNull();
            case "true" -> new CosBoolean(true);
            case "false" -> new CosBoolean(false);
            default -> {
                require(isNumber(token), "Unexpected PDF token: " + token);
                var position = cursor.position();
                if (isInteger(token)) {
                    try {
                        var second = cursor.readToken();
                        if (isInteger(second)) {
                            var third = cursor.readToken();
                            if (third.equals("R")) {
                                yield new CosReference(Integer.parseInt(token), Integer.parseInt(second));
                            }
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Restore and return the first number.
                    }
                    cursor.position(position);
                }
                yield new CosNumber(token);
            }
        };
    }

    private byte[] decoded(CosStream stream, String description) {
        var result = stream.encodedBytes();
        var filters = filterNames(stream.dictionary().get("Filter"));
        for (var filter : filters) {
            result = switch (filter) {
                case "FlateDecode", "Fl" -> inflate(result, description);
                case "ASCIIHexDecode", "AHx" -> asciiHex(result, description);
                case "ASCII85Decode", "A85" -> ascii85(result, description);
                default -> throw new IllegalArgumentException(
                    "Unsupported structural stream filter " + filter + " in " + description);
            };
        }
        return applyPredictor(result, stream.dictionary().get("DecodeParms"), description);
    }

    private List<String> filterNames(CosValue filterValue) {
        if (filterValue == null) {
            return List.of();
        }
        var resolved = resolve(filterValue);
        if (resolved instanceof CosName filter) {
            return List.of(filter.value());
        }
        var array = array(resolved, "stream Filter");
        return array.values().stream().map(value -> name(resolve(value), "stream filter")).toList();
    }

    private byte[] inflate(byte[] encoded, String description) {
        var inflater = new Inflater();
        var output = new ByteArrayOutputStream(Math.min(encoded.length * 2, 1_048_576));
        try {
            inflater.setInput(encoded);
            var buffer = new byte[8192];
            var total = 0;
            while (!inflater.finished()) {
                var read = inflater.inflate(buffer);
                if (read == 0) {
                    if (inflater.finished()) {
                        break;
                    }
                    require(!inflater.needsDictionary(), description + " requires an unsupported preset dictionary");
                    require(!inflater.needsInput(), description + " has truncated Flate data");
                    throw new IllegalArgumentException(description + " made no Flate decoding progress");
                }
                total += read;
                require(total <= MAXIMUM_DECODED_STRUCTURAL_BYTES,
                    description + " exceeds the decoded size limit");
                output.write(buffer, 0, read);
            }
            require(inflater.getRemaining() == 0, description + " has trailing Flate data");
            return output.toByteArray();
        } catch (DataFormatException exception) {
            throw new IllegalArgumentException("Unable to inflate " + description, exception);
        } finally {
            inflater.end();
        }
    }

    private boolean hasMalformedFlateStream() {
        var references = new HashSet<CosReference>();
        var direct = java.util.Collections.newSetFromMap(new IdentityHashMap<CosValue, Boolean>());
        var pending = new ArrayDeque<CosValue>();
        pending.add(trailer);
        while (!pending.isEmpty()) {
            var value = pending.removeLast();
            if (value instanceof CosReference reference) {
                if (references.add(reference)) {
                    pending.add(resolve(reference));
                }
                continue;
            }
            if (!direct.add(value)) {
                continue;
            }
            if (value instanceof CosArray array) {
                pending.addAll(array.values());
                continue;
            }
            var dictionary = value instanceof CosStream stream ? stream.dictionary()
                : value instanceof CosDictionary candidate ? candidate : null;
            if (dictionary == null) {
                continue;
            }
            if (value instanceof CosStream stream && !validFlateEncoding(stream)) {
                return true;
            }
            pending.addAll(dictionary.values().values());
        }
        return false;
    }

    private boolean validFlateEncoding(CosStream stream) {
        var filters = filterNames(stream.dictionary().get("Filter"));
        if (!filters.stream().anyMatch(filter -> Set.of("FlateDecode", "Fl").contains(filter))) {
            return true;
        }
        var data = stream.encodedBytes();
        try {
            for (var filter : filters) {
                data = switch (filter) {
                    case "ASCIIHexDecode", "AHx" -> asciiHex(data, "Flate preflight");
                    case "ASCII85Decode", "A85" -> ascii85(data, "Flate preflight");
                    case "FlateDecode", "Fl" -> inflate(data, "Flate preflight");
                    default -> {
                        // A preceding unknown filter prevents bounded validation of a later Flate stage.
                        yield null;
                    }
                };
                if (data == null) {
                    return true;
                }
            }
            return true;
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    private byte[] applyPredictor(byte[] decoded, CosValue parametersValue, String description) {
        if (parametersValue == null) {
            return decoded;
        }
        var resolved = resolve(parametersValue);
        if (resolved instanceof CosArray parametersArray) {
            resolved = parametersArray.values().isEmpty() ? new CosNull() : resolve(parametersArray.values().get(0));
        }
        if (resolved instanceof CosNull) {
            return decoded;
        }
        var parameters = dictionary(resolved, description + " DecodeParms");
        var predictor = parameters.get("Predictor") == null ? 1
            : integer(resolve(parameters.get("Predictor")), "Predictor");
        if (predictor <= 1) {
            return decoded;
        }
        require(predictor >= 10 && predictor <= 15, "Unsupported predictor in " + description);
        var colors = optionalInteger(parameters, "Colors", 1);
        var bits = optionalInteger(parameters, "BitsPerComponent", 8);
        var columns = optionalInteger(parameters, "Columns", 1);
        require(colors > 0 && bits == 8 && columns > 0, "Unsupported predictor parameters");
        var bytesPerPixel = colors;
        var rowBytes = Math.multiplyExact(colors, columns);
        require(decoded.length % (rowBytes + 1) == 0, "Predictor rows are truncated");
        var output = new byte[decoded.length / (rowBytes + 1) * rowBytes];
        for (int inputRow = 0, outputRow = 0; inputRow < decoded.length;
             inputRow += rowBytes + 1, outputRow += rowBytes) {
            var filter = decoded[inputRow] & 0xff;
            for (int column = 0; column < rowBytes; column++) {
                var raw = decoded[inputRow + 1 + column] & 0xff;
                var left = column >= bytesPerPixel ? output[outputRow + column - bytesPerPixel] & 0xff : 0;
                var up = outputRow >= rowBytes ? output[outputRow - rowBytes + column] & 0xff : 0;
                var upperLeft = outputRow >= rowBytes && column >= bytesPerPixel
                    ? output[outputRow - rowBytes + column - bytesPerPixel] & 0xff : 0;
                var value = switch (filter) {
                    case 0 -> raw;
                    case 1 -> raw + left;
                    case 2 -> raw + up;
                    case 3 -> raw + ((left + up) >>> 1);
                    case 4 -> raw + paeth(left, up, upperLeft);
                    default -> throw new IllegalArgumentException("Unknown PNG predictor filter");
                };
                output[outputRow + column] = (byte) value;
            }
        }
        return output;
    }

    private static int paeth(int left, int up, int upperLeft) {
        var estimate = left + up - upperLeft;
        var leftDistance = Math.abs(estimate - left);
        var upDistance = Math.abs(estimate - up);
        var diagonalDistance = Math.abs(estimate - upperLeft);
        return leftDistance <= upDistance && leftDistance <= diagonalDistance
            ? left : upDistance <= diagonalDistance ? up : upperLeft;
    }

    private int optionalInteger(CosDictionary dictionary, String key, int fallback) {
        return dictionary.get(key) == null ? fallback : integer(resolve(dictionary.get(key)), key);
    }

    private static byte[] asciiHex(byte[] encoded, String description) {
        var output = new ByteArrayOutputStream(encoded.length / 2);
        var high = -1;
        for (var item : encoded) {
            var value = item & 0xff;
            if (value == '>') {
                break;
            }
            if (isWhitespace(value)) {
                continue;
            }
            var nibble = Character.digit((char) value, 16);
            require(nibble >= 0, "Invalid ASCIIHex data in " + description);
            if (high < 0) {
                high = nibble;
            } else {
                output.write((high << 4) | nibble);
                high = -1;
            }
        }
        if (high >= 0) {
            output.write(high << 4);
        }
        return output.toByteArray();
    }

    private static byte[] ascii85(byte[] encoded, String description) {
        var output = new ByteArrayOutputStream(Math.min(encoded.length, 1_048_576));
        long value = 0;
        var digits = 0;
        var started = false;
        for (int index = 0; index < encoded.length; index++) {
            var item = encoded[index] & 0xff;
            if (isWhitespace(item)) {
                continue;
            }
            if (!started && item == '<' && index + 1 < encoded.length && encoded[index + 1] == '~') {
                started = true;
                index++;
                continue;
            }
            started = true;
            if (item == '~') {
                require(index + 1 < encoded.length && encoded[index + 1] == '>',
                    "Invalid ASCII85 terminator in " + description);
                break;
            }
            if (item == 'z') {
                require(digits == 0, "ASCII85 z appears inside a group in " + description);
                output.writeBytes(new byte[4]);
                require(output.size() <= MAXIMUM_DECODED_STRUCTURAL_BYTES,
                    description + " exceeds the decoded size limit");
                continue;
            }
            require(item >= '!' && item <= 'u', "Invalid ASCII85 data in " + description);
            value = value * 85 + item - '!';
            digits++;
            if (digits == 5) {
                require(value <= 0xffff_ffffL, "ASCII85 group overflows in " + description);
                writeAscii85Group(output, value, 4);
                value = 0;
                digits = 0;
                require(output.size() <= MAXIMUM_DECODED_STRUCTURAL_BYTES,
                    description + " exceeds the decoded size limit");
            }
        }
        require(digits != 1, "Truncated ASCII85 group in " + description);
        if (digits > 1) {
            for (int index = digits; index < 5; index++) {
                value = value * 85 + 84;
            }
            require(value <= 0xffff_ffffL, "ASCII85 group overflows in " + description);
            writeAscii85Group(output, value, digits - 1);
        }
        return output.toByteArray();
    }

    private static void writeAscii85Group(ByteArrayOutputStream output, long value, int bytes) {
        for (int shift = 24; shift >= 32 - bytes * 8; shift -= 8) {
            output.write((int) (value >>> shift) & 0xff);
        }
    }

    private long findStartXref() {
        var marker = ascii("startxref");
        var start = Math.max(0, source.length - STARTXREF_SEARCH_BYTES);
        for (int offset = source.length - marker.length; offset >= start; offset--) {
            if (startsWith(source, offset, marker)) {
                var cursor = new Cursor(source, offset + marker.length);
                return cursor.readLong("startxref offset");
            }
        }
        throw new IllegalArgumentException("PDF has no startxref marker in its final MiB");
    }

    private static long readField(byte[] bytes, int offset, int width) {
        long result = 0;
        for (int index = 0; index < width; index++) {
            result = (result << 8) | (bytes[offset + index] & 0xffL);
        }
        return result;
    }

    private static List<Integer> numberArray(CosValue value, String description) {
        var array = array(value, description);
        var result = new ArrayList<Integer>(array.values().size());
        for (var item : array.values()) {
            result.add(integer(item, description));
        }
        return List.copyOf(result);
    }

    private static CosDictionary dictionary(CosValue value, String description) {
        if (value instanceof CosDictionary dictionary) {
            return dictionary;
        }
        throw new IllegalArgumentException(description + " is not a dictionary");
    }

    private static CosArray array(CosValue value, String description) {
        if (value instanceof CosArray array) {
            return array;
        }
        throw new IllegalArgumentException(description + " is not an array");
    }

    private static CosStream stream(CosValue value, String description) {
        if (value instanceof CosStream stream) {
            return stream;
        }
        throw new IllegalArgumentException(description + " is not a stream");
    }

    private static CosReference reference(CosValue value, String description) {
        if (value instanceof CosReference reference) {
            return reference;
        }
        throw new IllegalArgumentException(description + " is not an indirect reference");
    }

    private static String name(CosValue value, String description) {
        if (value instanceof CosName name) {
            return name.value();
        }
        throw new IllegalArgumentException(description + " is not a name");
    }

    private static int integer(CosValue value, String description) {
        var result = numberLong(value, description);
        require(result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE,
            description + " is outside the integer range");
        return (int) result;
    }

    private static long numberLong(CosValue value, String description) {
        if (value instanceof CosNumber number && isInteger(number.lexicalValue())) {
            try {
                return Long.parseLong(number.lexicalValue());
            } catch (NumberFormatException ignored) {
                // Fall through to the public parser error.
            }
        }
        throw new IllegalArgumentException(description + " is not an integer");
    }

    private static float number(CosValue value, String description) {
        if (value instanceof CosNumber number) {
            try {
                var result = Float.parseFloat(number.lexicalValue());
                require(Float.isFinite(result), description + " is not finite");
                return result;
            } catch (NumberFormatException ignored) {
                // Fall through.
            }
        }
        throw new IllegalArgumentException(description + " is not a number");
    }

    private static boolean isNumber(String token) {
        return NUMBER.matcher(token).matches();
    }

    private static boolean isInteger(String token) {
        return INTEGER.matcher(token).matches();
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static boolean startsWith(byte[] bytes, int offset, String value) {
        return startsWith(bytes, offset, ascii(value));
    }

    private static boolean startsWith(byte[] bytes, int offset, byte[] value) {
        if (offset < 0 || offset + value.length > bytes.length) {
            return false;
        }
        for (int index = 0; index < value.length; index++) {
            if (bytes[offset + index] != value[index]) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(byte[] bytes, byte[] pattern, int start) {
        for (int offset = Math.max(0, start); offset <= bytes.length - pattern.length; offset++) {
            if (startsWith(bytes, offset, pattern)) {
                return offset;
            }
        }
        return -1;
    }

    private static boolean isWhitespace(int value) {
        return value == 0 || value == 9 || value == 10 || value == 12 || value == 13 || value == 32;
    }

    private static boolean isDelimiter(int value) {
        return isWhitespace(value) || "()<>[]{}/%".indexOf(value) >= 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean isLinearized() {
        var length = Math.min(source.length, 4096);
        return new String(source, 0, length, StandardCharsets.ISO_8859_1).contains("/Linearized");
    }

    private boolean hasConformanceProfile() {
        var catalog = dictionary(resolve(root), "catalog");
        var metadataValue = catalog.get("Metadata");
        if (metadataValue != null) {
            var resolved = resolve(metadataValue);
            if (!(resolved instanceof CosStream metadata)) {
                return true;
            }
            try {
                var xmp = new String(decoded(metadata, "XMP metadata"), StandardCharsets.UTF_8)
                    .toLowerCase(java.util.Locale.ROOT);
                if (List.of("pdfaid:", "pdfxid:", "pdfuaid:", "pdfeid:", "pdfvtid:",
                    "pdfa/ns/id", "pdfx/ns/id", "pdfua/ns/id", "pdfe/ns/id", "pdfvt/ns/id")
                    .stream().anyMatch(xmp::contains)) {
                    return true;
                }
            } catch (RuntimeException unreadableMetadata) {
                return true;
            }
        }
        var infoValue = trailer.get("Info");
        if (infoValue != null && resolve(infoValue) instanceof CosDictionary info
            && (info.get("GTS_PDFXVersion") != null || info.get("GTS_PDFXConformance") != null)) {
            return true;
        }
        return false;
    }

    private boolean hasSignatureDictionary() {
        var references = new HashSet<CosReference>();
        var direct = java.util.Collections.newSetFromMap(new IdentityHashMap<CosValue, Boolean>());
        var pending = new ArrayDeque<CosValue>();
        pending.add(trailer);
        while (!pending.isEmpty()) {
            var value = pending.removeLast();
            if (value instanceof CosReference reference) {
                if (references.add(reference)) {
                    pending.add(resolve(reference));
                }
                continue;
            }
            if (!direct.add(value)) {
                continue;
            }
            if (value instanceof CosArray array) {
                pending.addAll(array.values());
                continue;
            }
            var dictionary = value instanceof CosStream stream ? stream.dictionary()
                : value instanceof CosDictionary candidate ? candidate : null;
            if (dictionary == null) {
                continue;
            }
            if (isName(dictionary.get("Type"), "Sig") || isName(dictionary.get("FT"), "Sig")
                || dictionary.get("ByteRange") != null) {
                return true;
            }
            pending.addAll(dictionary.values().values());
        }
        return false;
    }

    private boolean isName(CosValue value, String expected) {
        return value != null && resolve(value) instanceof CosName name && name.value().equals(expected);
    }

    private final class SemanticDigest {
        private static final Set<String> STRUCTURAL_TRAILER_KEYS = Set.of(
            "Type", "Size", "Encrypt", "Prev", "XRefStm", "W", "Index", "Length", "Filter", "DecodeParms"
        );

        private final MessageDigest digest;
        private final IdentityHashMap<CosStream, CosStream> replacements;
        private final Map<CosReference, CosReference> referenceAliases;
        private final Map<CosReference, Integer> referenceNumbers = new LinkedHashMap<>();
        private final List<CosReference> pendingReferences = new ArrayList<>();

        SemanticDigest(IdentityHashMap<CosStream, CosStream> replacements,
                       Map<CosReference, CosReference> referenceAliases) {
            this.replacements = new IdentityHashMap<>(replacements);
            this.referenceAliases = Map.copyOf(referenceAliases);
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException impossible) {
                throw new AssertionError(impossible);
            }
        }

        byte[] digest() {
            var semanticTrailer = new LinkedHashMap<String, CosValue>();
            trailer.values().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                if (!STRUCTURAL_TRAILER_KEYS.contains(entry.getKey())) {
                    semanticTrailer.put(entry.getKey(), entry.getValue());
                }
            });
            value(new CosDictionary(semanticTrailer));
            for (int index = 0; index < pendingReferences.size(); index++) {
                token('O');
                integer(index);
                value(resolve(pendingReferences.get(index)));
            }
            return digest.digest();
        }

        private void value(CosValue value) {
            switch (value) {
                case CosNull ignored -> token('0');
                case CosBoolean bool -> token(bool.value() ? 'T' : 'F');
                case CosNumber number -> bytes('N', number.lexicalValue().getBytes(StandardCharsets.US_ASCII));
                case CosName name -> bytes('K', name.value().getBytes(StandardCharsets.ISO_8859_1));
                case CosString string -> bytes('S', string.bytes());
                case CosArray array -> {
                    token('A');
                    integer(array.values().size());
                    array.values().forEach(this::value);
                }
                case CosDictionary dictionary -> dictionary(dictionary, false);
                case CosStream original -> {
                    var stream = replacements.getOrDefault(original, original);
                    token('M');
                    dictionary(stream.dictionary(), true);
                    bytes('B', stream.encodedBytes());
                }
                case CosReference reference -> {
                    token('R');
                    var canonical = referenceAliases.getOrDefault(reference, reference);
                    var number = referenceNumbers.get(canonical);
                    if (number == null) {
                        number = pendingReferences.size();
                        referenceNumbers.put(canonical, number);
                        pendingReferences.add(canonical);
                    }
                    integer(number);
                }
            }
        }

        private void dictionary(CosDictionary dictionary, boolean stream) {
            token('D');
            var entries = dictionary.values().entrySet().stream()
                .filter(entry -> !stream || !entry.getKey().equals("Length"))
                .sorted(Map.Entry.comparingByKey())
                .toList();
            integer(entries.size());
            for (var entry : entries) {
                bytes('K', entry.getKey().getBytes(StandardCharsets.ISO_8859_1));
                value(entry.getValue());
            }
        }

        private void token(char value) {
            digest.update((byte) value);
        }

        private void integer(int value) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
        }

        private void bytes(char type, byte[] bytes) {
            token(type);
            integer(bytes.length);
            digest.update(bytes);
        }
    }

    private sealed interface ObjectLocation permits DirectLocation, CompressedLocation {
    }

    private record DirectLocation(long offset, int generation) implements ObjectLocation {
    }

    private record CompressedLocation(int objectStream, int index) implements ObjectLocation {
    }

    private record IndirectObject(int objectNumber, int generation, CosValue value) {
    }

    private static final class Cursor {
        private final byte[] bytes;
        private int position;

        Cursor(byte[] bytes, int position) {
            this.bytes = bytes;
            this.position = position;
        }

        int position() {
            return position;
        }

        void position(int value) {
            require(value >= 0 && value <= bytes.length, "PDF cursor is outside the input");
            position = value;
        }

        void skipWhitespace() {
            while (position < bytes.length) {
                var value = bytes[position] & 0xff;
                if (isWhitespace(value)) {
                    position++;
                } else if (value == '%') {
                    while (position < bytes.length && bytes[position] != '\n' && bytes[position] != '\r') {
                        position++;
                    }
                } else {
                    break;
                }
            }
        }

        boolean peek(char value) {
            skipWhitespace();
            return position < bytes.length && bytes[position] == value;
        }

        boolean consume(String value) {
            skipWhitespace();
            var pattern = ascii(value);
            if (!startsWith(bytes, position, pattern)) {
                return false;
            }
            position += pattern.length;
            return true;
        }

        boolean peekKeyword(String keyword) {
            var saved = position;
            try {
                return readToken().equals(keyword);
            } catch (IllegalArgumentException ignored) {
                return false;
            } finally {
                position = saved;
            }
        }

        void expectKeyword(String keyword) {
            var token = readToken();
            require(token.equals(keyword), "Expected " + keyword + " but found " + token);
        }

        String readToken() {
            skipWhitespace();
            require(position < bytes.length, "Unexpected end of PDF input");
            var start = position;
            while (position < bytes.length && !isDelimiter(bytes[position] & 0xff)) {
                position++;
            }
            require(position > start, "Expected a PDF token");
            return new String(bytes, start, position - start, StandardCharsets.ISO_8859_1);
        }

        int readInteger(String description) {
            var value = readLong(description);
            require(value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE,
                description + " is outside the integer range");
            return (int) value;
        }

        long readLong(String description) {
            var token = readToken();
            require(isInteger(token), description + " is not an integer");
            try {
                return Long.parseLong(token);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(description + " is outside the long range", exception);
            }
        }

        String readName() {
            skipWhitespace();
            require(position < bytes.length && bytes[position] == '/', "Expected a PDF name");
            position++;
            var output = new ByteArrayOutputStream();
            while (position < bytes.length && !isDelimiter(bytes[position] & 0xff)) {
                var value = bytes[position++] & 0xff;
                if (value == '#') {
                    require(position + 2 <= bytes.length, "Truncated PDF name escape");
                    var high = Character.digit((char) bytes[position++], 16);
                    var low = Character.digit((char) bytes[position++], 16);
                    require(high >= 0 && low >= 0, "Invalid PDF name escape");
                    output.write((high << 4) | low);
                } else {
                    output.write(value);
                }
            }
            return output.toString(StandardCharsets.ISO_8859_1);
        }

        byte[] readHexString() {
            skipWhitespace();
            require(position < bytes.length && bytes[position++] == '<', "Expected a PDF hex string");
            var output = new ByteArrayOutputStream();
            var high = -1;
            while (position < bytes.length) {
                var value = bytes[position++] & 0xff;
                if (value == '>') {
                    if (high >= 0) {
                        output.write(high << 4);
                    }
                    return output.toByteArray();
                }
                if (isWhitespace(value)) {
                    continue;
                }
                var nibble = Character.digit((char) value, 16);
                require(nibble >= 0, "Invalid PDF hex string");
                if (high < 0) {
                    high = nibble;
                } else {
                    output.write((high << 4) | nibble);
                    high = -1;
                }
            }
            throw new IllegalArgumentException("Unterminated PDF hex string");
        }

        byte[] readLiteralString() {
            skipWhitespace();
            require(position < bytes.length && bytes[position++] == '(', "Expected a PDF literal string");
            var output = new ByteArrayOutputStream();
            var nesting = 1;
            while (position < bytes.length) {
                var value = bytes[position++] & 0xff;
                if (value == '\\') {
                    require(position < bytes.length, "Truncated PDF string escape");
                    var escaped = bytes[position++] & 0xff;
                    switch (escaped) {
                        case 'n' -> output.write('\n');
                        case 'r' -> output.write('\r');
                        case 't' -> output.write('\t');
                        case 'b' -> output.write('\b');
                        case 'f' -> output.write('\f');
                        case '(', ')', '\\' -> output.write(escaped);
                        case '\r' -> {
                            if (position < bytes.length && bytes[position] == '\n') {
                                position++;
                            }
                        }
                        case '\n' -> {
                        }
                        default -> {
                            if (escaped >= '0' && escaped <= '7') {
                                var octal = escaped - '0';
                                for (int count = 1; count < 3 && position < bytes.length
                                    && bytes[position] >= '0' && bytes[position] <= '7'; count++) {
                                    octal = octal * 8 + bytes[position++] - '0';
                                }
                                output.write(octal & 0xff);
                            } else {
                                output.write(escaped);
                            }
                        }
                    }
                } else if (value == '(') {
                    nesting++;
                    output.write(value);
                } else if (value == ')') {
                    nesting--;
                    if (nesting == 0) {
                        return output.toByteArray();
                    }
                    output.write(value);
                } else {
                    output.write(value);
                }
            }
            throw new IllegalArgumentException("Unterminated PDF literal string");
        }

        void consumeStreamLineEnd() {
            if (position < bytes.length && bytes[position] == '\r') {
                position++;
                if (position < bytes.length && bytes[position] == '\n') {
                    position++;
                }
            } else if (position < bytes.length && bytes[position] == '\n') {
                position++;
            } else {
                throw new IllegalArgumentException("PDF stream keyword is not followed by a line ending");
            }
        }
    }
}
