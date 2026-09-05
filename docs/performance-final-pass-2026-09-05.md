# Final performance pass, 5 September 2026

The first audit was merged as PR #18 (`fb1ba5d`) after local and Linux CI builds,
publication checks, rendering comparisons and independent PDF validation passed.
This pass starts from that commit.

## Retained change

Validate numeric tokens with a shared ASCII scan instead of allocating regex
matchers. The structural parser and text extractor accept exactly the previous
integer/decimal grammar: optional leading sign, ASCII digits, at most one decimal
point for real numbers, and at least one digit. Exponent notation, whitespace,
Unicode digits and malformed punctuation remain rejected. Existing numeric
conversion, range checks and parser safety limits still apply after this check.

The scanner has no mutable shared state or per-call allocation. It removes
remaining matcher overhead from PR #18's precompiled patterns. The benchmark now
accepts scenario names so individual workloads can be compared in fresh JVMs.

## Measurements

Compared the merged PR #18 core JAR with the final numeric-only change using the
same benchmark classes, dependency classpath, OpenJDK 26.0.2.1 and 512 MiB heap
on the Apple M5 Max from the first audit. Runs alternated before/after/before/after,
each in a fresh JVM, selecting `parse-report extract-report`. Each scenario used
one second of warmup and three one-second measurement windows. Only the core JAR
was swapped. Values are medians of six windows per version; MB are decimal.

| Operation on a 1,000-row report | Before | After | Allocation before → after |
| --- | ---: | ---: | ---: |
| Parse | 0.380 ms | 0.184 ms | 0.963 MB → 0.810 MB |
| Extract text | 29.76 ms | 21.31 ms | 63.92 MB → 30.34 MB |

[Raw samples](benchmarks/performance-final-pass-2026-09-05.csv) are included.
Allocation falls by about 16% for parsing and 53% for extraction, relative to
PR #18. Timing varied substantially during this session, so these latency values
are observations rather than production throughput promises. The allocation
reduction and grammar equivalence are the reasons for retaining this change.
No additional generation/build speedup is claimed.

## Correctness

- `clean build publishToMavenLocal -PreleaseVersion=1.15.1-audit` passes with
  245 tests: 243 passed, two optional private-corpus tests skipped. This includes
  compiler warnings as errors, JPMS, Javadoc and runtime-dependency checks.

- Differential tests compare both numeric predicates with the previous regexes
  for all 111,111 strings of length zero through five over a ten-character
  alphabet (digits, signs, dot, exponent marker, whitespace and non-ASCII input).
- Additional comparisons cover every UTF-16 code unit embedded in a token,
  supplementary characters, all ten decimal digits and decimal edge cases.
- Long-token tests exercise 30,000-digit input and invalid suffixes; existing
  minimal PDF tests cover object/content parsing and rejected numeric boundaries.
- All 91 generated PDFs pass qpdf and Arlington PDF 2.0 validation. Poppler
  rendering of all 102 pages at 72 DPI is pixel-identical to PR #18, and extracted
  layout text is unchanged. Invoice and sticker previews were inspected again.

## Other candidates

Precomputing rounded font widths at font load was implemented experimentally and
checked against every bundled glyph plus altered units and trailing horizontal
metrics. The [OpenType hmtx specification](https://learn.microsoft.com/en-us/typography/opentype/spec/hmtx)
defines how the final advance applies to trailing glyphs. Equivalence checks
passed, but end-to-end timing did not show a consistent benefit, so that change
was removed from the final patch.

The final review also revisited repeated table layout work, font cmap lookup,
writer/parser buffer ownership and build-output caching. Those remain the larger
follow-ups described in the first audit: each needs lifecycle, memory or fixture
work beyond this small numeric change. Image quality and optimization correctness
checks remain central constraints on any later compression work.
