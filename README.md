# Skald PDF

Skald PDF is Beint's focused PDF generation and composition library for JDK 25 and
newer. It replaces the entire iText surface used by ReAI and Ecomtools: flowing
documents, tables, images, EAN-13 barcodes, custom page sizes, stamps, watermarks,
page events, and PDF merging.

The public API lives under `no.beint.skald`. Apache PDFBox supplies the maintained
PDF object model; Skald owns the small deterministic layout engine and barcode
implementation that are specific to our applications. Skald has no iText runtime
or source dependency.

## Use

```kotlin
implementation("no.beint:skald-pdf:0.1.0")
```

Packages are published to GitHub Packages:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/beint-no/skald-pdf")
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: "beint-no"
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}
```

A minimal document deliberately resembles the domain language used by the existing
generators while remaining in the Skald namespace:

```java
try (var pdf = new PdfDocument(new PdfWriter(output));
     var document = new Document(pdf, PageSize.A4)) {
    document.add(new Paragraph("Invoice").simulateBold().setFontSize(20));
    document.add(new Table(UnitValue.createPercentArray(new float[] {3, 1}))
        .useAllAvailableWidth()
        .addHeaderCell(new Cell().add(new Paragraph("Description")))
        .addHeaderCell(new Cell().add(new Paragraph("Amount")))
        .addCell(new Cell().add(new Paragraph("Accounting")))
        .addCell(new Cell().add(new Paragraph("1 250.00"))));
}
```

## Design choices

- JDK 25 bytecode and APIs; there is intentionally no compatibility layer for old
  JDKs or old iText package names.
- Sealed types, records, pattern-matching switches, virtual-thread-safe document
  isolation, and try-with-resources throughout the Java surface.
- One compressed content stream per page during layout, so multipage tables stay
  compact instead of creating a PDF object for every drawing operation.
- Strict EAN-13 checksum validation and standards-compatible bar patterns.
- Independent rendering, text-extraction, and barcode-scanning tests validate the
  output rather than comparing implementation details.
- Only features used by current Beint applications are implemented. Legacy PDF
  signing, forms, pre-PDF-1.7 compatibility shims, and unused iText APIs are not
  carried forward.

The complete migration and test mapping is in
[`docs/use-case-inventory.md`](docs/use-case-inventory.md).

## Build and inspect

```shell
./gradlew clean build
```

The test suite writes representative PDFs and rendered PNG previews to
`build/use-case-pdfs`. It parses every result with an independent reader and scans
the generated barcode with ZXing.

Requirements are JDK 25+ and the included Gradle 9.7 wrapper.

## Clean-room boundary

The checked-out iText repository was used to understand the public concepts already
used by Beint applications. Skald is an independently written implementation in a
new namespace, backed by Apache PDFBox; no iText source is copied or distributed.

## License

Copyright Beint AS. All rights reserved.
