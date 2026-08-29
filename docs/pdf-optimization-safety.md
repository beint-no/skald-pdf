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
| Recompress a JPEG image with JPEGli | Only 8-bit DeviceGray, DeviceRGB, or three-component ICCBased image XObjects with supported filters qualify. An optional, semantically simple DeviceGray soft mask is retained byte-exact and forces the colour plane to keep its original dimensions. ASCII85, ASCIIHex, and Flate wrappers around DCT streams are decoded in bounded memory. ICCBased replacements retain the original profile object and color-space dictionary. The replacement must clear both per-image and whole-document savings gates. |
| Convert a lossless raster to JPEGli | The same image restrictions apply, plus pixel, source-size, quality, and resize limits. Explicit identity `/Decode` arrays are retained; any inversion or remapping remains excluded. |
| Share exact image XObjects | Semantically simple image streams share one object only when their complete canonical stream bodies are byte-identical. The semantic digest receives an explicit source-reference alias map. Similar pixels or payload-only matches never qualify. |
| Share exact font programs | Only indirect `FontFile`, `FontFile2`, and `FontFile3` streams under a `FontDescriptor` qualify. Their complete canonical stream dictionaries and encoded bytes must be identical and contain no indirect values. Descriptors, font dictionaries, encodings, CMaps, glyph data, and content operators remain unchanged. |

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
| Malformed Flate stream | Truncated streams and streams with trailing encoded data may render in tolerant readers but fail independent validators. Skald leaves the complete source byte-for-byte unchanged. |

This is deliberately stricter than sending a PDF through Ghostscript
`pdfwrite`. [Ghostscript documents][ghostscript] that this creates a new page description and
may omit non-marking information such as comments, hyperlinks, bookmarks, or
other structures. That behavior is useful for controlled conversion, but not
for a zero-surprise attachment optimizer.

## Currently excluded image and document techniques

* Three-component ICCBased images can be recompressed while retaining their
  original profile, alternate space, and component interpretation. Profiles
  with one, two, or four components remain unchanged because the JPEGli path
  cannot preserve those sample planes and component counts.
* Images with hard masks, preblended `/Matte` soft masks, nested masks,
  non-DeviceGray soft masks, non-identity decode arrays, alternates, OPI, or
  image metadata are not replaced. A simple DeviceGray soft-mask stream is
  preserved exactly while only its colour plane is recompressed without
  resizing. Excluded streams may still receive exact lossless compression.
* CCITT, JBIG2, and JPX images remain in their specialized encodings. Lossy
  JBIG2 symbol substitution is especially unsuitable for invoices and receipts.
* Font subsetting and merging non-identical font subsets require complete
  glyph-use analysis across content, forms, patterns, appearances, and Type 3
  fonts. Incorrect analysis can silently change text. Exact duplicate program
  streams are already shared without interpreting glyphs. The largest-file
  audit found a one-page
  1.27 MiB PDF whose images use 2.5 KiB and whose full Calibri programs use
  about 1.24 MiB; that is a real next target, but not a safe stream-only rewrite.
* Generic indirect-object deduplication is not assumed safe merely because two
  objects serialize identically; object identity can participate in forms,
  structure, annotations, and extension semantics. Deduplication is limited to
  the simple image XObjects and font program streams described above.
* Effective-DPI downsampling needs a complete graphics-state walk across every
  use of an image, including forms, patterns, and appearance streams. The
  current fixed pixel cap is more conservative.
* Zopfli is not a request-time default. qpdf describes it as roughly 100 times
  slower than zlib for about 5% improvement over the best ordinary Deflate.

## Comparison with established optimizers

[Adobe Acrobat][adobe-optimizer] exposes image downsampling and recompression,
font removal/subsetting, transparency flattening, object and user-data removal,
content cleanup, and linearization. [iText pdfOptimizer][itext-optimizer]
combines stream compression, duplicate-font removal, font subsetting and
merging, image scaling/recompression, colour conversion, and rollback when a
replacement grows. [Apryse][apryse-optimizer] similarly removes duplicate
fonts, images, ICC profiles, and other streams, downsamples images, supports
JBIG2/JPEG 2000, and removes unused objects. [Nutrient][nutrient-optimizer]
offers unused-content removal, font and image analysis, and linearization.

Skald now covers the high-value subset that has a compact local proof: object
streams, exact stream recompression, bounded image resizing/recompression,
exact simple-image sharing, exact font-program sharing, whole-file rollback,
idempotence, and graph equivalence. The production audit found only 3,471 bytes
of duplicate ICC profile payloads in the effective 250-largest outputs, so a
separate ICC interning feature is not justified yet.

The remaining commercial techniques are not interchangeable with safe
attachment optimization. Removing metadata, forms, actions, tags, attachments,
or layers changes document semantics. Transparency flattening and colour-space
conversion can change rendering. JBIG2 symbol substitution is inappropriate
for financial text. Effective-DPI downsampling needs a complete graphics-state
walk. Font subsetting and merging remain the largest justified next feature,
but only after their broader reachability and glyph-closure proof is complete.

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
Skald changed 3,888 and saved 293,887,792 bytes (20.80%). Optimizer-only
latency was 6 ms p50, 48 ms p95, and 140 ms p99 on the development machine.
Every changed output returned success from qpdf, MuPDF, and Poppler. Ten
Poppler diagnostics from malformed opaque source structures were identical
before and after; Skald neither parses nor changes those structures.

The deterministic 250-largest subset contained 396,833,510 bytes. Skald 1.15
changed 179 and saved 201,273,417 bytes (50.72%). All 179 outputs passed qpdf,
MuPDF, and Poppler. Across 339 independently rendered before/after pages, the
minimum PSNR was 27.802 dB and minimum mean SSIM was 0.888447; the fixed gates
are 25 dB and 0.88. Release 1.14 saved 48.15%, 1.13 saved 38.37%, and 1.12
saved 6.54% on the same subset.

The post-1.14 fixed-point audit used the current 9,770 production PDFs
(1,127,587,226 bytes). Release 1.15 found another 14,174,908 bytes (1.26%) in
730 documents: 4,789,108 bytes came from safe soft-mask colour planes and the
remainder from lowering the lossless-raster work floor from 64 KiB to 16 KiB.
All 730 candidates passed structural and idempotence checks. A stricter
no-floor experiment saved another 1,276,448 bytes but spent more codec time
and removed the guard against documents containing many tiny image objects.
Across 1,038 rendered pages from the broader no-floor set, the minimum PSNR
was 41.380 dB and minimum mean SSIM was 0.997812.

The 71 unchanged files in that subset are accounted for: 51 are protected
documents, 15 fail codec or savings gates, four have no qualifying stream gain,
and one is excluded by image policy. The full corpus contains 117 otherwise
parseable inputs with malformed reachable Flate data and another 27 malformed,
non-standard, or encrypted inputs. All 144 remain byte-identical. Skald gives
up their small possible gains rather than normalizing around broken checksums,
empty or truncated streams, or trailing encoded data.

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
[adobe-optimizer]: https://helpx.adobe.com/acrobat/desktop/create-documents/optimize-pdfs/pdf-optimizer-settings.html
[itext-optimizer]: https://itextpdf.com/products/compress-pdf-pdfoptimizer
[apryse-optimizer]: https://docs.apryse.com/core/guides/features/optimization
[nutrient-optimizer]: https://www.nutrient.io/api/document-optimization-api/
