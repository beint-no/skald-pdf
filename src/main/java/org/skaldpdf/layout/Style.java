package org.skaldpdf.layout;

import org.skaldpdf.colors.Color;
import org.skaldpdf.colors.ColorConstants;
import org.skaldpdf.font.PdfFont;
import org.skaldpdf.layout.borders.Border;
import org.skaldpdf.layout.properties.HorizontalAlignment;
import org.skaldpdf.layout.properties.OverflowWrap;
import org.skaldpdf.layout.properties.TextAlignment;
import org.skaldpdf.layout.properties.UnitValue;
import org.skaldpdf.layout.properties.VerticalAlignment;

public final class Style {
    private PdfFont font;
    private float fontSize = Float.NaN;
    private Color fontColor;
    private Color backgroundColor;
    private TextAlignment textAlignment;
    private VerticalAlignment verticalAlignment;
    private HorizontalAlignment horizontalAlignment;
    private Border border = null;
    private Border borderTop = null;
    private Border borderRight = null;
    private Border borderBottom = null;
    private Border borderLeft = null;
    private float marginTop;
    private float marginRight;
    private float marginBottom;
    private float marginLeft;
    private float paddingTop;
    private float paddingRight;
    private float paddingBottom;
    private float paddingLeft;
    private UnitValue width;
    private float height = Float.NaN;
    private float multipliedLeading = 1.2f;
    private OverflowWrap overflowWrap = OverflowWrap.NORMAL;
    private boolean bold;
    private boolean keepTogether;
    private boolean keepWithNext;
    private FixedPosition fixedPosition;

    public Style() {
    }

    public record FixedPosition(int pageNumber, float x, float y, float width) {
        public FixedPosition {
            if (pageNumber < 0 || !Float.isFinite(x) || !Float.isFinite(y)
                || !(width > 0) || !Float.isFinite(width)) {
                throw new IllegalArgumentException("Fixed position must have finite coordinates and positive width");
            }
        }
    }

    public PdfFont font() {
        return font;
    }

    public void font(PdfFont value) {
        font = value;
    }

    public float fontSize(float fallback) {
        return Float.isNaN(fontSize) ? fallback : fontSize;
    }

    public void setFontSize(float value) {
        positive(value, "Font size");
        fontSize = value;
    }

    public Color fontColor(Color fallback) {
        return fontColor == null ? fallback : fontColor;
    }

    public void setFontColor(Color value) {
        fontColor = value;
    }

    public Color backgroundColor() {
        return backgroundColor;
    }

    public void backgroundColor(Color value) {
        backgroundColor = value;
    }

    public TextAlignment textAlignment(TextAlignment fallback) {
        return textAlignment == null ? fallback : textAlignment;
    }

    public void setTextAlignment(TextAlignment value) {
        textAlignment = value;
    }

    public VerticalAlignment verticalAlignment(VerticalAlignment fallback) {
        return verticalAlignment == null ? fallback : verticalAlignment;
    }

    public void setVerticalAlignment(VerticalAlignment value) {
        verticalAlignment = value;
    }

    public HorizontalAlignment horizontalAlignment(HorizontalAlignment fallback) {
        return horizontalAlignment == null ? fallback : horizontalAlignment;
    }

    public void setHorizontalAlignment(HorizontalAlignment value) {
        horizontalAlignment = value;
    }

    public Border border() {
        return border;
    }

    public void border(Border value) {
        border = value;
    }

    public Border borderTop() {
        return borderTop == null ? border : borderTop;
    }

    public void borderTop(Border value) {
        borderTop = value;
    }

    public Border borderRight() {
        return borderRight == null ? border : borderRight;
    }

    public void borderRight(Border value) {
        borderRight = value;
    }

    public Border borderBottom() {
        return borderBottom == null ? border : borderBottom;
    }

    public void borderBottom(Border value) {
        borderBottom = value;
    }

    public Border borderLeft() {
        return borderLeft == null ? border : borderLeft;
    }

    public void borderLeft(Border value) {
        borderLeft = value;
    }

    public float marginTop() {
        return marginTop;
    }

    public void marginTop(float value) {
        finite(value, "Margin");
        marginTop = value;
    }

    public float marginRight() {
        return marginRight;
    }

    public void marginRight(float value) {
        finite(value, "Margin");
        marginRight = value;
    }

    public float marginBottom() {
        return marginBottom;
    }

    public void marginBottom(float value) {
        finite(value, "Margin");
        marginBottom = value;
    }

    public float marginLeft() {
        return marginLeft;
    }

    public void marginLeft(float value) {
        finite(value, "Margin");
        marginLeft = value;
    }

    public float paddingTop() {
        return paddingTop;
    }

    public void paddingTop(float value) {
        nonNegative(value, "Padding");
        paddingTop = value;
    }

    public float paddingRight() {
        return paddingRight;
    }

    public void paddingRight(float value) {
        nonNegative(value, "Padding");
        paddingRight = value;
    }

    public float paddingBottom() {
        return paddingBottom;
    }

    public void paddingBottom(float value) {
        nonNegative(value, "Padding");
        paddingBottom = value;
    }

    public float paddingLeft() {
        return paddingLeft;
    }

    public void paddingLeft(float value) {
        nonNegative(value, "Padding");
        paddingLeft = value;
    }

    public UnitValue width() {
        return width;
    }

    public void width(UnitValue value) {
        width = value;
    }

    public float height() {
        return height;
    }

    public void height(float value) {
        nonNegative(value, "Height");
        height = value;
    }

    public float multipliedLeading() {
        return multipliedLeading;
    }

    public void multipliedLeading(float value) {
        positive(value, "Leading multiplier");
        multipliedLeading = value;
    }

    public OverflowWrap overflowWrap() {
        return overflowWrap;
    }

    public void overflowWrap(OverflowWrap value) {
        overflowWrap = java.util.Objects.requireNonNull(value, "value");
    }

    public boolean bold() {
        return bold;
    }

    public void bold(boolean value) {
        bold = value;
    }

    public boolean keepTogether() {
        return keepTogether;
    }

    public void keepTogether(boolean value) {
        keepTogether = value;
    }

    public boolean keepWithNext() {
        return keepWithNext;
    }

    public void keepWithNext(boolean value) {
        keepWithNext = value;
    }

    public FixedPosition fixedPosition() {
        return fixedPosition;
    }

    public void fixedPosition(FixedPosition value) {
        fixedPosition = value;
    }

    private static void positive(float value, String name) {
        if (!(value > 0) || !Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }

    private static void nonNegative(float value, String name) {
        if (value < 0 || !Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be non-negative and finite");
        }
    }

    private static void finite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
