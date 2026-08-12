package no.beint.skald.layout;

import no.beint.skald.colors.Color;
import no.beint.skald.colors.ColorConstants;
import no.beint.skald.font.PdfFont;
import no.beint.skald.font.PdfFontFactory;
import no.beint.skald.geom.Rectangle;
import no.beint.skald.layout.element.LayoutElement;
import no.beint.skald.layout.internal.LayoutEngine;
import no.beint.skald.layout.internal.PdfDrawing;
import no.beint.skald.layout.properties.TextAlignment;
import no.beint.skald.layout.properties.VerticalAlignment;
import no.beint.skald.pdf.PdfPage;
import no.beint.skald.pdf.canvas.PdfCanvas;

import java.util.Objects;

public final class Canvas implements AutoCloseable {
    private final PdfPage page;
    private final Rectangle bounds;
    private final float opacity;
    private PdfFont font = PdfFontFactory.regular();
    private float fontSize = 12f;
    private Color fontColor = ColorConstants.BLACK;

    public Canvas(PdfPage page, Rectangle bounds) {
        this.page = Objects.requireNonNull(page, "page");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.opacity = 1f;
    }

    public Canvas(PdfCanvas canvas, Rectangle bounds) {
        Objects.requireNonNull(canvas, "canvas");
        this.page = canvas.page();
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.opacity = canvas.fillOpacity();
    }

    public Canvas add(LayoutElement element) {
        LayoutEngine.renderOverlay(page.document(), page, bounds, element, opacity);
        return this;
    }

    public Canvas setFont(PdfFont value) {
        font = Objects.requireNonNull(value, "value");
        return this;
    }

    public Canvas setFontSize(float value) {
        fontSize = value;
        return this;
    }

    public Canvas setFontColor(Color value) {
        fontColor = Objects.requireNonNull(value, "value");
        return this;
    }

    public Canvas showTextAligned(String text, float x, float y, TextAlignment horizontal,
                                  VerticalAlignment vertical, float rotation) {
        var textWidth = font.getWidth(text, fontSize);
        var left = switch (horizontal) {
            case LEFT -> x;
            case CENTER -> x - textWidth / 2f;
            case RIGHT -> x - textWidth;
        };
        var baseline = switch (vertical) {
            case TOP -> y - fontSize;
            case MIDDLE -> y - fontSize * 0.35f;
            case BOTTOM -> y;
        };
        PdfDrawing.text(page.document(), page, text, font, fontSize, fontColor, left, baseline, textWidth,
            TextAlignment.LEFT, rotation, opacity);
        return this;
    }

    @Override
    public void close() {
    }
}
