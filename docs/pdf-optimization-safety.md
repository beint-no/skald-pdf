# PDF optimization safety model

Skald optimizes the final PDF object graph rather than printing or rasterizing
the document into a new PDF. This preserves pages, content operators, fonts,
forms, annotations, outlines, structure trees, optional content, JavaScript,
embedded files, metadata, and unknown extension dictionaries.

The implementation follows ISO 32000 semantics. The current PDF 2.0 standard
is indexed by the [PDF Association specification archive][pdf-spec]. The
lossless stream strategy also tracks the independently implemented
[qpdf file-size guidance][qpdf-size].

## Accepted transformations

| Transformation | Proof and gate |
| --- | --- |
| Remove unreachable indirect objects | Only objects unreachable from the final trailer are omitted. Undefined indirect references become `null`, as required by ISO 32000-1 section 7.3.9. |
| Generate object and cross-reference streams | Every reachable value is imported and a graph digest independent of object numbers and dictionary order must match. |
| Raw, ASCIIHex, or ASCII85 stream to Flate | The old filter is decoded within bounded memory, the new stream is inflated again, and decoded bytes must be identical. |
| Recompress an existing Flate stream | Decoded bytes must be identical. Image planes use balanced Deflate; other data uses a bounded level-6/level-9 sample to avoid maximum compression when it cannot pay. |
| Recompress a JPEG image with JPEGli | Only opaque 8-bit DeviceGray or DeviceRGB image XObjects with supported filters and no semantic modifiers qualify. ASCII85, ASCIIHex, and Flate wrappers around DCT streams are decoded in bounded memory. The replacement must clear both per-image and whole-document savings gates. |
| Convert a lossless raster to JPEGli | The same image restrictions apply, plus pixel, source-size, quality, and resize limits. Explicit identity `/Decode` arrays are retained; any inversion or remapping remains excluded. |
| Share exact image XObjects | Semantically simple image streams share one object only when their complete canonical stream bodies are byte-identical. The semantic digest receives an explicit source-reference alias map. Similar pixels or payload-only matches never qualify. |

Images reached through nested Form XObjects and shared image objects are
handled once. An APP15 policy marker makes JPEG processing exactly idempotent.
Lossless stream output is deterministic as well, so the complete second
optimization must equal the first byte for byte.

## Documents preserved byte for byte

Skald does not rewrite these document classes:

| Constraint | Why |
| --- | --- |
| Digital signature | A canonical rewrite changes signed byte ranges and invalidates the signature. |
| Incremental history | Old revisions may be required for audit, recovery, signatures, or forensic analysis. |
| Linearization | Object order, offsets, and hint tables implement progressive loading and must be rebuilt as a unit. |
| Declared PDF/A, PDF/X, PDF/UA, PDF/E, or PDF/VT conformance | A valid-looking rewrite is insufficient; profile-specific validation is required. [veraPDF][verapdf] supplies formal PDF/A and PDF/UA rules. |
| Encryption without credentials and policy | Decryption and permissions are outside attachment optimization. |

This is deliberately stricter than sending a PDF through Ghostscript
`pdfwrite`. [Ghostscript documents][ghostscript] that this creates a new page description and
may omit non-marking information such as comments, hyperlinks, bookmarks, or
other structures. That behavior is useful for controlled conversion, but not
for a zero-surprise attachment optimizer.

## Currently excluded image and document techniques

* ICCBased images stay unchanged until Skald has a color-managed conversion
  path that validates the profile and proves the rendered conversion. Treating
  ICC samples as DeviceRGB can change colors.
* Images with masks, soft masks, non-identity decode arrays, alternates, OPI,
  or image metadata are not replaced. Their streams may still receive exact
  lossless compression.
* CCITT, JBIG2, and JPX images remain in their specialized encodings. Lossy
  JBIG2 symbol substitution is especially unsuitable for invoices and receipts.
* Font subsetting and font deduplication require complete glyph-use analysis
  across content, forms, patterns, appearances, and Type 3 fonts. Incorrect
  analysis can silently change text.
* Generic indirect-object deduplication is not assumed safe merely because two
  objects serialize identically; object identity can participate in forms,
  structure, annotations, and extension semantics. Deduplication is limited to
  the simple image XObjects described above.
* Effective-DPI downsampling needs a complete graphics-state walk across every
  use of an image, including forms, patterns, and appearance streams. The
  current fixed pixel cap is more conservative.
* Zopfli is not a request-time default. qpdf describes it as roughly 100 times
  slower than zlib for about 5% improvement over the best ordinary Deflate.

## Verification gates

Every changed corpus document must pass all of these checks:

1. Skald reparses the candidate and compares a SHA-256 semantic digest of the
   entire reachable object graph against the expected graph with only approved
   stream replacements.
2. PDFBox independently compares pages, boxes, rotations, decoded content,
   annotations, forms, outlines, structure, optional content, attachments,
   JavaScript, signatures, metadata, and extracted text.
3. A second optimization produces exactly the same bytes.
4. The opt-in visual corpus test renders the first, middle, and last changed
   page independently before and after at 96 DPI. It requires at least 25 dB
   PSNR and 0.88 mean block SSIM and saves the worst before/after/difference
   images for inspection.
5. Fuzzed and truncated inputs may return the original bytes, but must never
   produce a new unparseable PDF.
6. Retained changed outputs pass `qpdf --check`, MuPDF, and Poppler as
   independent readers before a release.

## Current private-corpus result

The 2026-08-29 release gate used 9,630 production PDFs (1,412,810,215 bytes).
Skald changed 2,568 and saved 232,107,905 bytes (16.43%). Optimizer-only
latency was 8 ms p50, 70 ms p95, and 188 ms p99 on the development machine.

The deterministic 250-largest subset contained 396,833,510 bytes. Skald
changed 157 and saved 152,284,088 bytes (38.37%). All 157 outputs passed qpdf,
MuPDF, and Poppler. Across 282 independently rendered before/after pages, the
minimum PSNR was 27.800 dB and minimum mean SSIM was 0.891187; the fixed gates
are 25 dB and 0.88. The earlier 1.12 implementation saved 6.54% on the same
subset.

The 93 unchanged files in that subset are accounted for: 51 are protected
documents, 31 fail codec or savings gates, six use unsupported image semantics,
four have no qualifying stream gain, and one is excluded by image policy. The
full corpus also contains 27 malformed, non-standard, or encrypted inputs;
each remains byte-identical.

Exact whole-file hashing found 457 duplicate files representing 72,804,113
bytes beyond one copy of each payload. That is an application storage
deduplication opportunity and is deliberately reported separately from PDF
optimization.

Real production documents stay in the ignored `benchmark-corpus/` directory.
The largest-250 report assigns every PDF an outcome and records its object
makeup, eligible images, savings, and optimizer latency; see
[private PDF corpus](private-pdf-corpus.md).

[pdf-spec]: https://pdfa.org/resource/pdf-specification-archive/
[qpdf-size]: https://qpdf.readthedocs.io/en/latest/cli.html#optimizing-file-size
[verapdf]: https://docs.verapdf.org/validation/
[ghostscript]: https://ghostscript.readthedocs.io/en/latest/VectorDevices.html
