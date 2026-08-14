# Performance and compression

Skald optimizes the creation path instead of running a second whole-document
optimizer:

- top-level layout elements are consumed incrementally;
- page content uses one compressed stream per page;
- fonts are subset and embedded once per font face per document;
- unused OpenType layout tables are dropped from the subset so a Latin
  invoice stays tens of kilobytes instead of carrying the whole face;
- small structural objects are grouped into PDF 2.0 object streams;
- the cross-reference stream is compact and compressed;
- lossless raster rows select the best PNG predictor before Deflate;
- JPEG byte streams are embedded directly, avoiding quality loss and CPU cost;
- repeated image instances and opacity states share indirect objects;
- compact CID width ranges reduce font dictionaries;
- content-stream numbers are encoded without temporary formatted strings;
- batched barcode and QR rectangles reduce content operators;
- bounded parsers reject decompression and object-count abuse early.

Compression has four immutable policies: `NONE`, `FAST`, `BALANCED`, and
`MAXIMUM`. `BALANCED` is the default. Deflate level changes CPU/size trade-offs;
it does not alter image quality.

Downsampling or recompressing photographs can reduce files further, but it is
intentionally not automatic. The correct pixel density and quality depend on
whether a document is for screens, office printers, archival storage, or evidence.
Applications should make that policy explicit before passing image bytes.
`ImageData.scaledToFit` and `ImageData.asJpeg` exist for that policy.

## Generation harness

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
