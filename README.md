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
- Optional components under `skald-components/`: Norwegian invoices, packing slips, reminders, and print-stock labels
- Page events, drawing, watermarks, safer stamping, merging, and page import
- Object streams, compact CID widths, xref streams, XMP metadata, and configurable Deflate compression
- Optional `skald-sign` module: PAdES-B-B CMS integrity seals, JDK-only, no BouncyCastle
- Optional `skald-optimize` module: recompress image XObjects inside received PDFs
- Optional `skald-image` module: TurboJPEG, libheif, and JPEG XL *ingest* (never emitted in PDF 2.0)
- Independent rendering, extraction, barcode, syntax, signature, and PDF 2.0 validation tests

## Modules

| Artifact | Java module | Use it for |
|---|---|---|
| `skald-core` | `org.skaldpdf.core` | Low-level writing, reading, custom fonts, images, composition, and signature *placeholders* |
| `skald-fonts` | `org.skaldpdf.fonts` | Lazily loaded, embeddable Skald Sans faces |
| `skald-layout` | `org.skaldpdf.layout` | Flow layout and the high-level `Pdf` API |
| `skald-barcode` | `org.skaldpdf.barcode` | Immutable EAN-13, UPC-A, Code 128, GS1-128, and QR symbols |
| `skald-invoice-no` | `org.skaldpdf.invoice.no` | Norwegian faktura / kreditnota / tilbud / ordrebekreftelse |
| `skald-packing-slip-no` | `org.skaldpdf.packing.no` | Norwegian packing slip and delivery note |
| `skald-reminder-no` | `org.skaldpdf.reminder.no` | Norwegian purring and betalingsoppfordring |
| `skald-statement-no` | `org.skaldpdf.statement.no` | Norwegian statement of account |
| `skald-receipt-no` | `org.skaldpdf.receipt.no` | Norwegian A5 sales receipt |
| `skald-purchase-order-no` | `org.skaldpdf.purchase.no` | Norwegian purchase order |
| `skald-label-sticker` | `org.skaldpdf.labels` | 93×35 mm clothing EAN sticker and A4 n-up sheets |
| `skald-label-shipping` | `org.skaldpdf.labels.shipping` | 100×150 mm shipping label |
| `skald-sign` | `org.skaldpdf.sign` | Optional CMS / PAdES-B-B sealing and verification |
| `skald-image` | `org.skaldpdf.codec` | Optional FFM TurboJPEG / libheif / libjxl photo ingest |
| `skald-optimize` | `org.skaldpdf.optimize` | Optional recompression of images already stored in a received PDF |

Engine modules depend on core. Layout and human-readable barcodes also use the
standard font module; low-level core users do not pay for its bundled faces. Finished pages live under
[`skald-components/`](skald-components/README.md) as **separate** artifacts
— one module per invoice, slip, or label. See [docs/modules.md](docs/modules.md).
The complete runtime still depends solely on the JDK.

Signing is an integrity seal, not a qualified eIDAS signature. ReAI and Skald
are not QTSPs. See [docs/signing.md](docs/signing.md).

## Build and install

JDK 25 or newer is required. Release `1.9.0` is on Maven Central:

```kotlin
dependencies {
    implementation("no.beint.skaldpdf:skald-layout:1.9.0")
    implementation("no.beint.skaldpdf:skald-fonts:1.9.0")         // direct SkaldSans API access
    implementation("no.beint.skaldpdf:skald-barcode:1.9.0")        // optional symbols
    implementation("no.beint.skaldpdf:skald-invoice-no:1.9.0")    // optional Norwegian invoice
    implementation("no.beint.skaldpdf:skald-label-sticker:1.9.0") // optional clothing stickers
    implementation("no.beint.skaldpdf:skald-sign:1.9.0")          // optional integrity seals
    implementation("no.beint.skaldpdf:skald-image:1.9.0")         // optional HEIC / JPEG XL ingest
    implementation("no.beint.skaldpdf:skald-optimize:1.9.0")      // optional received-PDF recompress
}
```

To install a source checkout locally:

```shell
./gradlew clean build publishToMavenLocal
```

For a modular application:

```java
requires org.skaldpdf.layout;
requires org.skaldpdf.fonts;     // direct SkaldSans API access
requires org.skaldpdf.barcode;     // optional symbols
requires org.skaldpdf.invoice.no;  // optional Norwegian invoice
requires org.skaldpdf.labels;      // optional clothing stickers
requires org.skaldpdf.sign;        // optional
requires org.skaldpdf.optimize;    // optional
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

Norwegian invoices live in `skald-invoice-no`. The theme is ReAI's
faktura layout: 40 pt A4 margins, right-aligned letterhead, 7-column VAT
table, payment block, optional QR.

```java
import org.skaldpdf.invoice.no.Bank;
import org.skaldpdf.invoice.no.Company;
import org.skaldpdf.invoice.no.NorwegianInvoice;
import org.skaldpdf.invoice.no.Party;

byte[] invoice = NorwegianInvoice.pdf(NorwegianInvoice.Model.builder()
    .company(new Company("Nordlys Handel AS", "NO", "999888777",
        "Storgata 10, 0184 Oslo, Norge", true))
    .customer(new Party("Fjordbutikken AS", "Kaien 4", "5003 Bergen"))
    .bank(new Bank("DNB Bank ASA", "15034567890", "NO9315034567890", "DNBANOKK"))
    .number("1001")
    .issueDate(java.time.LocalDate.of(2026, 8, 12))
    .dueDate(java.time.LocalDate.of(2026, 8, 26))
    .line("Regnskapstjeneste august", "Løpende avtale", 8, "1,250.00", 25)
    .build());
```

Clothing / product stickers live in `skald-label-sticker`, not in the barcode
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
