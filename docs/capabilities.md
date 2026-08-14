# Capabilities

## Implemented

| Area | Capability |
|---|---|
| Documents | PDF 2.0 output, XMP metadata, optional Create/Modify dates, language, outlines, DisplayDocTitle, ISO/US/custom mm-in pages, rotation, first/rest headers, draft watermark |
| Text | Unicode, embedded subset TrueType, PostScript font names, regular/bold/italic/bold-italic, fallback stack, color, left/center/right/justify, rotation, underline, strikethrough, `.notdef` for missing glyphs |
| Layout | Paragraphs, lists with custom start index, runs, margins, padding, backgrounds, gradients, rounded borders, dashed rules, divs, nested cell content, fixed position, `keepTogether`, widow/orphan control, fitted-image measure, `AreaBreak` page size, named destinations |
| Tables | weighted or mixed point/percent columns, min-content honoured before leftover is distributed, column and row spans, repeating headers and footers, inset hairline rules (`addRule`), line-boundary row splits, complete-row checks, nested blocks in cells |
| Images | JPEG/PNG/GIF/BMP allowlist, encoded-size and pixel gates, JPEG pass-through, lossless raster embedding, alpha masks, adaptive prediction, `scaledToFit` / `asJpeg` / `fromRgb` / `fromGray` |
| Imported images | list `/XObject /Image` on parsed pages; replace with new DCT/Flate while keeping the `Do` name |
| Text extract | `PdfText` / `Pdf.extractText` via ToUnicode CMaps (WinAnsi fallback) |
| Optimize | optional `skald-optimize`: downsample and JPEG-recompress images already inside a received PDF |
| Drawing | fills, rounded paths, axial shadings, dashed lines, opacity, canvas overlays, end-page events, watermarks, URI and GoTo links |
| Barcodes | validated EAN-13, UPC-A, Code 128, GS1-128, and QR (versions 1–16) with independent decode tests |
| Labels | `skald-label-sticker` for 93×35 mm clothing EAN stickers; `skald-labels` umbrella |
| Composition | merge, copy pages, stamp existing pages, resolve indirect Contents/Annots, strip Launch/JS and other unsafe imported actions |
| Parsing | xref tables/streams, object streams, hybrid references, revisions, predictors |
| Signatures | reserved signature field in core; optional `skald-sign` CMS / PAdES-B-B; incremental second seal; rewrite of sealed files fails closed |
| Encryption | PDF 2.0 revision 6 AES-256 (`PdfEncryption`) on write; encrypted input is not parsed |
| Native photos | optional `skald-image`: TurboJPEG, libheif (HEIC/AVIF), libjxl (JPEG XL ingest only) via FFM; absent libraries are skipped |
| Runtime | eight published artifacts, JDK 25+, zero third-party *Java* runtime dependencies; native codecs optional |

## Deliberately deferred

| Area | Reason |
|---|---|
| Tagged PDF and PDF/UA | requires semantic structure throughout layout, not a post-process flag |
| PDF/A | requires output intents, metadata rules, color management, and profile validation |
| Qualified signatures (QES) | requires a QTSP, a qualified certificate, and a QSCD — not a library |
| PAdES-B-T / B-LT / B-LTA | needs a timestamp authority and revocation material |
| Decrypt / stamp encrypted files | write-only AES-256; received encrypted PDFs still fail closed |
| Interactive forms | outside the current transactional-document scope |
| HTML, CSS, SVG | large independent rendering languages; better as optional adapters |
| Complex-script shaping | needs a focused OpenType shaping engine or optional integration |
| Multi-script fallback | a document-level fallback stack exists; script-aware shaping is still deferred |
| CFF/OTF embedding | TrueType/OpenType glyf faces only until a dedicated CFF reader exists |
| Emitting JPEG XL in PDF | not in ISO 32000-2; no interoperable viewer filter yet. Decode-as-ingest is shipped |
| Lossy image optimization | quality and DPI policy belongs at the application boundary (`OptimizeOptions`) |

Priorities and the boundary between planned modules are documented in the
[roadmap](../ROADMAP.md). Signing policy is in [signing.md](signing.md).
