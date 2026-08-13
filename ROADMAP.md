# Roadmap

Skald prioritizes correctness and a small, durable API over matching the surface
area of older PDF libraries. Work is ordered by the amount of confidence or
practical document coverage it adds.

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

- Split tall table rows at line boundaries, not through glyphs.
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

- Optional PDF 2.0 cryptography and digital-signature modules after focused
  security review.
- Optional linearization for byte-range web delivery.
- Safer annotation and form APIs based on current PDF 2.0 structures.
- Explicit image downsampling/recompression policies; no silent quality loss.

## Non-goals

- Emitting PDF 1.x or preserving obsolete writer modes.
- Shipping a browser-sized HTML/CSS engine inside the core library.
- Executing JavaScript, actions, or external resources from parsed documents.
- Pulling large third-party frameworks into the production runtime for
  convenience.
