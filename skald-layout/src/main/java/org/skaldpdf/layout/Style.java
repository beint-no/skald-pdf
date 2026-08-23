package org.skaldpdf.layout;

import org.jspecify.annotations.Nullable;
import org.skaldpdf.colors.Color;
import org.skaldpdf.colors.LinearGradient;
import org.skaldpdf.font.PdfFont;
import org.skaldpdf.layout.borders.Border;
import org.skaldpdf.layout.properties.HorizontalAlignment;
import org.skaldpdf.layout.properties.OverflowWrap;
import org.skaldpdf.layout.properties.TextAlignment;
import org.skaldpdf.layout.properties.UnitValue;
import org.skaldpdf.layout.properties.VerticalAlignment;

public final class Style {
    private @Nullable PdfFont font;
    private float fontSize = Float.NaN;
    private @Nullable Color fontColor;
    private @Nullable Color backgroundColor;
    private @Nullable TextAlignment textAlignment;
    private @Nullable VerticalAlignment verticalAlignment;
    private @Nullable HorizontalAlignment horizontalAlignment;
    private @Nullable Border border;
    private @Nullable Border borderTop;
    private @Nullable Border borderRight;
    private @Nullable Border borderBottom;
    private @Nullable Border borderLeft;
    private float marginTop;
    private float marginRight;
    private float marginBottom;
    private float marginLeft;
    private float paddingTop;
    private float paddingRight;
    private float paddingBottom;
    private float paddingLeft;
    private @Nullable UnitValue width;
    private float height = Float.NaN;
    private float multipliedLeading = Float.NaN;
    private OverflowWrap overflowWrap = OverflowWrap.NORMAL;
    private boolean bold;
    private boolean italic;
    private boolean underline;
    private boolean strikethrough;
    private boolean keepTogether;
    private boolean keepWithNext;
    private float borderRadius;
    private @Nullable String destinationUri;
    private int destinationPage;
    private @Nullable String namedDestination;
    private @Nullable String localDestination;
    private @Nullable LinearGradient backgroundGradient;
    private @Nullable FixedPosition fixedPosition;

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

    public @Nullable PdfFont font() {
        return font;
    }

    public void font(@Nullable PdfFont value) {
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

    public @Nullable Color backgroundColor() {
        return backgroundColor;
    }

    public void backgroundColor(@Nullable Color value) {
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

    public @Nullable Border border() {
        return border;
    }

    public void border(@Nullable Border value) {
        border = value;
    }

    public @Nullable Border borderTop() {
        return borderTop == null ? border : borderTop;
    }

    public void borderTop(@Nullable Border value) {
        borderTop = value;
    }

    public @Nullable Border borderRight() {
        return borderRight == null ? border : borderRight;
    }

    public void borderRight(@Nullable Border value) {
        borderRight = value;
    }

    public @Nullable Border borderBottom() {
        return borderBottom == null ? border : borderBottom;
    }

    public void borderBottom(@Nullable Border value) {
        borderBottom = value;
    }

    public @Nullable Border borderLeft() {
        return borderLeft == null ? border : borderLeft;
    }

    public void borderLeft(@Nullable Border value) {
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

    public @Nullable UnitValue width() {
        return width;
    }

    public void width(@Nullable UnitValue value) {
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

    public float resolvedLeading(float fallback) {
        return Float.isNaN(multipliedLeading) ? fallback : multipliedLeading;
    }

    public void multipliedLeading(float value) {
        positive(value, "Leading multiplier");
        multipliedLeading = value;
    }

    public float borderRadius() {
        return borderRadius;
    }

    public void borderRadius(float value) {
        nonNegative(value, "Border radius");
        borderRadius = value;
    }

    public @Nullable String destinationUri() {
        return destinationUri;
    }

    public void destinationUri(@Nullable String value) {
        destinationUri = value;
    }

    public int destinationPage() {
        return destinationPage;
    }

    public void destinationPage(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Destination page must be non-negative");
        }
        destinationPage = value;
    }

    public @Nullable String namedDestination() {
        return namedDestination;
    }

    public void namedDestination(@Nullable String value) {
        namedDestination = value;
    }

    public @Nullable String localDestination() {
        return localDestination;
    }

    public void localDestination(@Nullable String value) {
        localDestination = value;
    }

    public @Nullable LinearGradient backgroundGradient() {
        return backgroundGradient;
    }

    public void backgroundGradient(@Nullable LinearGradient value) {
        backgroundGradient = value;
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

    public boolean italic() {
        return italic;
    }

    public void italic(boolean value) {
        italic = value;
    }

    public boolean underline() {
        return underline;
    }

    public void underline(boolean value) {
        underline = value;
    }

    public boolean strikethrough() {
        return strikethrough;
    }

    public void strikethrough(boolean value) {
        strikethrough = value;
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

    public @Nullable FixedPosition fixedPosition() {
        return fixedPosition;
    }

    public void fixedPosition(@Nullable FixedPosition value) {
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
