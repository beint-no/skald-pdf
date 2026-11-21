package org.skaldpdf.pdf;

import static org.skaldpdf.pdf.CosValue.CosArray;
import static org.skaldpdf.pdf.CosValue.CosBoolean;
import static org.skaldpdf.pdf.CosValue.CosDictionary;
import static org.skaldpdf.pdf.CosValue.CosName;
import static org.skaldpdf.pdf.CosValue.CosNumber;
import static org.skaldpdf.pdf.CosValue.CosStream;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Conservatively identifies an imported PDF whose entire visual output is one
 * uncropped, axis-aligned raster image on a single page.
 *
 * <p>The analysis fails closed: multi-page documents, documents with text
 * operators, fonts, vector path painting, annotations, forms, signatures,
 * outlines, embedded files, optional content layers, transparency/blend states,
 * non-orthogonal transforms, or cropping clip paths all return empty.
 */
final class SinglePageImageAnalysis {
    private static final int MINIMUM_IMAGE_PIXELS = 100;
    private static final double MINIMUM_PLACED_POINTS = 72.0;
    private static final double MINIMUM_PLACED_DPI = 40.0;
    private static final double AXIS_TOLERANCE = 1e-4;
    private static final double ALPHA_TOLERANCE = 1e-4;
    private static final double CROP_MIN_TOLERANCE_POINTS = 4.0;
    private static final double CROP_RELATIVE_TOLERANCE = 0.02;

    private final NativePdfParser parser;

    private SinglePageImageAnalysis(NativePdfParser parser) {
        this.parser = parser;
    }

    static Optional<SinglePageImage> analyze(NativePdfParser parser) {
        try {
            return new SinglePageImageAnalysis(parser).inspect();
        } catch (RuntimeException unfollowable) {
            return Optional.empty();
        }
    }

    private Optional<SinglePageImage> inspect() {
        if (parser.pages().size() != 1) {
            return Optional.empty();
        }
        var constraints = parser.canonicalRewriteConstraints();
        for (var constraint : constraints) {
            if (constraint != CanonicalRewriteConstraint.LINEARIZATION) {
                return Optional.empty();
            }
        }
        var catalog = parser.catalog();
        if (hasUnsafeCatalogEntries(catalog)) {
            return Optional.empty();
        }

        var page = parser.pages().getFirst();
        var pageDict = page.dictionary();
        if (hasNonEmptyArray(pageDict.get("Annots")) || pageDict.get("AA") != null) {
            return Optional.empty();
        }
        var resources = dictionaryOrNull(pageDict.get("Resources"));
        if (resources == null) {
            return Optional.empty();
        }
        if (hasNonEmptyDictionary(resources.get("Font"))
            || hasNonEmptyDictionary(resources.get("Pattern"))
            || hasNonEmptyDictionary(resources.get("Shading"))) {
            return Optional.empty();
        }

        var xobjects = dictionaryOrNull(resources.get("XObject"));
        if (xobjects == null || xobjects.values().isEmpty()) {
            return Optional.empty();
        }

        var userUnit = pageDict.get("UserUnit") == null ? 1.0 : number(pageDict.get("UserUnit"));
        if (!(userUnit > 0) || !Double.isFinite(userUnit)) {
            return Optional.empty();
        }

        var mediaBox = page.pageSize();
        var cropBox = page.cropBox();
        var pageClip = new Box(
            Math.max(mediaBox.getLeft(), cropBox.getLeft()) * userUnit,
            Math.max(mediaBox.getBottom(), cropBox.getBottom()) * userUnit,
            Math.min(mediaBox.getRight(), cropBox.getRight()) * userUnit,
            Math.min(mediaBox.getTop(), cropBox.getTop()) * userUnit
        );
        if (!pageClip.isPositive()) {
            return Optional.empty();
        }

        var walk = walkContentStream(parser.contentBytes(page), resources, xobjects, userUnit, pageClip);
        if (walk == null) {
            return Optional.empty();
        }

        EmbeddedImage embedded = null;
        for (var candidate : parser.imageXObjects(page, 1)) {
            if (candidate.stream() == walk.imageStream) {
                embedded = candidate;
                break;
            }
        }
        if (embedded == null
            || !embedded.safeToRecompress()
            || embedded.requiresOriginalDimensions()
            || embedded.width() < MINIMUM_IMAGE_PIXELS
            || embedded.height() < MINIMUM_IMAGE_PIXELS) {
            return Optional.empty();
        }

        var orientation = exifOrientation(walk.ctm, page.rotation());
        if (orientation == 0) {
            return Optional.empty();
        }

        var placed = transformUnitSquare(walk.ctm);
        if (!placed.isPositive()) {
            return Optional.empty();
        }
        var placedWidth = placed.width();
        var placedHeight = placed.height();
        if (placedWidth < MINIMUM_PLACED_POINTS || placedHeight < MINIMUM_PLACED_POINTS) {
            return Optional.empty();
        }

        var horizontalPoints = Math.hypot(walk.ctm[0], walk.ctm[1]);
        var verticalPoints = Math.hypot(walk.ctm[2], walk.ctm[3]);
        var dpiX = embedded.width() / (horizontalPoints / 72.0);
        var dpiY = embedded.height() / (verticalPoints / 72.0);
        if (dpiX < MINIMUM_PLACED_DPI || dpiY < MINIMUM_PLACED_DPI) {
            return Optional.empty();
        }

        var tolX = Math.max(CROP_MIN_TOLERANCE_POINTS, placedWidth * CROP_RELATIVE_TOLERANCE);
        var tolY = Math.max(CROP_MIN_TOLERANCE_POINTS, placedHeight * CROP_RELATIVE_TOLERANCE);
        if (walk.clipBox.minX > placed.minX + tolX
            || walk.clipBox.maxX < placed.maxX - tolX
            || walk.clipBox.minY > placed.minY + tolY
            || walk.clipBox.maxY < placed.maxY - tolY) {
            return Optional.empty();
        }

        return Optional.of(new SinglePageImage(embedded, orientation));
    }

    private boolean hasUnsafeCatalogEntries(CosDictionary catalog) {
        if (catalog.get("Outlines") != null
            || catalog.get("Dests") != null
            || catalog.get("OCProperties") != null
            || catalog.get("AF") != null
            || catalog.get("OpenAction") != null
            || catalog.get("AA") != null
            || catalog.get("JavaScript") != null) {
            return true;
        }
        if (catalog.get("AcroForm") != null) {
            var acroForm = dictionaryOrNull(catalog.get("AcroForm"));
            if (acroForm == null
                || hasNonEmptyArray(acroForm.get("Fields"))
                || acroForm.get("XFA") != null) {
                return true;
            }
            var sigFlags = acroForm.get("SigFlags");
            if (sigFlags != null && (!(resolve(sigFlags) instanceof CosNumber num)
                || Double.parseDouble(num.lexicalValue()) != 0.0)) {
                return true;
            }
        }
        var names = dictionaryOrNull(catalog.get("Names"));
        if (names != null) {
            if (hasNonEmptyDictionary(names.get("EmbeddedFiles"))
                || hasNonEmptyDictionary(names.get("JavaScript"))
                || hasNonEmptyDictionary(names.get("Dests"))) {
                return true;
            }
        }
        return false;
    }

    private @Nullable PaintedImage walkContentStream(
        byte[] content,
        CosDictionary resources,
        CosDictionary xobjects,
        double userUnit,
        Box initialClip
    ) {
        var scanner = new ContentScanner<>(content, Keyword::of, Keyword.UNKNOWN);
        var operands = new ArrayList<Object>();
        var saved = new ArrayDeque<GraphicsState>();
        var extGStates = dictionaryOrNull(resources.get("ExtGState"));

        var ctm = new double[] {userUnit, 0, 0, userUnit, 0, 0};
        var clipBox = initialClip;
        var inTextBlock = false;
        Box pendingRect = null;
        var hasPendingClip = false;
        PaintedImage painted = null;

        for (var token = scanner.next(); token != null; token = scanner.next()) {
            if (!(token instanceof Keyword keyword)) {
                operands.add(token);
                continue;
            }
            switch (keyword) {
                case SAVE -> {
                    if (inTextBlock || pendingRect != null || !operands.isEmpty()) {
                        return null;
                    }
                    saved.push(new GraphicsState(ctm, clipBox));
                }
                case RESTORE -> {
                    if (inTextBlock || pendingRect != null || !operands.isEmpty() || saved.isEmpty()) {
                        return null;
                    }
                    var state = saved.pop();
                    ctm = state.ctm;
                    clipBox = state.clipBox;
                }
                case CONCAT -> {
                    if (inTextBlock || pendingRect != null || operands.size() != 6) {
                        return null;
                    }
                    var matrix = matrix(operands);
                    if (matrix == null) {
                        return null;
                    }
                    ctm = multiply(matrix, ctm);
                }
                case EXT_GSTATE -> {
                    if (pendingRect != null || operands.size() != 1 || !(operands.getFirst() instanceof String gsName)) {
                        return null;
                    }
                    if (!isSafeExtGState(extGStates, gsName)) {
                        return null;
                    }
                }
                case STATE_NOOP -> {
                    if (pendingRect != null) {
                        return null;
                    }
                }
                case MARKED_CONTENT -> {
                    if (pendingRect != null) {
                        return null;
                    }
                }
                case BEGIN_TEXT -> {
                    if (inTextBlock || pendingRect != null || !operands.isEmpty()) {
                        return null;
                    }
                    inTextBlock = true;
                }
                case END_TEXT -> {
                    if (!inTextBlock || !operands.isEmpty()) {
                        return null;
                    }
                    inTextBlock = false;
                }
                case TEXT_POSITION_NOOP -> {
                    if (!inTextBlock) {
                        return null;
                    }
                }
                case RECTANGLE -> {
                    if (inTextBlock || pendingRect != null || operands.size() != 4) {
                        return null;
                    }
                    var x = operandNumber(operands.get(0));
                    var y = operandNumber(operands.get(1));
                    var w = operandNumber(operands.get(2));
                    var h = operandNumber(operands.get(3));
                    if (x == null || y == null || w == null || h == null) {
                        return null;
                    }
                    pendingRect = transformRect(ctm, x, y, w, h);
                    if (pendingRect == null) {
                        return null;
                    }
                    hasPendingClip = false;
                }
                case CLOSE_PATH -> {
                    if (pendingRect == null || !operands.isEmpty()) {
                        return null;
                    }
                }
                case CLIP -> {
                    if (pendingRect == null || !operands.isEmpty()) {
                        return null;
                    }
                    hasPendingClip = true;
                }
                case END_PATH_NOOP -> {
                    if (pendingRect == null || !operands.isEmpty()) {
                        return null;
                    }
                    if (hasPendingClip) {
                        clipBox = clipBox.intersect(pendingRect);
                        if (!clipBox.isPositive()) {
                            return null;
                        }
                    }
                    pendingRect = null;
                    hasPendingClip = false;
                }
                case PAINT -> {
                    if (inTextBlock || pendingRect != null || painted != null
                        || operands.size() != 1 || !(operands.getFirst() instanceof String name)) {
                        return null;
                    }
                    if (!(resolve(xobjects.get(name)) instanceof CosStream stream)
                        || !isName(stream.dictionary().get("Subtype"), "Image")) {
                        return null;
                    }
                    painted = new PaintedImage(stream, ctm, clipBox);
                }
                case UNKNOWN -> {
                    return null;
                }
            }
            operands.clear();
        }

        if (inTextBlock || pendingRect != null || !saved.isEmpty() || !operands.isEmpty()) {
            return null;
        }
        return painted;
    }

    private boolean isSafeExtGState(@Nullable CosDictionary extGStates, String name) {
        if (extGStates == null) {
            return false;
        }
        var state = dictionaryOrNull(extGStates.get(name));
        if (state == null) {
            return false;
        }
        if (state.get("Font") != null) {
            return false;
        }
        var smask = state.get("SMask");
        if (smask != null && !isName(smask, "None")) {
            return false;
        }
        var bm = state.get("BM");
        if (bm != null && !isName(bm, "Normal") && !isName(bm, "Compatible")) {
            return false;
        }
        if (!isOpaqueOrAbsent(state.get("ca")) || !isOpaqueOrAbsent(state.get("CA"))) {
            return false;
        }
        if (isTrue(state.get("OP")) || isTrue(state.get("op"))) {
            return false;
        }
        return true;
    }

    private boolean isOpaqueOrAbsent(@Nullable CosValue value) {
        if (value == null) {
            return true;
        }
        if (!(resolve(value) instanceof CosNumber num)) {
            return false;
        }
        var alpha = Double.parseDouble(num.lexicalValue());
        return Double.isFinite(alpha) && Math.abs(alpha - 1.0) <= ALPHA_TOLERANCE;
    }

    private boolean isTrue(@Nullable CosValue value) {
        return value != null && resolve(value) instanceof CosBoolean b && b.value();
    }

    /**
     * Combines the linear part of the image CTM with the page {@code /Rotate}
     * (clockwise degrees) to return EXIF orientation 1–8, or 0 if not strictly
     * axis-aligned.
     */
    static int exifOrientation(double[] ctm, int pageRotationDegrees) {
        double a = ctm[0], b = ctm[1], c = ctm[2], d = ctm[3];
        double scale = Math.max(Math.max(Math.abs(a), Math.abs(b)), Math.max(Math.abs(c), Math.abs(d)));
        if (!(scale > 0) || !Double.isFinite(scale)) {
            return 0;
        }
        double tol = scale * AXIS_TOLERANCE;
        // Image +col (rightward in stored pixels) maps in PDF y-up page space to (a, b).
        // Image +row (downward in stored pixels, dv = -1) maps in PDF y-up page space to (-c, -d).
        double[] right = rotateClockwise(a, b, pageRotationDegrees);
        double[] down = rotateClockwise(-c, -d, pageRotationDegrees);
        // Convert from PDF y-up visual space to screen y-down space: (x, y) -> (x, -y).
        int rx = axisSign(right[0], right[1], tol);
        int ry = axisSign(-right[1], right[0], tol);
        int dx = axisSign(down[0], down[1], tol);
        int dy = axisSign(-down[1], down[0], tol);
        if (rx != 0 && ry == 0 && dx == 0 && dy != 0) {
            if (rx > 0 && dy > 0) return 1; // NORMAL
            if (rx < 0 && dy > 0) return 2; // FLIP_HORIZONTAL
            if (rx < 0 && dy < 0) return 3; // ROTATE_180
            if (rx > 0 && dy < 0) return 4; // FLIP_VERTICAL
        } else if (rx == 0 && ry != 0 && dx != 0 && dy == 0) {
            if (ry > 0 && dx > 0) return 5; // TRANSPOSE
            if (ry > 0 && dx < 0) return 6; // ROTATE_90_CW
            if (ry < 0 && dx < 0) return 7; // TRANSVERSE
            if (ry < 0 && dx > 0) return 8; // ROTATE_270_CW
        }
        return 0;
    }

    private static double[] rotateClockwise(double x, double y, int rotationDegrees) {
        int normalized = Math.floorMod(rotationDegrees, 360);
        return switch (normalized) {
            case 0 -> new double[] {x, y};
            case 90 -> new double[] {y, -x};
            case 180 -> new double[] {-x, -y};
            case 270 -> new double[] {-y, x};
            default -> new double[] {Double.NaN, Double.NaN};
        };
    }

    private static int axisSign(double component, double orthogonal, double tol) {
        if (Math.abs(orthogonal) <= tol && Math.abs(component) > tol) {
            return component > 0 ? 1 : -1;
        }
        return 0;
    }

    private static Box transformUnitSquare(double[] ctm) {
        double x0 = ctm[4], y0 = ctm[5];
        double x1 = ctm[0] + ctm[4], y1 = ctm[1] + ctm[5];
        double x2 = ctm[2] + ctm[4], y2 = ctm[3] + ctm[5];
        double x3 = ctm[0] + ctm[2] + ctm[4], y3 = ctm[1] + ctm[3] + ctm[5];
        return new Box(
            Math.min(Math.min(x0, x1), Math.min(x2, x3)),
            Math.min(Math.min(y0, y1), Math.min(y2, y3)),
            Math.max(Math.max(x0, x1), Math.max(x2, x3)),
            Math.max(Math.max(y0, y1), Math.max(y2, y3))
        );
    }

    private static @Nullable Box transformRect(double[] ctm, double x, double y, double w, double h) {
        // Require axis-aligned CTM for clip rectangle so bounding box equals exact clip region.
        double scale = Math.max(Math.max(Math.abs(ctm[0]), Math.abs(ctm[1])),
            Math.max(Math.abs(ctm[2]), Math.abs(ctm[3])));
        if (!(scale > 0)) {
            return null;
        }
        double tol = scale * AXIS_TOLERANCE;
        boolean aligned = (Math.abs(ctm[1]) <= tol && Math.abs(ctm[2]) <= tol)
            || (Math.abs(ctm[0]) <= tol && Math.abs(ctm[3]) <= tol);
        if (!aligned) {
            return null;
        }
        double x0 = ctm[0] * x + ctm[2] * y + ctm[4];
        double y0 = ctm[1] * x + ctm[3] * y + ctm[5];
        double x1 = ctm[0] * (x + w) + ctm[2] * y + ctm[4];
        double y1 = ctm[1] * (x + w) + ctm[3] * y + ctm[5];
        double x2 = ctm[0] * x + ctm[2] * (y + h) + ctm[4];
        double y2 = ctm[1] * x + ctm[3] * (y + h) + ctm[5];
        double x3 = ctm[0] * (x + w) + ctm[2] * (y + h) + ctm[4];
        double y3 = ctm[1] * (x + w) + ctm[3] * (y + h) + ctm[5];
        return new Box(
            Math.min(Math.min(x0, x1), Math.min(x2, x3)),
            Math.min(Math.min(y0, y1), Math.min(y2, y3)),
            Math.max(Math.max(x0, x1), Math.max(x2, x3)),
            Math.max(Math.max(y0, y1), Math.max(y2, y3))
        );
    }

    private boolean hasNonEmptyArray(@Nullable CosValue value) {
        if (value == null) {
            return false;
        }
        return !(resolve(value) instanceof CosArray array) || !array.values().isEmpty();
    }

    private boolean hasNonEmptyDictionary(@Nullable CosValue value) {
        if (value == null) {
            return false;
        }
        return !(resolve(value) instanceof CosDictionary dict) || !dict.values().isEmpty();
    }

    private boolean isName(@Nullable CosValue value, String expected) {
        return value != null && resolve(value) instanceof CosName name && name.value().equals(expected);
    }

    private @Nullable CosValue resolve(@Nullable CosValue value) {
        return value == null ? null : parser.resolve(value);
    }

    private @Nullable CosDictionary dictionaryOrNull(@Nullable CosValue value) {
        return resolve(value) instanceof CosDictionary dictionary ? dictionary : null;
    }

    private double number(@Nullable CosValue value) {
        if (!(resolve(value) instanceof CosNumber number)) {
            throw new IllegalArgumentException("Expected a number");
        }
        return Double.parseDouble(number.lexicalValue());
    }

    private static @Nullable Double operandNumber(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double d = number.doubleValue();
        return Double.isFinite(d) ? d : null;
    }

    private static double @Nullable [] matrix(List<Object> operands) {
        var result = new double[6];
        for (int i = 0; i < 6; i++) {
            var num = operandNumber(operands.get(i));
            if (num == null) {
                return null;
            }
            result[i] = num;
        }
        return result;
    }

    private static double[] multiply(double[] left, double[] right) {
        return new double[] {
            left[0] * right[0] + left[1] * right[2],
            left[0] * right[1] + left[1] * right[3],
            left[2] * right[0] + left[3] * right[2],
            left[2] * right[1] + left[3] * right[3],
            left[4] * right[0] + left[5] * right[2] + right[4],
            left[4] * right[1] + left[5] * right[3] + right[5]
        };
    }

    private record GraphicsState(double[] ctm, Box clipBox) {
    }

    private record PaintedImage(CosStream imageStream, double[] ctm, Box clipBox) {
    }

    private record Box(double minX, double minY, double maxX, double maxY) {
        double width() {
            return maxX - minX;
        }

        double height() {
            return maxY - minY;
        }

        boolean isPositive() {
            return Double.isFinite(minX) && Double.isFinite(minY)
                && Double.isFinite(maxX) && Double.isFinite(maxY)
                && maxX > minX && maxY > minY;
        }

        Box intersect(Box other) {
            return new Box(
                Math.max(minX, other.minX),
                Math.max(minY, other.minY),
                Math.min(maxX, other.maxX),
                Math.min(maxY, other.maxY)
            );
        }
    }

    private enum Keyword {
        SAVE,
        RESTORE,
        CONCAT,
        EXT_GSTATE,
        STATE_NOOP,
        MARKED_CONTENT,
        BEGIN_TEXT,
        END_TEXT,
        TEXT_POSITION_NOOP,
        RECTANGLE,
        CLOSE_PATH,
        CLIP,
        END_PATH_NOOP,
        PAINT,
        UNKNOWN;

        static Keyword of(byte[] bytes, int start, int length) {
            return switch (new String(bytes, start, length, StandardCharsets.ISO_8859_1)) {
                case "q" -> SAVE;
                case "Q" -> RESTORE;
                case "cm" -> CONCAT;
                case "gs" -> EXT_GSTATE;
                case "w", "J", "j", "M", "d", "ri", "i",
                     "CS", "cs", "SC", "sc", "SCN", "scn",
                     "G", "g", "RG", "rg", "K", "k" -> STATE_NOOP;
                case "BMC", "BDC", "EMC", "MP", "DP" -> MARKED_CONTENT;
                case "BT" -> BEGIN_TEXT;
                case "ET" -> END_TEXT;
                case "Td", "TD", "Tm", "T*", "Tc", "Tw", "Tz", "TL", "Tr", "Ts" -> TEXT_POSITION_NOOP;
                case "re" -> RECTANGLE;
                case "h" -> CLOSE_PATH;
                case "W", "W*" -> CLIP;
                case "n" -> END_PATH_NOOP;
                case "Do" -> PAINT;
                default -> UNKNOWN;
            };
        }
    }
}
