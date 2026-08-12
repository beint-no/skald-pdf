package no.beint.skald.layout;

import no.beint.skald.colors.Color;
import no.beint.skald.colors.ColorConstants;
import no.beint.skald.font.PdfFont;
import no.beint.skald.layout.borders.Border;
import no.beint.skald.layout.properties.HorizontalAlignment;
import no.beint.skald.layout.properties.TextAlignment;
import no.beint.skald.layout.properties.UnitValue;
import no.beint.skald.layout.properties.VerticalAlignment;

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
    private float maxWidth = Float.NaN;
    private float multipliedLeading = 1.2f;
    private boolean simulatedBold;
    private boolean keepTogether;
    private boolean keepWithNext;
    private FixedPosition fixedPosition;

    public record FixedPosition(int pageNumber, float x, float y, float width) {
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
        marginTop = value;
    }

    public float marginRight() {
        return marginRight;
    }

    public void marginRight(float value) {
        marginRight = value;
    }

    public float marginBottom() {
        return marginBottom;
    }

    public void marginBottom(float value) {
        marginBottom = value;
    }

    public float marginLeft() {
        return marginLeft;
    }

    public void marginLeft(float value) {
        marginLeft = value;
    }

    public float paddingTop() {
        return paddingTop;
    }

    public void paddingTop(float value) {
        paddingTop = value;
    }

    public float paddingRight() {
        return paddingRight;
    }

    public void paddingRight(float value) {
        paddingRight = value;
    }

    public float paddingBottom() {
        return paddingBottom;
    }

    public void paddingBottom(float value) {
        paddingBottom = value;
    }

    public float paddingLeft() {
        return paddingLeft;
    }

    public void paddingLeft(float value) {
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
        height = value;
    }

    public float maxWidth() {
        return maxWidth;
    }

    public void maxWidth(float value) {
        maxWidth = value;
    }

    public float multipliedLeading() {
        return multipliedLeading;
    }

    public void multipliedLeading(float value) {
        multipliedLeading = value;
    }

    public boolean simulatedBold() {
        return simulatedBold;
    }

    public void simulatedBold(boolean value) {
        simulatedBold = value;
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
}
