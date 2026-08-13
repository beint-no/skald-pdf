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

- First-class page templates, running headers and footers, and page numbering.
- Stronger table row/column constraints, diagnostics, and orphan/widow control.
- Font fallback and an optional modern OpenType shaping module for complex
  scripts.
- More vector path, clipping, gradient, and reusable form APIs in core.
- Additional barcode symbologies in the existing optional barcode module, each
  verified by an independent decoder and its governing standard.

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
