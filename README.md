# Skald PDF

[Website](https://beint-no.github.io/skald-pdf/) ·
[Capability matrix](docs/capabilities.md) ·
[Roadmap](ROADMAP.md) ·
[Maven Central](https://central.sonatype.com/search?q=no.beint.skaldpdf)

Skald PDF is a modern Java library for creating and composing PDF 2.0 documents.
It targets JDK 25+, uses named Java modules, and has no third-party runtime
dependencies.

Use it for invoices, statements, reports, receipts, agreements, labels, tickets,
and other generated documents that do not need historical PDF output modes.

## Highlights

- Native PDF 2.0 writer and bounded parser
- Unicode TrueType fonts with compact embedding, real italic/bold-italic faces, and a fallback stack
- Flowing paragraphs, lists, justified text, divisions, repeating tables, and automatic pagination
- Nested blocks inside table cells, row spans, repeating footer rows, mixed point/percent columns
- First-page vs continuing headers, named destinations, URI links, internal GoTo links, and draft watermarks
- JPEG pass-through, lossless raster compression, alpha, image allowlisting, and image deduplication
- Rounded surfaces, dashed rules, underline, strikethrough, and axial gradients
- EAN-13, UPC-A, Code 128, GS1-128, and QR as drawable symbols
- Optional `skald-labels` for 93×35 mm clothing stickers and A4 n-up sheets
- Page events, drawing, watermarks, safer stamping, merging, and page import
- Object streams, compact CID widths, xref streams, XMP metadata, and configurable Deflate compression
- Optional `skald-sign` module: PAdES-B-B CMS integrity seals, JDK-only, no BouncyCastle
- Optional `skald-optimize` module: recompress image XObjects inside received PDFs
- Optional `skald-image` module: TurboJPEG, libheif, and JPEG XL *ingest* (never emitted in PDF 2.0)
- Independent rendering, extraction, barcode, syntax, signature, and PDF 2.0 validation tests

## Modules

| Artifact | Java module | Use it for |
|---|---|---|
| `skald-core` | `org.skaldpdf.core` | Low-level writing, reading, fonts, images, composition, and signature *placeholders* |
| `skald-layout` | `org.skaldpdf.layout` | Flow layout and the high-level `Pdf` API |
| `skald-barcode` | `org.skaldpdf.barcode` | Immutable EAN-13, UPC-A, Code 128, GS1-128, and QR symbols |
| `skald-label-sticker` | `org.skaldpdf.labels` | 93×35 mm clothing EAN sticker and A4 n-up sheets |
| `skald-labels` | `org.skaldpdf.labels.all` | Umbrella that depends on every current `skald-label-*` |
| `skald-sign` | `org.skaldpdf.sign` | Optional CMS / PAdES-B-B sealing and verification |
| `skald-image` | `org.skaldpdf.codec` | Optional FFM TurboJPEG / libheif / libjxl photo ingest |
| `skald-optimize` | `org.skaldpdf.optimize` | Optional recompression of images already stored in a received PDF |

Engine modules (`layout`, `barcode`, `sign`, `image`, `optimize`) each depend
on core, not on one another. `skald-labels` composes barcode + core into a
finished label. An application only pays for the capabilities it selects.
Invoices and packing slips stay as examples, not artifacts. See
[docs/modules.md](docs/modules.md). The complete runtime still depends solely
on the JDK.

Signing is an integrity seal, not a qualified eIDAS signature. ReAI and Skald
are not QTSPs. See [docs/signing.md](docs/signing.md).

## Build and install

JDK 25 or newer is required. Release `1.8.0` is on Maven Central:

```kotlin
dependencies {
    implementation("no.beint.skaldpdf:skald-layout:1.8.0")
    implementation("no.beint.skaldpdf:skald-barcode:1.8.0")        // optional symbols
    implementation("no.beint.skaldpdf:skald-label-sticker:1.8.0") // optional clothing stickers
    implementation("no.beint.skaldpdf:skald-sign:1.8.0")          // optional integrity seals
    implementation("no.beint.skaldpdf:skald-image:1.8.0")         // optional HEIC / JPEG XL ingest
    implementation("no.beint.skaldpdf:skald-optimize:1.8.0")      // optional received-PDF recompress
}
```

To install a source checkout locally:

```shell
./gradlew clean build publishToMavenLocal
```

For a modular application:

```java
requires org.skaldpdf.layout;
requires org.skaldpdf.barcode;  // optional symbols
requires org.skaldpdf.labels;   // optional clothing stickers
requires org.skaldpdf.sign;     // optional
requires org.skaldpdf.optimize; // optional
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
    document.setFirstHeader(page -> new Paragraph("Northstar Ledger · original"));
    document.setFooter(18, page -> new Paragraph(page.pageNumber() + " / " + page.pageCount())
        .setTextAlignment(TextAlignment.CENTER));
    document.add(new Paragraph("Invoice 2026-1001").bold().setFontSize(20));
    document.add(new Paragraph("Payment terms: net 14 days.").italic());

    var lines = new Table(UnitValue.createPercentArray(new float[] {3, 1}))
        .useAllAvailableWidth()
        .addHeaderCell(new Cell().add(new Paragraph("Description").bold()))
        .addHeaderCell(new Cell().add(new Paragraph("Amount").bold()))
        .addCell("Consulting")
        .addCell("1 250.00");
    document.add(lines);
});
```

Add a payment QR or an EAN-13 without coupling layout to the barcode implementation:

```java
import org.skaldpdf.barcode.Ean13Barcode;
import org.skaldpdf.barcode.QrCode;
import org.skaldpdf.layout.element.Image;

document.add(new Image(new QrCode("https://pay.example/inv/2026-1001")).scaleToFit(72, 72));

var barcode = new Ean13Barcode("590123412345")
    .withModuleWidth(1.2f)
    .withBarHeight(48f);
document.add(new Image(barcode).scaleToFit(260, 110));
```

Clothing / product stickers live in `skald-labels`, not in the barcode
module. `ProductSticker.sheet` tiles the same labels onto A4:

```java
import org.skaldpdf.labels.ProductSticker;

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

String text = Pdf.extractText(stamped);
```

Recompress photos that arrived *inside* a supplier PDF:

```java
import org.skaldpdf.optimize.OptimizeOptions;
import org.skaldpdf.optimize.PdfOptimizer;

byte[] smaller = PdfOptimizer.recompress(attachment, OptimizeOptions.attachments());
```

Seal an issued invoice (integrity, not a qualified eIDAS signature):

```java
import org.skaldpdf.pdf.SignatureField;
import org.skaldpdf.sign.PdfSigner;
import org.skaldpdf.sign.SigningKey;

var sealed = PdfSigner.sign(invoice, SigningKey.fromPkcs12(pkcs12, password),
    SignatureField.invisible("InvoiceSeal").withReason("Issued invoice"));
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

See the [website](https://beint-no.github.io/skald-pdf/),
[module layers](docs/modules.md),
[capability matrix](docs/capabilities.md), [architecture](docs/architecture.md),
[standards policy](docs/standards.md), [signing / eIDAS](docs/signing.md),
[security model](docs/security.md),
[performance notes](docs/performance.md),
[workflow modules](docs/workflows.md), and [roadmap](ROADMAP.md).

## Verify generated PDFs

```shell
./gradlew clean build
scripts/validate-pdf2.sh build/use-case-pdfs
```

The test suite produces a representative corpus in `build/use-case-pdfs`,
`build/reai-compare`, `build/typical-documents`, and `build/example-gallery`.
It checks parsing, text extraction, rendering, font embedding, pagination,
composition, compression, barcode decoding, ReAI-faithful invoice layout, and
CMS signature verify/tamper. The validation script adds qpdf syntax checks and
the Arlington PDF 2.0 rules.

Website demos are generated with:

```shell
./gradlew :skald-layout:writeSiteDemos
```

## Contributing and license

Contributions are welcome; start with [CONTRIBUTING.md](CONTRIBUTING.md).
Skald PDF is licensed under the [Apache License 2.0](LICENSE). Bundled IBM Plex
font programs are separately available under the SIL Open Font License 1.1.
