package org.skaldpdf.pdf;

import org.skaldpdf.colors.Color;
import org.skaldpdf.font.PdfFont;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.geom.Rectangle;
import org.skaldpdf.image.ImageData;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A page owned by one {@link PdfDocument}. */
public final class PdfPage {
    private final PdfDocument document;
    private final StringBuilder content = new StringBuilder(512);
    private final Map<PdfFont, String> fonts = new LinkedHashMap<>();
    private final Map<PdfFont, FontUsage> fontUsage = new LinkedHashMap<>();
    private final IdentityHashMap<ImageData, String> imageNames = new IdentityHashMap<>();
    private final List<ImageResource> images = new ArrayList<>();
    private final Map<Float, String> opacities = new LinkedHashMap<>();
    private final List<LinkAnnotation> links = new ArrayList<>();
    private final List<ShadingResource> shadings = new ArrayList<>();
    private final ImportedPage importedPage;
    private PageSize pageSize;
    private Rectangle cropBox;
    private boolean ignorePageRotationForContent;
    private boolean rotationMatrixWritten;

    PdfPage(PdfDocument document, PageSize pageSize) {
        this.document = document;
        this.pageSize = pageSize;
        cropBox = new Rectangle(0, 0, pageSize.getWidth(), pageSize.getHeight());
        importedPage = null;
    }

    PdfPage(PdfDocument document, ImportedPage importedPage) {
        this.document = document;
        this.importedPage = importedPage;
        pageSize = importedPage.pageSize();
        cropBox = importedPage.cropBox();
    }

    public PageSize getPageSize() {
        return pageSize;
    }

    public Rectangle getCropBox() {
        return cropBox;
    }

    public int rotation() {
        return importedPage == null ? 0 : importedPage.rotation();
    }

    public PdfPage setIgnorePageRotationForContent(boolean value) {
        if (rotationMatrixWritten && !value) {
            throw new IllegalStateException("Page rotation handling cannot change after content is written");
        }
        ignorePageRotationForContent = value;
        return this;
    }

    public PdfResources getResources() {
        return new PdfResources(this);
    }

    public PdfContentStream newContentStreamAfter() {
        return new PdfContentStream(this);
    }

    public PdfDocument document() {
        return document;
    }

    public void append(String operators) {
        document.ensureOpen();
        writeRotationMatrixIfNeeded();
        content.append(operators);
    }

    public String registerFont(PdfFont font, PdfFont.GlyphRun run) {
        var name = fonts.computeIfAbsent(font, ignored -> uniqueResourceName("SkF", fonts.size() + 1));
        var usage = fontUsage.computeIfAbsent(font, ignored -> new FontUsage());
        var glyphs = run.glyphs();
        var codePoints = run.codePoints();
        for (int index = 0; index < glyphs.length; index++) {
            usage.glyphs.add(glyphs[index]);
            usage.unicodeByGlyph.putIfAbsent(glyphs[index], codePoints[index]);
        }
        return name;
    }

    public String registerImage(ImageData image) {
        var existing = imageNames.get(image);
        if (existing != null) {
            return existing;
        }
        var name = uniqueResourceName("SkIm", images.size() + 1);
        imageNames.put(image, name);
        images.add(new ImageResource(name, image));
        return name;
    }

    public String registerOpacity(float opacity) {
        return opacities.computeIfAbsent(opacity,
            ignored -> uniqueResourceName("SkGs", opacities.size() + 1));
    }

    public String registerAxialShading(float x0, float y0, float x1, float y1, Color start, Color end) {
        document.ensureOpen();
        var name = uniqueResourceName("SkSh", shadings.size() + 1);
        shadings.add(new ShadingResource(name, x0, y0, x1, y1, start, end));
        return name;
    }

    public PdfPage addUriLink(Rectangle bounds, String uri) {
        document.ensureOpen();
        links.add(LinkAnnotation.uri(bounds, uri));
        return this;
    }

    public PdfPage addGoToLink(Rectangle bounds, int pageNumber) {
        document.ensureOpen();
        links.add(LinkAnnotation.goTo(bounds, pageNumber));
        return this;
    }

    public PdfPage addNamedGoToLink(Rectangle bounds, String destinationName) {
        document.ensureOpen();
        links.add(LinkAnnotation.named(bounds, destinationName));
        return this;
    }

    String content() {
        return content.toString();
    }

    Map<PdfFont, String> fonts() {
        return Map.copyOf(fonts);
    }

    Map<PdfFont, FontUsage> fontUsage() {
        return Map.copyOf(fontUsage);
    }

    List<ImageResource> images() {
        return List.copyOf(images);
    }

    Map<Float, String> opacities() {
        return Map.copyOf(opacities);
    }

    List<LinkAnnotation> links() {
        return List.copyOf(links);
    }

    List<ShadingResource> shadings() {
        return List.copyOf(shadings);
    }

    ImportedPage importedPage() {
        return importedPage;
    }

    private String uniqueResourceName(String prefix, int initial) {
        var candidate = initial;
        while (importedPage != null && importedPage.resourceNames().contains(prefix + candidate)) {
            candidate++;
        }
        return prefix + candidate;
    }

    private void writeRotationMatrixIfNeeded() {
        var rotation = rotation();
        if (!ignorePageRotationForContent || rotationMatrixWritten || rotation == 0) {
            return;
        }
        content.append(switch (rotation) {
            case 90 -> "0 1 -1 0 " + NativePdfWriter.number(pageSize.getWidth()) + " 0 cm\n";
            case 180 -> "-1 0 0 -1 " + NativePdfWriter.number(pageSize.getWidth()) + ' '
                + NativePdfWriter.number(pageSize.getHeight()) + " cm\n";
            case 270 -> "0 -1 1 0 0 " + NativePdfWriter.number(pageSize.getHeight()) + " cm\n";
            default -> throw new AssertionError("Rotation was normalized by the parser");
        });
        rotationMatrixWritten = true;
    }

    static final class FontUsage {
        private final Set<Integer> glyphs = new LinkedHashSet<>();
        private final Map<Integer, Integer> unicodeByGlyph = new LinkedHashMap<>();

        Set<Integer> glyphs() {
            return Set.copyOf(glyphs);
        }

        Map<Integer, Integer> unicodeByGlyph() {
            return Map.copyOf(unicodeByGlyph);
        }
    }

    record ImageResource(String name, ImageData image) {
    }

    record ShadingResource(String name, float x0, float y0, float x1, float y1, Color start, Color end) {
    }

    record LinkAnnotation(Rectangle bounds, String uri, int destinationPage, String namedDestination) {
        LinkAnnotation {
            java.util.Objects.requireNonNull(bounds, "bounds");
            var targets = 0;
            if (uri != null) {
                targets++;
            }
            if (destinationPage > 0) {
                targets++;
            }
            if (namedDestination != null) {
                targets++;
            }
            if (targets != 1) {
                throw new IllegalArgumentException("A link must target a URI, page, or named destination");
            }
            if (uri != null && (uri.isBlank() || uri.codePoints().anyMatch(code -> code < 0x20 || code > 0x7e))) {
                throw new IllegalArgumentException("URI links must be printable ASCII");
            }
            if (namedDestination != null && (namedDestination.isBlank()
                || namedDestination.codePoints().anyMatch(code -> code < 0x20 || code > 0x7e))) {
                throw new IllegalArgumentException("Named destinations must be printable ASCII");
            }
        }

        static LinkAnnotation uri(Rectangle bounds, String uri) {
            return new LinkAnnotation(bounds, uri, 0, null);
        }

        static LinkAnnotation goTo(Rectangle bounds, int pageNumber) {
            if (pageNumber < 1) {
                throw new IllegalArgumentException("GoTo links require a 1-based page number");
            }
            return new LinkAnnotation(bounds, null, pageNumber, null);
        }

        static LinkAnnotation named(Rectangle bounds, String destinationName) {
            return new LinkAnnotation(bounds, null, 0, destinationName);
        }
    }
}
