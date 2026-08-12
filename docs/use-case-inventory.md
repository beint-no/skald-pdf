# Beint PDF use-case inventory

This inventory is the replacement contract for removing iText. It was built from
all production iText imports and call sites in the primary ReAI and Ecomtools
checkouts. The migrated source compiles against Skald only, and every behavior class
below has a rendered-output test.

## Application coverage

| Application area | Production generators | Required behavior | Skald test artifact |
| --- | --- | --- | --- |
| Invoice, order, and offer | `InvoicePdfGenerator`, `OrderPdfGenerator`, `OfferPdfGenerator`, `PdfGeneratorUtil` | logos, mixed text, invoice line tables, colspans, summaries, payment details, page overflow | `invoice-order-offer.pdf` |
| Invoice reminders | `InvoiceReminderPdfGenerator`, `InvoiceReminderSvc` | original PDF merge, reminder tables, interest periods, fees, separator borders | `invoice-reminder.pdf`, composition test |
| Agreements | accounting services, employee contract, purchase, rent, and service agreement generators | long flowing prose, keep-together sections, keep-with-next headings, signatures, multipage output | `agreements.pdf` |
| Annual accounts | `AnnualAccountsNotesPdfGenerator` | note headings, paragraphs, metadata, multipage tables | `annual-account-notes.pdf` |
| Attachments | `AttachmentSvc` | raster image conversion, fixed-position banner/footer stamps, read-modify-write | `attachment-image.pdf`, composition test |
| EHF | `EhfPdfGenerator` | party and line tables, monetary summary colspans, custom font styles, translucent rotated watermark page event | `ehf-watermark.pdf` |
| Point of sale | `KassasystemReceiptPdfSvc` | narrow custom page, compact typography, receipt rows and totals | `pos-receipt.pdf` |
| Z reports | `KassasystemZRapportPdfSvc` | VAT, payment-method, ledger, and summary tables | `pos-z-report.pdf` |
| Payslips | `PayslipPdfGenerator` | explicit page breaks, borderless metadata grids, line separators, right-aligned amounts | `payslips.pdf` |
| Shopify | `TransactionRowsPdf` | landscape output, wide tables, repeated headers, highlighted refunds, page overflow | `shopify-transactions.pdf` |
| Tax return | `TaxReturnReceiptPdfSvc` | compact metadata grids and long validation tables | `tax-return-receipt.pdf` |
| Inventory | `InventoryStockListPdf` | long product names, wrapping, repeated table headers, totals | `inventory-stock-list.pdf` |
| Generic reports | `ReportPdfSvc` | A3 landscape, dynamic columns, filters, column spans, fixed layout, long multipage tables | `generic-reports.pdf` |
| Barcode labels | Ecomtools `PdfGenerator` | EAN-13 checksum and bars, human-readable number, 93 × 35 mm page, fixed text, long-word wrapping | barcode label/scanner test |

## Replaced API surface

- PDF lifecycle: readers, writers, document metadata, pages, resources, content
  streams, writer properties, and one-based page access.
- Composition: PDF merging, read/write stamping, overlay canvases, and end-page
  event handlers.
- Geometry: A3, A4, Letter, rotation, arbitrary dimensions, and rectangles.
- Typography: built-in Helvetica regular/bold/oblique variants, font selection,
  simulated bold, size, color, leading, alignment, and safe replacement of glyphs
  outside the built-in font encoding.
- Layout: documents, paragraphs, text runs, divs, images, tables, header rows,
  column spans, fixed/percent widths, fixed layout, area breaks, keep-together,
  keep-with-next, margins, padding, fixed positioning, borders, backgrounds,
  horizontal/vertical alignment, and forced wrapping of unbroken values.
- Drawing: line separators, direct PDF canvas operations, fill opacity, and rotated
  aligned text.
- Images and codes: PNG/JPEG decoding, scaling, and standards-correct EAN-13 output.

## Verification contract

`ApplicationUseCaseRenderingTest` generates the thirteen ReAI document families.
For every result it:

1. checks the PDF signature and parses the full file using PDFBox's independent
   loader;
2. extracts and checks use-case-specific text;
3. renders pages to pixels and rejects blank or accidentally filled pages; and
4. saves reviewable PDFs and PNGs under `build/use-case-pdfs`.

`BarcodeEANTest` additionally scans the rendered label using ZXing, including the
calculated check digit. `PdfCompositionTest` verifies merging and stamping across
multiple source pages. `ConcurrencyAndEfficiencyTest` verifies isolated generation
on virtual threads and enforces a compact size bound for a 1,000-row report.

The ReAI and Ecomtools builds are a second contract: every former iText call site
compiles against this public API, and dependency inspection must report no iText
artifact in either runtime graph.
