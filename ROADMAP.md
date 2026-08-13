# Roadmap

Skald prioritizes correctness and a small, durable API over matching the surface
area of older PDF libraries. Work is ordered by the amount of confidence or
practical document coverage it adds.

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

## 2. Complete common generated-document APIs

Work is ordered by what blocks invoices, statements, labels, and reports today.

### Make layout honest

- Paint nested `Div` / `Table` / `ListBlock` inside cells, and children of
  fixed-position / header `Div`s. Estimation without drawing is a bug.
- Honour `keepTogether` on paragraphs and tables; stop header/footer painters
  from calling `newPage()`.
- Split tall table rows at line boundaries, not through glyphs.
- Measure images after fitting them to the content width.

### Document chrome and navigation

- First-page vs continuing headers; `AreaBreak` with a next page size.
- Named destinations and outlines bound to headings, not guessed page numbers.
- Internal GoTo links for TOCs and “see appendix”.

### Tables and type

- Row spans, repeating footer rows, mixed point/percent columns.
- Font fallback so one unsupported character does not abort the file.
- Underline, strikethrough, and a real italic face (embedded, not slanted).

### Core composition

- Resolve indirect `/Contents` and `/Annots` when stamping received files.
- Use the font’s PostScript name in `/BaseFont` (not always `SkaldSans`).
- Allowlist and size-gate images before `ImageIO` allocates a raster.
- Strip launch/JS/page-open actions when importing supplier PDFs.
- CFF/OTF embedding so licensed retail faces work, still as PDF 2.0 CID fonts.

### Optional barcode additions

- QR and GS1-128 in `skald-barcode`, each verified by an independent decoder.
- N-up sticker sheets for warehouse printing.

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
