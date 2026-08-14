# Roadmap

Skald prioritizes correctness and a small, durable API over matching the surface
area of older PDF libraries. Work is ordered by the amount of confidence or
practical document coverage it adds.

## Shipped in 1.8.0

- PDF 2.0 AES-256 encryption (`PdfEncryption`, revision 6) for payslips
  and similar generated files. Not combined with signatures. Encrypted
  input still fails closed.
- Incremental second signature; rewrite of a sealed file throws.
- `skald-label-sticker` is the clothing-sticker artifact.
  `skald-labels` is now the umbrella. No empty GS1/shelf modules.
- Jpegli evaluated and deferred: no shared library to bind; TurboJPEG
  remains the optional JPEG encoder.

## Shipped in 1.7.0

- Split print stock out of barcode. `skald-barcode` is symbols only.
  `ProductSticker` moved to `skald-labels` (`org.skaldpdf.labels`).
- Documented the engine vs print-stock vs application-document rule in
  [docs/modules.md](docs/modules.md). Invoices stay examples, not artifacts.

## Shipped in 1.6.0

- Imported image XObjects: `PdfDocument.importedImages()` and
  `replaceImportedImage`. The page `Do` name is preserved.
- Optional `skald-optimize`: downsample / JPEG-recompress images *inside*
  received PDFs. Depends only on core.
- JPEG XL *ingest* in `skald-image` via libjxl. Not written into PDF 2.0 —
  ISO 32000-2 has no `/JXLDecode`, and Acrobat / Preview / PDFBox cannot
  display it. The PDF Association selected JXL as the preferred HDR solution
  for a *future* spec (Wyatt, PDF Days Europe 2025). See
  [native-image.md](docs/native-image.md).
- `ImageData.fromRgb` / `fromGray` for rasters already decoded by a native codec.
- `PdfText` / `Pdf.extractText`: ToUnicode-aware extraction for invoices and slips.
- 16×16 visual fingerprints in the layout test harness.

## Shipped in 1.5.0

- Optional `skald-image`: FFM bindings to TurboJPEG and libheif. Missing
  natives fail closed. Core still uses ImageIO. See [native-image.md](docs/native-image.md).

## Next

Ordered by what would make Skald the better business-PDF library, not a
bigger iText clone:

1. **Visual regression baselines** — persist fingerprints for the ReAI corpus.
2. **CFF/OTF embedding** — licensed retail faces as PDF 2.0 CID fonts.
3. **PDF/A-4 module** — archive invoices with output intents.
4. **PAdES-B-T** — timestamp after a TSA; still not QES.
5. **Emit JPEG XL** — only after ISO publishes a filter and viewers implement it.

## Shipped in 1.4.1

- Table min-content columns so invoice headers such as `Antall` do not split.
- Inset hairline rules; `Table.addRule` for summary separators.
- `ImageData.scaledToFit` / `asJpeg` for generation-time photo policy.
- Generation speed/size harness (`build/benchmarks/latest.json`).
- Documented `skald-optimize` (recompress images *inside* received PDFs) as
  the next optional module. Not a pdfHTML clone.

## Shipped in 1.4.0

- Optional `skald-sign` module: detached CMS SignedData, SHA-256, RSA,
  PAdES-B-B signed attributes, Adobe.PPKLite or `ETSI.CAdES.detached`.
- Core only reserves `/ByteRange` + `/Contents`. Cryptography stays out of
  layout and barcode.
- ReAI-faithful invoice / credit note / paid copy / reminder / packing-slip
  corpus with side-by-side samples.
- Large typical-document suite (invoices, slips, barcodes, statements).
- Honest signing policy: not a QTSP, not QES, not a Scrive replacement.
  See [docs/signing.md](docs/signing.md).

## Shipped in 1.3.0

- Tall table rows split at line boundaries when the row is paragraph-only.
- Widow/orphan control when a paragraph fragments across pages.
- Optional XMP CreateDate / ModifyDate (unset keeps output deterministic).
- `Document.setWatermark`, `setMargins(all)`, and `addOutline(title)` for the current page.
- `PageSize.ofMillimetres` / `ofInches` for label stock.
- Numbered lists can start at an arbitrary index.
- UPC-A barcodes (EAN-13 with a leading zero).

## Shipped in 1.2.0

- Real italic and bold-italic bundled faces (`italic()`, not a slanted regular).
- Font fallback stack; a custom document face falls back to Skald Sans by default.
- Table row spans, repeating footer rows, and mixed point/percent columns.
- Named destinations (`setLocalDestination` / `setNamedDestination`) for TOCs.
- GS1-128 in `skald-barcode`, verified by an independent decoder.
- `Image.scaleInto` so small symbols such as QR codes can enlarge to a box.

## Shipped in 1.1.0

Business-document work that was blocking invoices, labels, and composition:

- Nested `Div` / `Table` / `ListBlock` inside cells, and children of
  fixed-position / header `Div`s, now paint instead of only being measured.
- `keepTogether` on paragraphs, tables, and lists; header/footer painters cannot
  call `newPage()`.
- Images are measured after they are fitted to the content width.
- First-page headers can use a different painter and a different reserved height.
- Underline and strikethrough.
- Internal GoTo links (`setDestinationPage`) for tables of contents.
- `AreaBreak` can switch the next page size (portrait report, landscape appendix).
- Indirect `/Contents` and `/Annots` resolve when stamping received files.
- Launch, JavaScript, and other unsafe imported actions are dropped.
- Fonts use the face’s PostScript name in `/BaseFont`.
- Images are allowlisted and size-gated before `ImageIO` allocates a raster.
- Missing characters use `.notdef` instead of aborting the file.
- QR codes in `skald-barcode`, verified by ZXing after rendering.
- N-up `ProductSticker.sheet` for warehouse printing.
- Public site at <https://beint-no.github.io/skald-pdf/> with generated demos.

## 1. Harden the foundation

- Expand malformed-input, font, image, parser, and layout fuzzing.
- Pin reproducible local PDF 2.0 validation alongside the independent hosted
  Arlington check.
- Add cross-renderer visual regression baselines for Poppler, MuPDF, and
  Ghostscript.
- Add larger deterministic stress fixtures and publish allocation, throughput,
  and output-size benchmarks.
- Spool encoded streams to bounded temporary storage so output size is not
  limited by heap, while keeping the default in-memory path fast for ordinary
  documents.

## 2. Complete remaining generated-document APIs

Still ordered by what businesses hit next.

### Tables and type

- A second fallback face chosen per-script, not only per missing glyph.

### Core composition

- CFF/OTF embedding so licensed retail faces work, still as PDF 2.0 CID fonts.

### Still later

- Orphan/widow control, Form XObjects, OpenType shaping as an optional module.

## 3. Add modern conformance profiles

- A semantic structure model and optional PDF/UA-2 accessibility module.
- An optional PDF/A-4 archival module with color management, output intents,
  metadata rules, and profile validation.
- Conformance-aware APIs that prevent invalid combinations instead of adding a
  flag at serialization time.

## 4. Extend composition and delivery

- PAdES-B-T (timestamp) and B-LT/B-LTA after a TSA integration. QES remains
  out of scope unless a listed QTSP is used.
- Optional PDF 2.0 encryption after focused security review.
- Optional linearization for byte-range web delivery.
- Safer annotation and form APIs based on current PDF 2.0 structures.
- Explicit image downsampling/recompression policies; no silent quality loss.

## Non-goals

- Emitting PDF 1.x or preserving obsolete writer modes.
- Shipping a browser-sized HTML/CSS engine inside the core library.
- Executing JavaScript, actions, or external resources from parsed documents.
- Pulling large third-party frameworks into the production runtime for
  convenience.
