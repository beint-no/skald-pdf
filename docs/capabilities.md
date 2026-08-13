# Capabilities

## Implemented

| Area | Capability |
|---|---|
| Documents | PDF 2.0 output, XMP metadata, language, outlines, DisplayDocTitle, ISO/US pages, rotation, first/rest headers |
| Text | Unicode, embedded subset TrueType, PostScript font names, regular/bold/italic/bold-italic, fallback stack, color, left/center/right/justify, rotation, underline, strikethrough, `.notdef` for missing glyphs |
| Layout | Paragraphs, lists, runs, margins, padding, backgrounds, gradients, rounded borders, dashed rules, divs, nested cell content, fixed position, `keepTogether`, fitted-image measure, `AreaBreak` page size, named destinations |
| Tables | weighted or mixed point/percent columns, column and row spans, repeating headers and footers, page fragmentation, complete-row checks, nested blocks in cells |
| Images | JPEG/PNG/GIF/BMP allowlist, encoded-size and pixel gates, JPEG pass-through, lossless raster embedding, alpha masks, adaptive prediction |
| Drawing | fills, rounded paths, axial shadings, dashed lines, opacity, canvas overlays, end-page events, watermarks, URI and GoTo links |
| Barcodes | validated EAN-13, Code 128, GS1-128, and QR (versions 1–16) with independent decode tests |
| Product stickers | 93 mm × 35 mm EAN-13 clothing labels; A4 n-up print sheets |
| Composition | merge, copy pages, stamp existing pages, resolve indirect Contents/Annots, strip Launch/JS and other unsafe imported actions |
| Parsing | xref tables/streams, object streams, hybrid references, revisions, predictors |
| Runtime | three focused JPMS modules, JDK 25+, zero third-party runtime dependencies |

## Deliberately deferred

| Area | Reason |
|---|---|
| Tagged PDF and PDF/UA | requires semantic structure throughout layout, not a post-process flag |
| PDF/A | requires output intents, metadata rules, color management, and profile validation |
| Signatures and encryption | cryptography should be a separate optional module with expert review |
| Interactive forms | outside the current transactional-document scope |
| HTML, CSS, SVG | large independent rendering languages; better as optional adapters |
| Complex-script shaping | needs a focused OpenType shaping engine or optional integration |
| Multi-script fallback | a document-level fallback stack exists; script-aware shaping is still deferred |
| CFF/OTF embedding | TrueType/OpenType glyf faces only until a dedicated CFF reader exists |
| Optimizing arbitrary PDFs | rewriting received content is distinct from efficient generation |
| Lossy image optimization | quality and DPI policy belongs at the application boundary |

Priorities and the boundary between planned modules are documented in the
[roadmap](../ROADMAP.md).
