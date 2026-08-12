# Skald PDF

Skald PDF is a focused PDF 2.0 generation and composition library for JDK 25+.
It provides flowing text, repeating tables, images, EAN-13 barcodes, page events,
watermarks, stamping, and merging with zero external runtime dependencies.

The library deliberately targets current JVMs and the current PDF specification.
It does not carry compatibility layers for old Java releases or obsolete PDF
writer modes.

## Install

```kotlin
implementation("org.skaldpdf:skald-pdf:0.2.0")
```

Skald is a named Java module:

```java
requires org.skaldpdf;
```

## Create a document

```java
import org.skaldpdf.Pdf;
import org.skaldpdf.layout.element.Cell;
import org.skaldpdf.layout.element.Paragraph;
import org.skaldpdf.layout.element.Table;
import org.skaldpdf.layout.properties.UnitValue;

byte[] bytes = Pdf.create(document -> {
    document.setMargins(40, 40, 40, 40);
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

The facade also covers the common composition paths:

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

Use `PdfDocument`, `PdfWriter`, and `PdfReader` directly when page events or
lower-level drawing are needed.

## Design

- Every new file is PDF 2.0. Input composition accepts structurally valid PDF
  1.x and 2.0 files, but output is normalized to PDF 2.0.
- The production module depends only on `java.base` and `java.desktop`.
- Regular and bold Unicode TrueType fonts are bundled, subset once per document,
  embedded, and mapped for reliable text extraction.
- Layout consumes one top-level element at a time. Tables repeat headers and long
  paragraphs or rows fragment across pages.
- Content, metadata, font programs, lossless images, object streams, and xref
  streams use Deflate compression. JPEG data passes through without a lossy
  decode/re-encode cycle. Lossless images use adaptive PNG predictors; alpha is
  represented by a soft mask.
- Images and opacity states are deduplicated across pages by identity.
- Mutable documents are thread-confined. Immutable fonts and image inputs can be
  shared; separate documents work naturally on virtual threads.
- Parsing is bounded by size, page, object, nesting, and decoded-stream limits.
  Encrypted inputs are rejected rather than partially interpreted.

See [standards](docs/standards.md), [capabilities](docs/capabilities.md),
[security](docs/security.md), and [performance](docs/performance.md).

## Build and verify

JDK 25 or newer is required.

```shell
./gradlew clean build
scripts/validate-pdf2.sh build/use-case-pdfs
```

The test suite creates a representative PDF corpus in `build/use-case-pdfs`,
then parses, extracts text, renders pages, and scans the barcode with independent
test tools. The validation script adds qpdf checks and Arlington PDF 2.0 rules.

## Scope

Skald is already suited to transactional documents, statements, receipts,
agreements, reports, payslips, raster attachments, and page composition. The
current intentional exclusions are interactive forms, encryption, signatures,
rich text/HTML/SVG conversion, complex-script shaping, tagged PDF/PDF/UA, and
PDF/A profiles. These require dedicated conformance work rather than superficial
API coverage.

## License

Apache License 2.0. The bundled font programs are licensed separately under the
SIL Open Font License 1.1; see `META-INF/licenses` in the published artifact.
