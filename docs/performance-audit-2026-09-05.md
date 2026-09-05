# Performance audit, 5 September 2026

Audited `cadab53` (1.15.0), including the build, font measurement, layout,
generation, PDF parsing/extraction, and attachment optimization paths. Both
Ecomtools and ReAI pin 1.15.0. Ecomtools uses `ProductSticker.pdf`; ReAI uses
layout/fonts for invoices, agreements, reports, receipts and attachments, plus
JPEGli attachment optimization. This is a targeted performance audit, not a
security or full PDF conformance review.

## Changes

1. **Avoid glyph arrays when measuring text.** `PdfFont.getWidth` previously
   created a `GlyphRun`, including code-point/glyph arrays and their defensive
   copies, just to read its advance. It now sums the same rounded glyph widths
   directly. Unicode handling, missing-glyph behavior, rounding, the public API,
   and the immutable `GlyphRun` contract are preserved.
2. **Compile numeric patterns once.** The structural PDF parser and text
   extractor used `String.matches` for each numeric token, compiling the same
   regex repeatedly. They now share immutable patterns within each class and
   create a fresh matcher per call. The accepted grammar and thread isolation
   are unchanged. The JDK documents why [reusing compiled patterns avoids this
   repeated work](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/regex/Pattern.html).
3. **Make configuration caching work and enable parallel builds.** The original
   `build --configuration-cache` failed with 17 reported problems. Runtime
   dependency checks captured `Project` and live configurations in task actions.
   A typed task now checks declared coordinate inputs obtained from the resolved
   dependency graph; all modules retain the dependency restriction and only the
   JPEGli adapter permits Glimt. Release-version printing is also compatible.
   This follows [Gradle's configuration-cache requirements](https://docs.gradle.org/current/userguide/configuration_cache_requirements.html).
4. **Keep a repeatable performance probe.** `:skald-layout:performanceBenchmark`
   reports latency and allocation without adding production dependencies or
   making noisy timing thresholds part of ordinary tests.

## Runtime measurements

Apple M5 Max, 18 CPU cores, 64 GiB RAM, OpenJDK 26.0.2.1, fixed 512 MiB Java heap.
The same probe ran against the original and changed runtime, in two fresh JVMs
each. Each scenario has one second of warmup and three one-second measurement
windows. Values below are medians of the six windows. The probe consumes results;
fixture construction and independent PDF validation are outside timed operations.
[Raw samples](benchmarks/performance-audit-2026-09-05.csv) are included.

| Operation | Before | After | Java allocation before → after |
| --- | ---: | ---: | ---: |
| Text-width measurement | 179 ns | 119 ns | 480 B → 0 B |
| ReAI-style invoice | 3.60 ms | 3.28 ms | 2.32 MB → 2.15 MB |
| Ecomtools sticker | 0.481 ms | 0.477 ms | 536 KB → 534 KB |
| Generate 1,000-row report | 34.33 ms | 32.19 ms | 85.77 MB → 63.45 MB |
| Parse that report | 0.368 ms | 0.216 ms | 2.11 MB → 0.96 MB |
| Extract its text | 87.29 ms | 27.82 ms | 386.65 MB → 64.13 MB |

The robust wins are 41% lower parsing latency, 68% lower extraction latency,
83% less extraction allocation, and 26% less report-generation allocation.
Sticker latency is effectively unchanged. The smaller invoice/report timing
differences need longer application benchmarks before being treated as production
capacity gains. MB/KB above are decimal; allocation excludes native memory.
Text extraction is a library benchmark, not evidence that either application
currently spends significant time in it. Attachment optimization benefits from
the shared parser, but its end-to-end gain was not measured on private documents.

## Build measurements

On the same changed source tree, alternating two runs of each mode with warmed
dependencies/daemon and task-output caching explicitly disabled:

| Clean build mode | Run 1 | Run 2 |
| --- | ---: | ---: |
| `--no-parallel --no-configuration-cache` | 37.12 s | 34.46 s |
| New parallel/configuration-cache defaults | 15.62 s | 14.04 s |

Both used `./gradlew clean build -PreleaseVersion=1.15.1-audit --no-build-cache`;
only the flags above differed. These are elapsed process times including Gradle
startup. The first new-default run stored the configuration and the second
reused it. Most of the clean-build improvement comes from parallel module tasks;
the benefit depends on available CPU/RAM. This comparison isolates build settings
from runtime changes. Use `build` without `clean` during normal development to
retain incremental outputs.

## Validation

- Full build and local Maven publication using `-PreleaseVersion=1.15.1-audit`:
  242 tests, zero failures/errors, two optional private-corpus tests skipped.
- Java warnings as errors, JPMS compilation, Javadocs and dependency restrictions
  all pass. Configuration cache stores and reuses successfully. A temporary
  runtime dependency injected into core was rejected on both the first run and
  cache reuse; release-version printing also passed on both runs.
- Added width/glyph equivalence coverage for all four bundled faces, fractional
  sizes, Norwegian text, combining characters, supplementary code points,
  unpaired surrogates, missing glyphs and empty strings.
- Added minimal PDF fixtures for signed integers/decimals in objects/content and
  rejection of malformed numbers, exponent notation and non-integer rotations.
- All 91 generated use-case PDFs pass `qpdf --check`. Poppler renders of all
  102 pages at 72 DPI are pixel-identical to the original runtime; extracted
  layout text is also identical. Invoice and sticker previews were inspected.
  Raw PDF bytes can differ because existing font-object ordering varies between
  JVMs; that behavior predates these changes.
- `scripts/validate-pdf2.sh build/use-case-pdfs` passes the independent Arlington
  PDF 2.0 object-model validator for all 91 generated fixtures.

## Remaining opportunities

| Opportunity | Assessment |
| --- | --- |
| Gradle task-output caching | Model generated PDF/report outputs and optional corpus inputs first. Tests currently write into root `build` and sometimes `~/Downloads`; blindly enabling the build cache can restore test results without recreating required PDFs. |
| Font cmap lookup and precomputed widths | `TrueTypeFont.Format4.glyph` scans segments and widths are rounded repeatedly. Worth profiling next, but changes need broader custom-font fixtures before replacing lookup behavior. |
| Repeated table measurement/tokenization | Layout measures cell content and later lays it out again. Caching may help large reports, but mutable styles/elements require careful cache lifetimes and invalidation. |
| Writer/parser peak memory | Encoded page streams, object graphs and final byte arrays remain resident. Streaming/spooling and internal ownership changes would be larger API/lifecycle work; retain defensive copies at public boundaries. |
| Image compression and concurrency | Existing direct JPEG embedding, explicit image policy, Glimt codecs and ReAI's four-permit optimization limit are sensible. Keep quality, signature protection, equivalence verification and parser limits intact. |

The optional module graph is already lean: the core has no production runtime
dependencies, fonts are lazily shared, and Ecomtools' sticker module does not
require the layout engine. Both applications consume published JARs, so build
improvements here primarily benefit library development and releases.

After review, publish a new patch release and update the shared `skald-pdf`
version in each application's `gradle/libs.versions.toml`. No consumer changes,
Maven Central release or production deployment are part of this audit.
