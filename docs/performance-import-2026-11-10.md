# Imported stream allocation audit — 10 November 2026

Image enumeration previously cloned each image stream before the document-level
deduplication step. A large image shared across pages therefore caused one full
copy per page, even when the application only requested its dimensions or encoded
length. `EmbeddedImage` now refers to the immutable parsed stream. Its public
`encodedBytes()` method still returns a defensive copy.

Internal stream length checks, graph hashing and unencrypted import writing also
avoid copying the payload merely to measure, hash or copy it to the writer's
buffer. Constructor ownership remains defensive; encryption still uses a separate
buffer. No public API, codec policy or output format changes are required.

## Reproduce

```shell
./gradlew :skald-core:importPerformanceBenchmark
```

The retained harness creates a deterministic synthetic RGB noise image, encodes
it as a 793,465-byte JPEG with the JDK, and shares it across 32 pages. The generated
PDF is 799,009 bytes. Data is generated in memory; no customer files or native
codecs are needed. Inspection and hashing reuse an already parsed document;
canonical rewriting includes parsing, preservation checks, writing and post-write
graph verification. The benchmark does not enable stream recompression.

Measurements below use Apple M5 Max, JDK 27 and a fixed 512 MiB heap. For each
revision, two fresh JVMs each ran a one-second warmup and three one-second timed
windows per scenario. Values are medians of the six windows. Allocation counters
cover the calling Java thread, not process memory or peak live heap. Baseline
production code is `3c4d8d0`, with the same benchmark harness added.

| Scenario | Before bytes/op | After bytes/op | Allocation reduction | Before µs/op | After µs/op |
| --- | ---: | ---: | ---: | ---: | ---: |
| Inspect shared image | 25,413,272 | 24,460 | 99.90% | 476.7 | 4.7 |
| Semantic graph digest | 905,223 | 115,332 | 87.26% | 309.9 | 288.9 |
| Complete canonical rewrite | 16,387,690 | 14,011,220 | 14.50% | 1,786.3 | 1,813.9 |

[Raw measurements](benchmarks/import-stream-copies-2026-11-10.csv)

The inspection improvement scales with shared image size and page count. It is
not a claim that every PDF operation is 100× faster. End-to-end rewrite timing
varied between JVM runs, so this audit claims reduced allocation there, not a
throughput improvement. These changes do not reduce PDF file size: the encoded
image bytes and the serialization contract are preserved. No wall-clock threshold
is enforced in CI.

Regression tests cover shared-image enumeration, defensive ownership and returned
arrays, stream hashing, and preservation of the original JPEG bytes in a PDFBox
read of the rewritten 32-page file. The standard build also covers encryption,
canonical graph preservation, codecs and module/dependency checks. A separate
JPEG XL regression exercises explicit lossy optimization of uploaded raster PDFs
at distances 1 and 2 and confirms that decoded samples actually change.
