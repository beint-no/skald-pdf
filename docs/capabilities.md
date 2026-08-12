# Capabilities

## Implemented

| Area | Capability |
|---|---|
| Documents | PDF 2.0 output, metadata, A3/A4/Letter/custom pages, rotation |
| Text | Unicode, embedded subset TrueType, regular/bold, color, alignment, rotation |
| Layout | Paragraphs, runs, margins, padding, backgrounds, borders, divs, fixed position |
| Tables | weighted columns, spans, repeating headers, page fragmentation, alignment |
| Images | JPEG pass-through, lossless raster embedding, alpha masks, adaptive prediction |
| Drawing | fills, lines, opacity, canvas overlays, end-page events, watermarks |
| Barcodes | validated EAN-13 with human-readable text |
| Composition | merge, copy pages, stamp existing pages, preserve imported resources |
| Parsing | xref tables/streams, object streams, hybrid references, revisions, predictors |
| Runtime | named JPMS module, JDK 25, zero external runtime dependencies |

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

The next standards priority is tagged document structure and PDF/UA-2. The next
writer priority is bounded temporary-file spooling for extremely large documents,
followed by optional linearization for byte-range web delivery.
