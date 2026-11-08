package org.skaldpdf.pdf;

import static org.skaldpdf.pdf.CosValue.CosArray;
import static org.skaldpdf.pdf.CosValue.CosDictionary;
import static org.skaldpdf.pdf.CosValue.CosName;
import static org.skaldpdf.pdf.CosValue.CosReference;
import static org.skaldpdf.pdf.CosValue.CosStream;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which glyph IDs page content draws from each embedded TrueType program.
 *
 * <p>Only composite fonts with an {@code Identity-H} or {@code Identity-V} encoding and
 * a {@code CIDFontType2} descendant qualify: their two-byte codes are CIDs, and the
 * CIDs map to glyph IDs through {@code /CIDToGIDMap}, so the drawn glyphs follow from
 * the text-showing operators alone. Every {@code Tf}, {@code Tj}, {@code TJ},
 * {@code '} and {@code "} on imported pages and the Forms they paint is followed
 * through {@code q} and {@code Q}.
 *
 * <p>The answer is deliberately conservative. A program is left out when any font
 * that uses it is reachable from interactive form resources, annotation appearances,
 * patterns, graphics states, Type3 glyphs or content that does not parse cleanly,
 * when a font that uses it is not a qualifying composite font, or when page content
 * never selects one of its fonts.
 */
final class FontGlyphUsage {
    private static final int MAX_FORM_DEPTH = 16;

    private final NativePdfParser parser;
    private final Map<CosDictionary, BitSet> cids = new IdentityHashMap<>();
    private final Set<CosDictionary> unknown = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<CosDictionary> inspected = Collections.newSetFromMap(new IdentityHashMap<>());

    private FontGlyphUsage(NativePdfParser parser) {
        this.parser = parser;
    }

    static Map<CosStream, BitSet> analyze(NativePdfParser parser) {
        var usage = new FontGlyphUsage(parser);
        for (var page : parser.pages()) {
            usage.page(page);
        }
        usage.markInteractiveFormFonts();
        return usage.glyphsByProgram();
    }

    private void page(ImportedPage page) {
        var resources = dictionaryOrNull(page.dictionary().get("Resources"));
        if (resources != null) {
            try {
                walk(parser.contentBytes(page), resources, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
            } catch (RuntimeException unfollowable) {
                markReachableUnknown(resources, Collections.newSetFromMap(new IdentityHashMap<>()));
            }
        }
        if (resolve(page.dictionary().get("Annots")) instanceof CosArray annotations) {
            for (var value : annotations.values()) {
                if (resolve(value) instanceof CosDictionary annotation
                    && resolve(annotation.get("AP")) instanceof CosDictionary appearances) {
                    for (var appearance : appearances.values().values()) {
                        markAppearanceUnknown(resolve(appearance));
                    }
                }
            }
        }
    }

    private void walk(byte[] content, CosDictionary resources, int depth, Set<CosStream> forms) {
        inspectIndirectUses(resources);
        var scanner = new ContentScanner<>(content, Keyword::of, Keyword.OTHER);
        var operands = new ArrayList<Object>();
        var saved = new ArrayDeque<CosDictionary[]>();
        CosDictionary[] font = {null};
        for (var token = scanner.next(); token != null; token = scanner.next()) {
            if (!(token instanceof Keyword keyword)) {
                operands.add(token);
                continue;
            }
            switch (keyword) {
                case SAVE -> saved.push(font.clone());
                case RESTORE -> {
                    if (saved.isEmpty()) {
                        throw new IllegalArgumentException("Unbalanced Q");
                    }
                    font = saved.pop();
                }
                case SET_FONT -> font[0] = font(resources, operands);
                case SHOW -> show(font[0], last(operands));
                case SHOW_SPACED -> {
                    if (operands.size() != 3) {
                        throw new IllegalArgumentException("\" needs three operands");
                    }
                    show(font[0], operands.get(2));
                }
                case SHOW_ARRAY -> {
                    if (!(last(operands) instanceof List<?> items)) {
                        throw new IllegalArgumentException("TJ needs an array");
                    }
                    for (var item : items) {
                        if (item instanceof byte[]) {
                            show(font[0], item);
                        }
                    }
                }
                case GRAPHICS_STATE -> graphicsState(resources, operands);
                case PAINT -> paint(operands, resources, depth, forms);
                case INLINE_IMAGE -> scanner.skipInlineImage();
                case OTHER -> {
                }
            }
            operands.clear();
        }
    }

    private CosDictionary font(CosDictionary resources, List<Object> operands) {
        if (operands.size() != 2 || !(operands.getFirst() instanceof String name)) {
            throw new IllegalArgumentException("Tf needs a name and a size");
        }
        var fonts = dictionaryOrNull(resources.get("Font"));
        if (fonts == null || !(resolve(fonts.get(name)) instanceof CosDictionary font)) {
            throw new IllegalArgumentException("Tf names a missing font");
        }
        return font;
    }

    private void show(CosDictionary font, Object operand) {
        if (font == null || !(operand instanceof byte[] bytes)) {
            throw new IllegalArgumentException("Text shown without a font or string");
        }
        var used = cids.computeIfAbsent(font, ignored -> new BitSet());
        if (!identityComposite(font)) {
            return;
        }
        if (bytes.length % 2 != 0) {
            unknown.add(font);
            return;
        }
        for (int index = 0; index < bytes.length; index += 2) {
            used.set((bytes[index] & 0xff) << 8 | bytes[index + 1] & 0xff);
        }
    }

    /** A graphics state that sets a font changes the font outside {@code Tf}; such pages are not followed. */
    private void graphicsState(CosDictionary resources, List<Object> operands) {
        if (operands.size() != 1 || !(operands.getFirst() instanceof String name)) {
            throw new IllegalArgumentException("gs needs one name");
        }
        var states = dictionaryOrNull(resources.get("ExtGState"));
        var state = states == null ? null : dictionaryOrNull(states.get(name));
        if (state == null || state.get("Font") != null) {
            throw new IllegalArgumentException("gs names a missing state or sets a font");
        }
    }

    private void paint(List<Object> operands, CosDictionary resources, int depth, Set<CosStream> forms) {
        if (operands.size() != 1 || !(operands.getFirst() instanceof String name)) {
            throw new IllegalArgumentException("Do needs one name operand");
        }
        var xobjects = dictionaryOrNull(resources.get("XObject"));
        var value = xobjects == null ? null : resolve(xobjects.get(name));
        if (!(value instanceof CosStream stream)) {
            throw new IllegalArgumentException("Do names a missing XObject");
        }
        if (resolve(stream.dictionary().get("Subtype")) instanceof CosName form && form.value().equals("Form")) {
            if (depth >= MAX_FORM_DEPTH || !forms.add(stream)) {
                throw new IllegalArgumentException("Form nesting is too deep or cyclic");
            }
            var formResources = dictionaryOrNull(stream.dictionary().get("Resources"));
            walk(parser.decodedStream(stream, "Form XObject"), formResources == null ? resources : formResources,
                depth + 1, forms);
            forms.remove(stream);
        }
    }

    private boolean identityComposite(CosDictionary font) {
        return program(font) != null;
    }

    /** The TrueType program of a qualifying composite font, or null. */
    private CosStream program(CosDictionary font) {
        if (!isName(font.get("Subtype"), "Type0")
            || !(isName(font.get("Encoding"), "Identity-H") || isName(font.get("Encoding"), "Identity-V"))
            || !(resolve(font.get("DescendantFonts")) instanceof CosArray descendants)
            || descendants.values().size() != 1
            || !(resolve(descendants.values().getFirst()) instanceof CosDictionary cidFont)
            || !isName(cidFont.get("Subtype"), "CIDFontType2")
            || !(resolve(cidFont.get("FontDescriptor")) instanceof CosDictionary descriptor)
            || !(resolve(descriptor.get("FontFile2")) instanceof CosStream program)) {
            return null;
        }
        var map = cidFont.get("CIDToGIDMap");
        if (map != null && !isName(map, "Identity") && !(resolve(map) instanceof CosStream)) {
            return null;
        }
        return program;
    }

    private BitSet glyphs(CosDictionary font, BitSet used) {
        var descendant = (CosDictionary) resolve(((CosArray) resolve(font.get("DescendantFonts"))).values().getFirst());
        var map = descendant.get("CIDToGIDMap");
        if (map == null || isName(map, "Identity")) {
            return (BitSet) used.clone();
        }
        var bytes = parser.decodedStream((CosStream) resolve(map), "CIDToGIDMap");
        var glyphs = new BitSet();
        for (int cid = used.nextSetBit(0); cid >= 0; cid = used.nextSetBit(cid + 1)) {
            glyphs.set(2 * cid + 1 < bytes.length ? (bytes[2 * cid] & 0xff) << 8 | bytes[2 * cid + 1] & 0xff : 0);
        }
        return glyphs;
    }

    /**
     * Joins the drawn glyphs per program. Every font in the document that names a
     * program must be a qualifying composite font that page content uses and that no
     * unfollowed context reaches; otherwise the program is left out.
     */
    private Map<CosStream, BitSet> glyphsByProgram() {
        var users = new IdentityHashMap<CosStream, List<CosDictionary>>();
        var excluded = Collections.newSetFromMap(new IdentityHashMap<CosStream, Boolean>());
        for (var font : documentFonts()) {
            var program = program(font);
            if (program != null) {
                users.computeIfAbsent(program, ignored -> new ArrayList<>()).add(font);
            } else {
                excluded.addAll(programsOf(font));
            }
        }
        var result = new IdentityHashMap<CosStream, BitSet>();
        for (var entry : users.entrySet()) {
            if (excluded.contains(entry.getKey())) {
                continue;
            }
            var glyphs = new BitSet();
            var usable = true;
            for (var font : entry.getValue()) {
                var used = cids.get(font);
                if (used == null || unknown.contains(font)) {
                    usable = false;
                    break;
                }
                glyphs.or(glyphs(font, used));
            }
            if (usable) {
                result.put(entry.getKey(), glyphs);
            }
        }
        return result;
    }

    /** Every font dictionary reachable from the trailer, wherever it is used. */
    private List<CosDictionary> documentFonts() {
        var fonts = new ArrayList<CosDictionary>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<CosValue, Boolean>());
        var references = new java.util.HashSet<CosReference>();
        var pending = new ArrayDeque<CosValue>();
        pending.add(parser.trailer());
        while (!pending.isEmpty()) {
            var value = pending.removeLast();
            if (value instanceof CosReference reference) {
                if (references.add(reference)) {
                    pending.add(parser.resolve(reference));
                }
                continue;
            }
            if (!seen.add(value)) {
                continue;
            }
            if (value instanceof CosArray array) {
                pending.addAll(array.values());
                continue;
            }
            var dictionary = value instanceof CosStream stream ? stream.dictionary()
                : value instanceof CosDictionary plain ? plain : null;
            if (dictionary == null) {
                continue;
            }
            if (isName(dictionary.get("Type"), "Font") && !isName(dictionary.get("Subtype"), "CIDFontType2")
                && !isName(dictionary.get("Subtype"), "CIDFontType0")) {
                fonts.add(dictionary);
            }
            pending.addAll(dictionary.values().values());
        }
        return fonts;
    }

    /** TrueType programs a non-qualifying font names, directly or through its descendant. */
    private List<CosStream> programsOf(CosDictionary font) {
        var result = new ArrayList<CosStream>();
        var candidates = new ArrayList<CosDictionary>();
        candidates.add(font);
        if (resolve(font.get("DescendantFonts")) instanceof CosArray descendants) {
            for (var value : descendants.values()) {
                if (resolve(value) instanceof CosDictionary descendant) {
                    candidates.add(descendant);
                }
            }
        }
        for (var candidate : candidates) {
            if (resolve(candidate.get("FontDescriptor")) instanceof CosDictionary descriptor
                && resolve(descriptor.get("FontFile2")) instanceof CosStream program) {
                result.add(program);
            }
        }
        return result;
    }

    private void markInteractiveFormFonts() {
        var catalog = resolve(parser.trailer().values().get("Root"));
        if (catalog instanceof CosDictionary root && resolve(root.get("AcroForm")) instanceof CosDictionary form) {
            var resources = dictionaryOrNull(form.get("DR"));
            if (resources != null) {
                markReachableUnknown(resources, Collections.newSetFromMap(new IdentityHashMap<>()));
            }
            if (resolve(form.get("Fields")) instanceof CosArray fields) {
                markFieldFonts(fields, Collections.newSetFromMap(new IdentityHashMap<>()));
            }
        }
    }

    private void markFieldFonts(CosArray fields, Set<CosDictionary> visited) {
        for (var value : fields.values()) {
            if (!(resolve(value) instanceof CosDictionary field) || !visited.add(field)) {
                continue;
            }
            var resources = dictionaryOrNull(field.get("DR"));
            if (resources != null) {
                markReachableUnknown(resources, Collections.newSetFromMap(new IdentityHashMap<>()));
            }
            if (resolve(field.get("AP")) instanceof CosDictionary appearances) {
                for (var appearance : appearances.values().values()) {
                    markAppearanceUnknown(resolve(appearance));
                }
            }
            if (resolve(field.get("Kids")) instanceof CosArray kids) {
                markFieldFonts(kids, visited);
            }
        }
    }

    private void inspectIndirectUses(CosDictionary resources) {
        if (inspected.add(resources)) {
            for (var nested : indirectResources(resources)) {
                markReachableUnknown(nested, Collections.newSetFromMap(new IdentityHashMap<>()));
            }
            var states = dictionaryOrNull(resources.get("ExtGState"));
            if (states != null) {
                for (var value : states.values().values()) {
                    if (resolve(value) instanceof CosDictionary state
                        && resolve(state.get("Font")) instanceof CosArray setting
                        && !setting.values().isEmpty()
                        && resolve(setting.values().getFirst()) instanceof CosDictionary font) {
                        unknown.add(font);
                    }
                }
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
                var nested = dictionaryOrNull(dictionary.get("Resources"));
                if (nested != null) {
                    result.add(nested);
                }
                if (resolve(dictionary.get("SMask")) instanceof CosDictionary softMask
                    && resolve(softMask.get("G")) instanceof CosStream maskGroup) {
                    var groupResources = dictionaryOrNull(maskGroup.dictionary().get("Resources"));
                    if (groupResources != null) {
                        result.add(groupResources);
                    }
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
        var fonts = dictionaryOrNull(resources.get("Font"));
        if (fonts != null) {
            for (var value : fonts.values().values()) {
                if (resolve(value) instanceof CosDictionary font) {
                    unknown.add(font);
                }
            }
        }
        var xobjects = dictionaryOrNull(resources.get("XObject"));
        if (xobjects != null) {
            for (var value : xobjects.values().values()) {
                if (resolve(value) instanceof CosStream stream) {
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

    private static Object last(List<Object> operands) {
        if (operands.size() != 1) {
            throw new IllegalArgumentException("Text operator needs one operand");
        }
        return operands.getFirst();
    }

    private CosValue resolve(CosValue value) {
        return value == null ? null : parser.resolve(value);
    }

    private CosDictionary dictionaryOrNull(CosValue value) {
        return resolve(value) instanceof CosDictionary dictionary ? dictionary : null;
    }

    private boolean isName(CosValue value, String name) {
        return resolve(value) instanceof CosName candidate && candidate.value().equals(name);
    }

    private enum Keyword {
        SAVE, RESTORE, SET_FONT, SHOW, SHOW_SPACED, SHOW_ARRAY, GRAPHICS_STATE, PAINT, INLINE_IMAGE, OTHER;

        static Keyword of(byte[] bytes, int start, int length) {
            if (length == 1) {
                return switch (bytes[start]) {
                    case 'q' -> SAVE;
                    case 'Q' -> RESTORE;
                    case '\'' -> SHOW;
                    case '"' -> SHOW_SPACED;
                    default -> OTHER;
                };
            }
            if (length != 2) {
                return OTHER;
            }
            var first = bytes[start];
            var second = bytes[start + 1];
            if (first == 'T') {
                return switch (second) {
                    case 'f' -> SET_FONT;
                    case 'j' -> SHOW;
                    case 'J' -> SHOW_ARRAY;
                    default -> OTHER;
                };
            }
            if (first == 'g' && second == 's') {
                return GRAPHICS_STATE;
            }
            if (first == 'D' && second == 'o') {
                return PAINT;
            }
            return first == 'B' && second == 'I' ? INLINE_IMAGE : OTHER;
        }
    }
}
