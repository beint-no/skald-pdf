package no.beint.skald.layout.element;

import no.beint.skald.colors.Color;
import no.beint.skald.font.PdfFont;
import no.beint.skald.layout.Style;
import no.beint.skald.layout.borders.Border;
import no.beint.skald.layout.properties.HorizontalAlignment;
import no.beint.skald.layout.properties.TextAlignment;
import no.beint.skald.layout.properties.UnitValue;
import no.beint.skald.layout.properties.VerticalAlignment;

public abstract non-sealed class AbstractElement<T extends AbstractElement<T>> implements LayoutElement {
    private final Style style = new Style();

    protected abstract T self();

    @Override
    public final Style style() {
        return style;
    }

    public final T setFont(PdfFont value) {
        style.font(value);
        return self();
    }

    public final T setFontSize(float value) {
        style.setFontSize(value);
        return self();
    }

    public final T setFontColor(Color value) {
        style.setFontColor(value);
        return self();
    }

    public final T simulateBold() {
        style.simulatedBold(true);
        return self();
    }

    public final T setTextAlignment(TextAlignment value) {
        style.setTextAlignment(value);
        return self();
    }

    public final T setVerticalAlignment(VerticalAlignment value) {
        style.setVerticalAlignment(value);
        return self();
    }

    public final T setHorizontalAlignment(HorizontalAlignment value) {
        style.setHorizontalAlignment(value);
        return self();
    }

    public final T setBackgroundColor(Color value) {
        style.backgroundColor(value);
        return self();
    }

    public final T setBorder(Border value) {
        style.border(value);
        return self();
    }

    public final T setBorderTop(Border value) {
        style.borderTop(value);
        return self();
    }

    public final T setBorderRight(Border value) {
        style.borderRight(value);
        return self();
    }

    public final T setBorderBottom(Border value) {
        style.borderBottom(value);
        return self();
    }

    public final T setBorderLeft(Border value) {
        style.borderLeft(value);
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
        style.width(value);
        return self();
    }

    public final UnitValue getWidth() {
        return style.width();
    }

    public final T setHeight(float value) {
        style.height(value);
        return self();
    }

    public final T setMaxWidth(float value) {
        style.maxWidth(value);
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

    public final T setProperty(int property, Object value) {
        return self();
    }
}
