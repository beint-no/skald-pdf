# Business workflow modules

iText sells a suite. Skald stays modular so an accounting app only pays for
what it uses. This is the mapping against the workflows ReAI actually runs.

| Workflow | iText | Skald today | Next module? |
|---|---|---|---|
| Generate invoices, slips, statements | Core + layout | `skald-layout` + `skald-invoice-no` and companions | No |
| Barcode symbols | Third-party or custom | `skald-barcode` | No |
| Clothing / warehouse labels | Custom iText page | `skald-label-sticker`, `skald-label-shipping` | No |
| Password-protect a payslip | Core encryption | `PdfEncryption` (AES-256, PDF 2.0 R=6) | Done |
| Merge + stamp + watermark | Core | `skald-core` | No |
| Seal issued PDFs | Signatures add-on | `skald-sign` (PAdES-B-B, not QES) | Timestamp later |
| HTML/CSS → PDF | pdfHTML | Out of scope | Do not add |
| OCR inbox scans | pdfOCR (Tesseract) | — | Optional later; not zero-dep |
| Redact personal data | pdfSweep | — | Maybe `skald-redact` when we have a concrete ReAI case |
| Forms / AcroForm fill | Core forms | Signature field only | Not a priority |
| Optimize generated files | pdfOptimizer | Compact subset, object streams, JPEG pass-through | Incremental, stays in core |
| Recompress photos *already inside* a received PDF | pdfOptimizer | `skald-optimize` | Done, optional |
| Downscale/re-encode *before* embedding | Manual | `skald-image`: `RasterImages.scaleToFit` / `asJpeg`, plus optional native HEIC / JXL / TurboJPEG | No |
| HEIC/AVIF phone photos | ImageIO plugins / none | `skald-image` (`NativeImages.prepare`) | Done, optional |
| JPEG XL phone photos | none | `skald-image` decode → DCT JPEG. Not stored as JXL in PDF 2.0 | Ingest only |

## Image compression of existing PDFs

This is the one iText add-on that is a no-brainer for accounting.

Supplier invoices and expense attachments often carry 4–12 megapixel phone
photos as PNG or uncompressed JPEG. Generation-time helpers
(`RasterImages.scaleToFit`, `RasterImages.asJpeg`) cover files we create. Rewriting *received* PDFs
needs a different path: walk imported XObject image streams, decode, optionally
downsample, write a new DCT or Flate XObject, keep the page content stream's
`Do` name.

That work is `skald-optimize`. It depends on core and `skald-image`. `PdfDocument.importedImages()`
lists XObjects; `replaceImportedImage` writes a new DCT/Flate stream under the
same resource name. `PdfOptimizer.recompress` applies `OptimizeOptions`
(max edge + JPEG quality) and skips filters it cannot decode (JPX, JBIG2, CCITT).

Do not add pdfHTML. HTML-in-core is the baggage Skald exists to avoid.

## Algorithms used

- Table columns: CSS table min-content, then leftover distributed by the
  author's percent/weight (CSS2.1 §17.5.2 simplified).
- Word wrap: CSS `overflow-wrap`. `NORMAL` never splits a token; `ANYWHERE`
  splits by code point.
- Borders: PDF stroke is centred on the path (ISO 32000-2 §8.4.3). Rules are
  inset by half the stroke width so they live inside the cell box.
- Fonts: TrueType subset, unused GSUB/GPOS/GDEF dropped. PDF 2.0 does not
  offer the Standard 14 unembedded escape that makes ReAI's 2 KiB invoices
  possible.
- Images: JPEG pass-through; other rasters use predictor + Flate. Optional
  bilinear downsample + JDK JPEG writer for photos.
- Deflate: `NONE` / `FAST` / `BALANCED` / `MAXIMUM` (levels 0/1/6/9).
