package org.skaldpdf;

import org.skaldpdf.colors.DeviceRgb;
import org.skaldpdf.geom.PageSize;
import org.skaldpdf.layout.element.Div;
import org.skaldpdf.layout.element.Paragraph;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowDecorationTest {
    @Test
    void rendersDecoratedContentWithoutObscuringItsChildren() {
        var bytes = Pdf.create(document -> document.add(
            new Div()
                .add(new Paragraph("Visible over the background").bold())
                .setBackgroundColor(new DeviceRgb(238, 245, 241))
                .setPadding(12)
        ));

        assertDoesNotThrow(() -> PdfTestSupport.assertVisibleInk(PdfTestSupport.renderFirstPage(bytes)));
    }

    @Test
    void rejectsDecoratedContainersThatCannotBeRepresentedOnOnePage() {
        assertThrows(IllegalArgumentException.class, () -> Pdf.create(PageSize.A4, document -> document.add(
            new Div()
                .add(new Paragraph("Oversized"))
                .setBackgroundColor(new DeviceRgb(238, 245, 241))
                .setHeight(900)
        )));
    }
}
