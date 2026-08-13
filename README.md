# Skald PDF

Skald PDF is a modern Java library for creating and composing PDF 2.0 documents.
It targets JDK 25+, uses named Java modules, and has no third-party runtime
dependencies.

Use it for invoices, statements, reports, receipts, agreements, labels, and
other generated documents that do not need historical PDF output modes.

## Highlights

- Native PDF 2.0 writer and bounded parser
- Unicode TrueType fonts with embedding and subsetting
- Flowing paragraphs, lists, justified text, divisions, repeating tables, and automatic pagination
- Running headers and footers with page numbers, outlines, and URI links
- JPEG pass-through, lossless raster compression, alpha, and image deduplication
- Rounded surfaces, dashed rules, and axial gradients
- EAN-13 and Code 128 barcodes in an optional module
- Page events, drawing, watermarks, stamping, merging, and page import
- Object streams, compact CID widths, xref streams, XMP metadata, and configurable Deflate compression
- Independent rendering, extraction, barcode, syntax, and PDF 2.0 validation tests

## Modules

| Artifact | Java module | Use it for |
|---|---|---|
| `skald-core` | `org.skaldpdf.core` | Low-level writing, reading, fonts, images, and composition |
| `skald-layout` | `org.skaldpdf.layout` | Flow layout and the high-level `Pdf` API |
| `skald-barcode` | `org.skaldpdf.barcode` | Immutable EAN-13 and Code 128 image sources |

`skald-layout` and `skald-barcode` each depend on core, but not on one another.
An application only pays for the capabilities it selects. The complete runtime
still depends solely on the JDK.

## Build and install

JDK 25 or newer is required. Release `1.0.1` is on Maven Central:

```kotlin
dependencies {
    implementation("no.beint.skaldpdf:skald-layout:1.0.1")
    implementation("no.beint.skaldpdf:skald-barcode:1.0.1") // optional
}
```

To install a source checkout locally:

```shell
./gradlew clean build publishToMavenLocal
```

For a modular application:

```java
requires org.skaldpdf.layout;
requires org.skaldpdf.barcode; // optional
```

## Create a document

```java
import org.skaldpdf.Pdf;
import org.skaldpdf.layout.element.Cell;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.properties.TextAlignment;
import org.skaldpdf.layout.properties.UnitValue;

byte[] invoice = Pdf.create(document -> {
    document.setMargins(40, 40, 40, 40);
    document.setFooter(18, page -> new Paragraph(page.pageNumber() + " / " + page.pageCount())
        .setTextAlignment(TextAlignment.CENTER));
    document.add(new Paragraph("Invoice 2026-1001").bold().setFontSize(20));

    var lines = new Table(UnitValue.createPercentArray(new float[] {3, 1}))
        .useAllAvailableWidth()
        .addHeaderCell(new Cell().add(new Paragraph("Description").bold()))
        .addHeaderCell(new Cell().add(new Paragraph("Amount").bold()))
        .addCell("Consulting")
        .addCell("1 250.00");
    document.add(lines);
});
```

Add an EAN-13 barcode without coupling layout to the barcode implementation:

```java
import org.skaldpdf.barcode.Ean13Barcode;
import org.skaldpdf.layout.element.Image;

var barcode = new Ean13Barcode("590123412345")
    .withModuleWidth(1.2f)
    .withBarHeight(48f);
document.add(new Image(barcode).scaleToFit(260, 110));
```

Clothing / product stickers (the ecomtools 93 mm × 35 mm label) are a single call:

```java
import org.skaldpdf.barcode.ProductSticker;

byte[] sticker = ProductSticker.pdf(new ProductSticker.Spec(
    "SOJA-BA-L", "CN", "Softy Jacket", "L", "",
    "80%Nylon, 20%Lycra", "8123613319580", "Orchid"
));
```

The high-level facade also handles common composition paths:

```java
byte[] joined = Pdf.merge(List.of(cover, attachment));

byte[] stamped = Pdf.rewrite(joined, pdf -> {
    var page = pdf.getPage(1);
    new Canvas(page, page.getCropBox()).showTextAligned(
        "Reviewed", 36, 24,
        TextAlignment.LEFT, VerticalAlignment.BOTTOM, 0
    );
});
```

Use `PdfDocument`, `PdfWriter`, and `PdfReader` directly for page events or
low-level drawing.

## PDF policy

Every new file is PDF 2.0. There is no PDF 1.x output switch. Composition accepts
unencrypted PDF 1.x and 2.0 input because received files are not under the
application's control; newly saved output is normalized to PDF 2.0.

Mutable documents are thread-confined. Immutable inputs can be shared, and
separate documents work naturally on virtual threads. Encrypted input and
unsupported structural features fail closed instead of being partially read.

See the [capability matrix](docs/capabilities.md), [architecture](docs/architecture.md),
[standards policy](docs/standards.md), [security model](docs/security.md),
[performance notes](docs/performance.md), and [roadmap](ROADMAP.md).

## Verify generated PDFs

```shell
./gradlew clean build
scripts/validate-pdf2.sh build/use-case-pdfs
```

The test suite produces a representative corpus in `build/use-case-pdfs` and
checks parsing, text extraction, rendering, font embedding, pagination,
composition, compression, and barcode decoding. The validation script adds qpdf
syntax checks and the Arlington PDF 2.0 rules.

## Contributing and license

Contributions are welcome; start with [CONTRIBUTING.md](CONTRIBUTING.md).
Skald PDF is licensed under the [Apache License 2.0](LICENSE). Bundled IBM Plex
font programs are separately available under the SIL Open Font License 1.1.
