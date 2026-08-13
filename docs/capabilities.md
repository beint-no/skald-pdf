# Capabilities

## Implemented

| Area | Capability |
|---|---|
| Documents | PDF 2.0 output, XMP metadata, language, outlines, DisplayDocTitle, ISO/US pages, rotation |
| Text | Unicode, embedded subset TrueType, regular/bold, color, left/center/right/justify, rotation |
| Layout | Paragraphs, lists, runs, margins, padding, backgrounds, gradients, rounded borders, dashed rules, divs, fixed position, running headers/footers |
| Tables | weighted columns, spans, repeating headers, page fragmentation, complete-row checks |
| Images | JPEG pass-through, lossless raster embedding, alpha masks, adaptive prediction |
| Drawing | fills, rounded paths, axial shadings, dashed lines, opacity, canvas overlays, end-page events, watermarks, URI links |
| Barcodes | validated EAN-13 and Code 128 with human-readable text |
| Product stickers | 93 mm × 35 mm EAN-13 clothing labels (SKU, size, origin, composition) |
| Composition | merge, copy pages, stamp existing pages, preserve imported resources |
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
| Optimizing arbitrary PDFs | rewriting received content is distinct from efficient generation |
| Lossy image optimization | quality and DPI policy belongs at the application boundary |

Priorities and the boundary between planned modules are documented in the
[roadmap](../ROADMAP.md).
