# Performance and compression

Skald optimizes the creation path instead of running a second whole-document
optimizer:

- top-level layout elements are consumed incrementally;
- page content uses one compressed stream per page;
- fonts are subset and embedded once per font face per document;
- text-width measurement walks Unicode code points without allocating glyph arrays;
- structural and content parsers share an allocation-free ASCII numeric validator;
- unused OpenType layout tables are dropped from the subset so a Latin
  invoice stays tens of kilobytes instead of carrying the whole face;
- small structural objects are grouped into PDF 2.0 object streams;
- the cross-reference stream is compact and compressed;
- lossless raster rows select the best PNG predictor before Deflate;
- JPEG byte streams are embedded directly, avoiding quality loss and CPU cost;
- repeated image instances and opacity states share indirect objects;
- received PDFs share only byte-identical embedded font program streams while
  retaining separate descriptors, encodings, and font resources;
- compact CID width ranges reduce font dictionaries;
- adjacent generated text shares colour/font/text state and uses positioned
  `TJ` arrays instead of repeating a complete text object for every run;
- content-stream numbers are encoded without temporary formatted strings;
- batched barcode and QR rectangles reduce content operators;
- bounded parsers reject decompression and object-count abuse early;
- Deflate uses try-with-resources on `java.util.zip.Deflater`.

Generation is measured on JDK 26. Compact object headers and the JDK 26 G1
throughput work apply without extra flags. Preview APIs (`LazyConstant`,
Vector, PEM) were measured or reviewed and are not used: they do not beat
the current holder-class, packed-int, and JCA paths enough to justify
`--enable-preview`.

Compression has four immutable policies: `NONE`, `FAST`, `BALANCED`, and
`MAXIMUM`. `BALANCED` is the default. Deflate level changes CPU/size trade-offs;
it does not alter image quality.

Downsampling or recompressing photographs can reduce files further, but it is
intentionally not automatic. The correct pixel density and quality depend on
whether a document is for screens, office printers, archival storage, or evidence.
Applications should make that policy explicit before passing image bytes.
`RasterImages.scaleToFit` and `RasterImages.asJpeg` in the optional
`skald-image` module implement that policy. Received files use `skald-optimize`
with an explicit `OptimizeOptions`; `skald-optimize-jpegli` adds Glimt's native
Mitchell resize and JPEGli encoder. Raster conversion uses a bulk
`BufferedImage.getRGB` / `setRGB` path; the old per-pixel accessors are kept
only as a measured baseline in tests.

`PdfCorpusBenchmarkTest` is an opt-in private-corpus gate. Set
`SKALD_PDF_CORPUS` to a directory of PDFs. It records bytes and latency, loads
every result through PDFBox, and reruns every changed file to assert exact
idempotence. Private customer documents are never copied into the repository.

## Generation harness

Run `./gradlew :skald-layout:performanceBenchmark` for an opt-in throughput and
allocation probe covering text measurement, ReAI-style invoices, Ecomtools
stickers, 1,000-row reports, parsing, and extraction. It uses a one-second warmup
and three one-second measurement windows per scenario, with a fixed 512 MiB heap.
Run it in multiple fresh JVMs on an otherwise idle machine; it is not a CI timing
assertion or a substitute for application profiling. Allocation counters cover
the calling Java thread, not native codec memory.

Select individual scenarios with, for example,
`./gradlew :skald-layout:performanceBenchmark --args="parse-report extract-report"`.

See the [September 2026 audit](performance-audit-2026-09-05.md) for measurements,
verification, and remaining opportunities, and the
[final pass](performance-final-pass-2026-09-05.md) for the subsequent numeric
validation improvement.

`GenerationHarness` times and sizes the ReAI + typical document corpus. Tests
write `build/benchmarks/latest.{json,md}` (and `~/Downloads/skald-benchmarks`
when present). Size is asserted against
`skald-layout/src/test/resources/benchmarks/baseline.json`. Wall-clock is
recorded and bounded generously so CI machines do not flake.

A production ReAI invoice that uses unembedded Helvetica is about 2 KiB. Skald
will not match that number: PDF 2.0 output embeds a compact TrueType subset, so
an ordinary invoice lands around 25–40 KiB. That is the size of correctness, not
a regression.

## Table layout

Percent columns now honour min-content (the longest unbreakable header/body
token plus padding) before leftover width is distributed. Hairline rules are
stroked *inside* the cell so a 1.25 pt separator cannot paint through the
previous row. `Table.addRule` is the invoice-style separator.

The current writer retains encoded page streams and the final object table until
close. Incremental layout bounds the much larger semantic element graph, but
extreme multi-gigabyte output should eventually spool encoded streams to bounded
temporary storage.
