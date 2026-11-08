package org.skaldpdf.pdf;

import static org.skaldpdf.pdf.CosValue.CosArray;
import static org.skaldpdf.pdf.CosValue.CosDictionary;
import static org.skaldpdf.pdf.CosValue.CosName;
import static org.skaldpdf.pdf.CosValue.CosNumber;
import static org.skaldpdf.pdf.CosValue.CosStream;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * How large page content paints each image XObject. Every {@code Do} on imported
 * pages and the Forms they paint is followed through {@code q}, {@code Q} and
 * {@code cm}; the largest placement of an image decides its resolution.
 *
 * <p>The answer is deliberately conservative. An image is unknown when any use
 * could not be followed: a content stream that does not parse cleanly, an image
 * reachable from annotation appearances, patterns, soft-mask groups or Type3
 * glyphs, or one that page content never paints.
 */
final class ImagePlacements {
    private static final int MAX_FORM_DEPTH = 16;

    private final NativePdfParser parser;
    /** Largest points per image pixel along either axis, over every placement. */
    private final Map<CosStream, Double> pointsPerPixel = new IdentityHashMap<>();
    private final Set<CosStream> unknown = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<CosDictionary> inspectedResources = Collections.newSetFromMap(new IdentityHashMap<>());

    private ImagePlacements(NativePdfParser parser) {
        this.parser = parser;
    }

    static ImagePlacements analyze(NativePdfParser parser) {
        var placements = new ImagePlacements(parser);
        for (var page : parser.pages()) {
            placements.page(page);
        }
        return placements;
    }

    /**
     * The lowest resolution, in pixels per inch, at which page content paints the
     * image, or empty when any use is unknown or the image is never painted.
     */
    OptionalDouble pixelsPerInch(CosStream image) {
        var scale = pointsPerPixel.get(image);
        if (scale == null || unknown.contains(image) || !(scale > 0)) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(72 / scale);
    }

    private void page(ImportedPage page) {
        var resources = dictionaryOrNull(page.dictionary().get("Resources"));
        try {
            var userUnit = page.dictionary().get("UserUnit") == null ? 1 : number(page.dictionary().get("UserUnit"));
            if (!(userUnit > 0) || !Double.isFinite(userUnit)) {
                throw new IllegalArgumentException("Invalid UserUnit");
            }
            if (resources != null) {
                walk(parser.contentBytes(page), resources, new double[] {userUnit, 0, 0, userUnit, 0, 0},
                    0, Collections.newSetFromMap(new IdentityHashMap<>()));
            }
        } catch (RuntimeException unfollowable) {
            if (resources != null) {
                markReachableUnknown(resources, Collections.newSetFromMap(new IdentityHashMap<>()));
            }
        }
        var annotations = resolve(page.dictionary().get("Annots"));
        if (annotations instanceof CosArray array) {
            for (var value : array.values()) {
                if (resolve(value) instanceof CosDictionary annotation
                    && resolve(annotation.get("AP")) instanceof CosDictionary appearances) {
                    for (var appearance : appearances.values().values()) {
                        markAppearanceUnknown(resolve(appearance));
                    }
                }
            }
        }
    }

    private void walk(byte[] content, CosDictionary resources, double[] start, int depth, Set<CosStream> forms) {
        inspectIndirectUses(resources);
        var scanner = new ContentScanner<>(content, Keyword::of, Keyword.OTHER);
        var operands = new ArrayList<Object>();
        var saved = new ArrayDeque<double[]>();
        var ctm = start;
        for (var token = scanner.next(); token != null; token = scanner.next()) {
            if (!(token instanceof Keyword keyword)) {
                operands.add(token);
                continue;
            }
            switch (keyword) {
                case SAVE -> saved.push(ctm);
                case RESTORE -> {
                    if (saved.isEmpty()) {
                        throw new IllegalArgumentException("Unbalanced Q");
                    }
                    ctm = saved.pop();
                }
                case CONCAT -> ctm = multiply(matrix(operands), ctm);
                case PAINT -> paint(operands, resources, ctm, depth, forms);
                case INLINE_IMAGE -> scanner.skipInlineImage();
                case OTHER -> {
                }
            }
            operands.clear();
        }
    }

    private void paint(List<Object> operands, CosDictionary resources, double[] ctm, int depth, Set<CosStream> forms) {
        if (operands.size() != 1 || !(operands.getFirst() instanceof String name)) {
            throw new IllegalArgumentException("Do needs one name operand");
        }
        var xobjects = dictionaryOrNull(resources.get("XObject"));
        var value = xobjects == null ? null : resolve(xobjects.get(name));
        if (!(value instanceof CosStream stream)) {
            throw new IllegalArgumentException("Do names a missing XObject");
        }
        var subtype = resolve(stream.dictionary().get("Subtype"));
        if (subtype instanceof CosName image && image.value().equals("Image")) {
            record(stream, ctm);
        } else if (subtype instanceof CosName form && form.value().equals("Form")) {
            if (depth >= MAX_FORM_DEPTH || !forms.add(stream)) {
                throw new IllegalArgumentException("Form nesting is too deep or cyclic");
            }
            var formMatrix = stream.dictionary().get("Matrix") == null
                ? new double[] {1, 0, 0, 1, 0, 0} : matrix(resolve(stream.dictionary().get("Matrix")));
            var formResources = dictionaryOrNull(stream.dictionary().get("Resources"));
            walk(parser.decodedStream(stream, "Form XObject"), formResources == null ? resources : formResources,
                multiply(formMatrix, ctm), depth + 1, forms);
            forms.remove(stream);
        }
    }

    private void record(CosStream image, double[] ctm) {
        var width = number(image.dictionary().get("Width"));
        var height = number(image.dictionary().get("Height"));
        var scale = Math.max(Math.hypot(ctm[0], ctm[1]) / width, Math.hypot(ctm[2], ctm[3]) / height);
        if (!Double.isFinite(scale) || !(width > 0) || !(height > 0)) {
            throw new IllegalArgumentException("Image placement is not finite");
        }
        pointsPerPixel.merge(image, scale, Math::max);
    }

    /** Patterns, soft-mask groups and Type3 glyphs paint images in ways this walk does not follow. */
    private void inspectIndirectUses(CosDictionary resources) {
        if (inspectedResources.add(resources)) {
            for (var nested : indirectResources(resources)) {
                markReachableUnknown(nested, Collections.newSetFromMap(new IdentityHashMap<>()));
            }
        }
    }

    /** Resources of the patterns, soft-mask groups and Type3 fonts that these resources name. */
    private List<CosDictionary> indirectResources(CosDictionary resources) {
        var result = new ArrayList<CosDictionary>();
        for (var key : List.of("Pattern", "ExtGState", "Font")) {
            var group = dictionaryOrNull(resources.get(key));
            if (group == null) {
                continue;
            }
            for (var value : group.values().values()) {
                var resolved = resolve(value);
                var dictionary = resolved instanceof CosStream stream ? stream.dictionary()
                    : resolved instanceof CosDictionary plain ? plain : null;
                if (dictionary == null) {
                    continue;
                }
                addIfDictionary(result, dictionary.get("Resources"));
                if (resolve(dictionary.get("SMask")) instanceof CosDictionary softMask
                    && resolve(softMask.get("G")) instanceof CosStream maskGroup) {
                    addIfDictionary(result, maskGroup.dictionary().get("Resources"));
                }
            }
        }
        return result;
    }

    private void markAppearanceUnknown(CosValue appearance) {
        if (appearance instanceof CosStream stream) {
            var resources = dictionaryOrNull(stream.dictionary().get("Resources"));
            if (resources != null) {
                markReachableUnknown(resources, Collections.newSetFromMap(new IdentityHashMap<>()));
            }
        } else if (appearance instanceof CosDictionary states) {
            for (var state : states.values().values()) {
                markAppearanceUnknown(resolve(state));
            }
        }
    }

    private void markReachableUnknown(CosDictionary resources, Set<CosDictionary> visited) {
        if (!visited.add(resources)) {
            return;
        }
        var xobjects = dictionaryOrNull(resources.get("XObject"));
        if (xobjects != null) {
            for (var value : xobjects.values().values()) {
                if (!(resolve(value) instanceof CosStream stream)) {
                    continue;
                }
                var subtype = resolve(stream.dictionary().get("Subtype"));
                if (subtype instanceof CosName name && name.value().equals("Image")) {
                    unknown.add(stream);
                } else {
                    var nested = dictionaryOrNull(stream.dictionary().get("Resources"));
                    if (nested != null) {
                        markReachableUnknown(nested, visited);
                    }
                }
            }
        }
        for (var nested : indirectResources(resources)) {
            markReachableUnknown(nested, visited);
        }
    }

    private void addIfDictionary(List<CosDictionary> target, CosValue value) {
        var dictionary = dictionaryOrNull(value);
        if (dictionary != null) {
            target.add(dictionary);
        }
    }

    private CosValue resolve(CosValue value) {
        return value == null ? null : parser.resolve(value);
    }

    private CosDictionary dictionaryOrNull(CosValue value) {
        return resolve(value) instanceof CosDictionary dictionary ? dictionary : null;
    }

    private double number(CosValue value) {
        if (!(resolve(value) instanceof CosNumber number)) {
            throw new IllegalArgumentException("Expected a number");
        }
        return Double.parseDouble(number.lexicalValue());
    }

    private double[] matrix(CosValue value) {
        if (!(value instanceof CosArray array) || array.values().size() != 6) {
            throw new IllegalArgumentException("Expected a six-number matrix");
        }
        var result = new double[6];
        for (int index = 0; index < 6; index++) {
            result[index] = number(array.values().get(index));
        }
        return result;
    }

    private static double[] matrix(List<Object> operands) {
        if (operands.size() != 6) {
            throw new IllegalArgumentException("cm needs six operands");
        }
        var result = new double[6];
        for (int index = 0; index < 6; index++) {
            if (!(operands.get(index) instanceof Float number) || !Float.isFinite(number)) {
                throw new IllegalArgumentException("cm operands must be numbers");
            }
            result[index] = number;
        }
        return result;
    }

    /** {@code m × ctm} for PDF row-vector matrices [a b c d e f]. */
    private static double[] multiply(double[] m, double[] ctm) {
        return new double[] {
            m[0] * ctm[0] + m[1] * ctm[2],
            m[0] * ctm[1] + m[1] * ctm[3],
            m[2] * ctm[0] + m[3] * ctm[2],
            m[2] * ctm[1] + m[3] * ctm[3],
            m[4] * ctm[0] + m[5] * ctm[2] + ctm[4],
            m[4] * ctm[1] + m[5] * ctm[3] + ctm[5],
        };
    }

    private enum Keyword {
        SAVE, RESTORE, CONCAT, PAINT, INLINE_IMAGE, OTHER;

        static Keyword of(byte[] bytes, int start, int length) {
            if (length == 1) {
                return switch (bytes[start]) {
                    case 'q' -> SAVE;
                    case 'Q' -> RESTORE;
                    default -> OTHER;
                };
            }
            if (length != 2) {
                return OTHER;
            }
            var first = bytes[start];
            var second = bytes[start + 1];
            if (first == 'c' && second == 'm') {
                return CONCAT;
            }
            if (first == 'D' && second == 'o') {
                return PAINT;
            }
            return first == 'B' && second == 'I' ? INLINE_IMAGE : OTHER;
        }
    }
}
