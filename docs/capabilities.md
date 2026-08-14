# Capabilities

## Implemented

| Area | Capability |
|---|---|
| Documents | PDF 2.0 output, XMP metadata, optional Create/Modify dates, language, outlines, DisplayDocTitle, ISO/US/custom mm-in pages, rotation, first/rest headers, draft watermark |
| Text | Unicode, embedded subset TrueType, PostScript font names, regular/bold/italic/bold-italic, fallback stack, color, left/center/right/justify, rotation, underline, strikethrough, `.notdef` for missing glyphs |
| Layout | Paragraphs, lists with custom start index, runs, margins, padding, backgrounds, gradients, rounded borders, dashed rules, divs, nested cell content, fixed position, `keepTogether`, widow/orphan control, fitted-image measure, `AreaBreak` page size, named destinations |
| Tables | weighted or mixed point/percent columns, min-content honoured before leftover is distributed, column and row spans, repeating headers and footers, inset hairline rules (`addRule`), line-boundary row splits, complete-row checks, nested blocks in cells |
| Images | JPEG/PNG/GIF/BMP allowlist, encoded-size and pixel gates, JPEG pass-through, lossless raster embedding, alpha masks, adaptive prediction, `scaledToFit` / `asJpeg` |
| Drawing | fills, rounded paths, axial shadings, dashed lines, opacity, canvas overlays, end-page events, watermarks, URI and GoTo links |
| Barcodes | validated EAN-13, UPC-A, Code 128, GS1-128, and QR (versions 1–16) with independent decode tests |
| Product stickers | 93 mm × 35 mm EAN-13 clothing labels; A4 n-up print sheets |
| Composition | merge, copy pages, stamp existing pages, resolve indirect Contents/Annots, strip Launch/JS and other unsafe imported actions |
| Parsing | xref tables/streams, object streams, hybrid references, revisions, predictors |
| Signatures | reserved signature field in core; optional `skald-sign` CMS / PAdES-B-B integrity seal; verify + tamper tests |
| Runtime | four focused JPMS modules, JDK 25+, zero third-party runtime dependencies |

## Deliberately deferred

| Area | Reason |
|---|---|
| Tagged PDF and PDF/UA | requires semantic structure throughout layout, not a post-process flag |
| PDF/A | requires output intents, metadata rules, color management, and profile validation |
| Qualified signatures (QES) | requires a QTSP, a qualified certificate, and a QSCD — not a library |
| PAdES-B-T / B-LT / B-LTA | needs a timestamp authority and revocation material |
| Encryption | cryptography should stay an optional module with expert review |
| Interactive forms | outside the current transactional-document scope |
| HTML, CSS, SVG | large independent rendering languages; better as optional adapters |
| Complex-script shaping | needs a focused OpenType shaping engine or optional integration |
| Multi-script fallback | a document-level fallback stack exists; script-aware shaping is still deferred |
| CFF/OTF embedding | TrueType/OpenType glyf faces only until a dedicated CFF reader exists |
| Optimizing arbitrary PDFs | rewriting received content is distinct from efficient generation |
| Lossy image optimization | quality and DPI policy belongs at the application boundary |

Priorities and the boundary between planned modules are documented in the
[roadmap](../ROADMAP.md). Signing policy is in [signing.md](signing.md).
