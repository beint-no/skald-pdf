package org.skaldpdf.layout.element;

import org.jspecify.annotations.Nullable;
import org.skaldpdf.colors.Color;
import org.skaldpdf.colors.LinearGradient;
import org.skaldpdf.font.PdfFont;
import org.skaldpdf.layout.Style;
import org.skaldpdf.layout.borders.Border;
import org.skaldpdf.layout.properties.HorizontalAlignment;
import org.skaldpdf.layout.properties.OverflowWrap;
import org.skaldpdf.layout.properties.TextAlignment;
import org.skaldpdf.layout.properties.UnitValue;
import org.skaldpdf.layout.properties.VerticalAlignment;

import java.util.Objects;

public abstract non-sealed class AbstractElement<T extends AbstractElement<T>> implements LayoutElement {
    private final Style style = new Style();

    protected AbstractElement() {
    }

    protected abstract T self();

    @Override
    public final Style style() {
        return style;
    }

    public final T setFont(PdfFont value) {
        style.font(Objects.requireNonNull(value, "value"));
        return self();
    }

    public final T setFontSize(float value) {
        style.setFontSize(value);
        return self();
    }

    public final T setFontColor(Color value) {
        style.setFontColor(Objects.requireNonNull(value, "value"));
        return self();
    }

    public final T bold() {
        style.bold(true);
        return self();
    }

    public final T italic() {
        style.italic(true);
        return self();
    }

    public final T underline() {
        style.underline(true);
        return self();
    }

    public final T strikethrough() {
        style.strikethrough(true);
        return self();
    }

    public final T setTextAlignment(TextAlignment value) {
        style.setTextAlignment(Objects.requireNonNull(value, "value"));
        return self();
    }

    public final T setVerticalAlignment(VerticalAlignment value) {
        style.setVerticalAlignment(Objects.requireNonNull(value, "value"));
        return self();
    }

    public final T setHorizontalAlignment(HorizontalAlignment value) {
        style.setHorizontalAlignment(Objects.requireNonNull(value, "value"));
        return self();
    }

    public final T setBackgroundColor(Color value) {
        style.backgroundColor(Objects.requireNonNull(value, "value"));
        return self();
    }

    public final T setBorder(Border value) {
        style.border(Objects.requireNonNull(value, "value"));
        return self();
    }

    public final T setBorderTop(Border value) {
        style.borderTop(Objects.requireNonNull(value, "value"));
        return self();
    }

    public final T setBorderRight(Border value) {
        style.borderRight(Objects.requireNonNull(value, "value"));
        return self();
    }

    public final T setBorderBottom(Border value) {
        style.borderBottom(Objects.requireNonNull(value, "value"));
        return self();
    }

    public final T setBorderLeft(Border value) {
        style.borderLeft(Objects.requireNonNull(value, "value"));
        return self();
    }

    public final T setMargin(float value) {
        style.marginTop(value);
        style.marginRight(value);
        style.marginBottom(value);
        style.marginLeft(value);
        return self();
    }

    public final T setMarginTop(float value) {
        style.marginTop(value);
        return self();
    }

    public final T setMarginRight(float value) {
        style.marginRight(value);
        return self();
    }

    public final T setMarginBottom(float value) {
        style.marginBottom(value);
        return self();
    }

    public final T setMarginLeft(float value) {
        style.marginLeft(value);
        return self();
    }

    public final T setPadding(float value) {
        style.paddingTop(value);
        style.paddingRight(value);
        style.paddingBottom(value);
        style.paddingLeft(value);
        return self();
    }

    public final T setPaddingTop(float value) {
        style.paddingTop(value);
        return self();
    }

    public final T setPaddingRight(float value) {
        style.paddingRight(value);
        return self();
    }

    public final T setPaddingBottom(float value) {
        style.paddingBottom(value);
        return self();
    }

    public final T setPaddingLeft(float value) {
        style.paddingLeft(value);
        return self();
    }

    public final T setWidth(UnitValue value) {
        style.width(Objects.requireNonNull(value, "value"));
        return self();
    }

    public final @Nullable UnitValue getWidth() {
        return style.width();
    }

    public final T setHeight(float value) {
        style.height(value);
        return self();
    }

    public final T setOverflowWrap(OverflowWrap value) {
        style.overflowWrap(value);
        return self();
    }

    public final T setMultipliedLeading(float value) {
        style.multipliedLeading(value);
        return self();
    }

    public final T setKeepTogether(boolean value) {
        style.keepTogether(value);
        return self();
    }

    public final T setKeepWithNext(boolean value) {
        style.keepWithNext(value);
        return self();
    }

    public final T setFixedPosition(float x, float y, float width) {
        style.fixedPosition(new Style.FixedPosition(0, x, y, width));
        return self();
    }

    public final T setFixedPosition(int pageNumber, float x, float y, float width) {
        style.fixedPosition(new Style.FixedPosition(pageNumber, x, y, width));
        return self();
    }

    public final T setBorderRadius(float value) {
        style.borderRadius(value);
        return self();
    }

    public final T setDestinationUri(String value) {
        style.destinationUri(Objects.requireNonNull(value, "value"));
        return self();
    }

    public final T setDestinationPage(int pageNumber) {
        style.destinationPage(pageNumber);
        return self();
    }

    public final T setNamedDestination(String name) {
        style.namedDestination(Objects.requireNonNull(name, "name"));
        return self();
    }

    public final T setLocalDestination(String name) {
        style.localDestination(Objects.requireNonNull(name, "name"));
        return self();
    }

    public final T setBackground(LinearGradient value) {
        style.backgroundGradient(Objects.requireNonNull(value, "value"));
        return self();
    }

}
