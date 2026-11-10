# Codec benchmark results (1.21.0 development, 2026-11-10)

These are measured results, not a universal compression promise. Machine: Apple
M5 Max, macOS ARM64, JDK 27, libbrotli 1.2.1, libjxl 0.12.0. Native JXL encoding
is single-threaded. Re-run the retained benchmark tasks for deployment hardware.
No private PDFs or image pixels are included in these results or the repository.

## Lossless PDF stream compression

91 generated public Skald use-case PDFs total 2,138,001 bytes. The benchmark
loads files before timing, warms each policy across the complete corpus, then
runs three timed passes with cyclic policy order. Time is the sum of per-document
median operation times. It uses the actual PDF optimizer with image conversions,
font pruning and deduplication disabled, so this isolates lossless stream/graph
rewriting. Conformance-profile protection and positive-savings gates remain on;
files that cannot profitably be rewritten are retained. File I/O is excluded.

| Policy | Output bytes | Saved vs inputs | Changed PDFs | Time, ms |
| --- | ---: | ---: | ---: | ---: |
| Flate maximum | 2,102,764 | 1.65% | 90 | 507 |
| Brotli fast | 2,137,604 | 0.02% | 7 | 255 |
| Brotli balanced | 2,042,575 | 4.46% | 90 | 470 |
| Brotli maximum | 1,869,525 | 12.56% | 90 | 10,440 |

Brotli maximum is 11.09% smaller than the Flate maximum result here, at roughly
22 times balanced's measured processing time. Balanced is 2.86% smaller than Flate
maximum in aggregate, but it does not win on every document.

The separately maintained ReAI fictional invoice makes that tradeoff visible:

| Policy | Output bytes | Saved vs 35,275-byte input | Time, ms |
| --- | ---: | ---: | ---: |
| Flate maximum | 33,134 | 6.07% | 25.5 |
| Brotli fast | 35,275 (original retained) | 0% | 6.2 |
| Brotli balanced | 33,890 | 3.93% | 10.5 |
| Brotli maximum | 28,458 | 19.33% | 196.7 |

Choosing Brotli balanced blindly would be worse than Flate maximum for this
invoice. Maximum offers better size at a real latency cost. Already-compressed
JPEG images remain JPEG in this benchmark; Brotli does not recompress them.

```sh
./gradlew :skald-layout:test --rerun-tasks
./gradlew :skald-brotli:compressionBenchmark
./gradlew :skald-brotli:compressionBenchmark \
  -PbenchmarkCorpus=skald-core/src/test/resources/codec/reai-invoice/reai-invoice-flate.pdf
```

The task writes `build/benchmarks/brotli-compression.csv`. The optional input may
be a directory or a single PDF. Do not commit private corpora or their outputs.

## JPEG XL images and complete image-only PDFs

Six distinct eligible DeviceRGB JPEG scans were selected in sorted source order
from the local uploaded-PDF corpus, with dimensions at least 256 × 256 and at
most 4 MP. This is a small scanned-document subset, **not a general photo,
logo, grayscale, ICC or whole multi-page document benchmark**. Inputs total
1,808,645 JPEG bytes. Encoding is warmed, then timed three times per setting;
the table sums the per-image median times. Each encoded candidate is validated
and decoded, and PSNR is measured against ImageIO-decoded source samples.

| Distance, effort 7 | Encoded image bytes | Image saving | Time, ms |
| --- | ---: | ---: | ---: |
| 0, reversible JPEG | 1,479,905 | 18.18% | 534–738 |
| 0.5 | 2,359,508 | −30.46% | 12,128 |
| 1 | 1,947,250 | −7.66% | 12,832 |
| 2 | 1,398,765 | 22.66% | 12,490 |

Negative savings mean larger candidates. Lossy re-encoding can be both slower
and larger than reversible JPEG recompression. The adapter therefore defaults
to reversible JPEG recompression and, for unchanged JPEGs with a requested lossy
setting, chooses the smaller of reversible and requested candidates.

The same six images were wrapped in complete in-memory image-only PDFs and run
through `PdfOptimizer` with the actual JXL adapter and savings gates. Total input
PDF size was 1,819,054 bytes. Default reversible output was 1,488,560 bytes
(**18.17% smaller**). Requested distances 0.5 and 1 selected the same reversible
results. Distance 2, with per-image reversible fallback, produced 1,402,214 bytes
(**22.92% smaller**). No private image/PDF files were written or uploaded.

For raw lossy distance-2 candidates, per-image PSNR ranged from 39.23 to 44.75 dB. This is a numerical
diagnostic, not a substitute for visual approval of text/photos. Reversible JPEG
output can have finite pixel PSNR against ImageIO because JPEG decoders differ
in rounding; exact original JPEG byte reconstruction is independently tested
with `djxl`. Originally lossless raster samples are tested for exact equality.

```sh
./gradlew :skald-optimize-jxl:imageBenchmark \
  -PbenchmarkCorpus=/path/to/local/pdf-corpus
# Faster check of the default reversible policy only:
./gradlew :skald-optimize-jxl:imageBenchmark \
  -PbenchmarkCorpus=/path/to/local/pdf-corpus -PbenchmarkLosslessOnly=true
```

The task writes aggregate, non-identifying rows to
`build/benchmarks/jpeg-xl-images.csv`. Whole-PDF sizes are measured through the
optimizer; reported timings cover image encoding/validation, not the complete
optimizer operation. A final focused reversible-only run after profile/resource hardening measured
738 ms for the same six images, with identical output sizes. Timing is elapsed
wall time and varies with machine load; it is not a CPU-cycle measurement.
Native managed-allocation/pixel limits apply. Neither this
benchmark nor test normalization proves support in browser PDF viewers. Read
[experimental JPEG XL readiness](jpeg-xl.md) before any consumer opt-in.
