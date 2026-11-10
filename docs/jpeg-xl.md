# Experimental JPEG XL PDF image optimization

Since 1.21.0, `skald-image` can encode static RGB8/Gray8 JPEG XL with libjxl,
including reversible JPEG recompression. `skald-optimize-jxl` connects that
encoder to received-PDF image replacement, quality controls, resolution limits,
size gates and soft-mask preservation.

**This is an experimental PDF integration, not finalized PDF conformance or a
claim of support in released browser viewers.** `/JXLDecode` is provisional in
the [PDFium proposal](https://gist.github.com/hjanuschka/4b23e2067344b88fe27a8dd4a2a6e048).
Its name and color/alpha semantics may change. Browser HTML JPEG XL support does
not imply support for these PDF images. Standard settings still produce JPEG or
Flate, and existing ReAI consumers are unchanged.

## Application integration

Add `no.beint.skaldpdf:skald-optimize-jxl:1.21.0`, install libjxl 0.11 or newer
(Homebrew `jpeg-xl`, Debian/Ubuntu `libjxl0.11` or the distribution's current
runtime package), and grant native access to `org.skaldpdf.codec` on the module
path or `ALL-UNNAMED` on the class path. Use `SKALD_JXL` to override the library
path. No CLI or third-party Java runtime is required by the encoder.

```java
var options = OptimizeOptions.builder()
    .experimentalJpegXl(true)
    .build();
var encoder = new JpegXlImageRecompressor();
byte[] candidate = PdfOptimizer.recompress(uploadedPdf, options, encoder);
```

The default adapter preserves exact samples for originally lossless rasters and
uses reversible JPEG recompression for unchanged JPEGs. Reversible output retains
JPEG reconstruction metadata; the reference `djxl` can recreate the original
JPEG bytes exactly. This avoids another lossy JPEG generation.

To deliberately allow lossy photographs while keeping logos/graphics lossless:

```java
var encoder = new JpegXlImageRecompressor(
    JpegXlOptions.photos(), JpegXlOptions.lossless());
```

Distance is a libjxl perceptual distance, **not JPEG quality percent**:

| Setting | Distance | Effort |
| --- | ---: | ---: |
| `lossless()` | 0 | 7 |
| `highQuality()` | 0.5 | 7 |
| `photos()` | 1 | 7 |
| `compact()` | 2 | 7 |

`new JpegXlOptions(distance, effort)` accepts distance 0–25 and effort 1–9.
Higher effort increases CPU cost. Lower distance usually means higher quality,
but does not guarantee smaller files than an existing JPEG. For unchanged JPEGs,
a requested lossy candidate is compared with reversible recompression; the
smaller candidate wins. The optimizer retains original image/document data when
configured savings gates are not met. See [measured results](codec-benchmarks.md).

The adapter can resize to `maxEdge`/placed-resolution limits without inserting an
intermediate JPEG encoding. Raw numeric samples are area-averaged; grayscale
rasters retain one component. Separate PDF soft masks retain their dimensions or
are resampled with their parent when the existing optimizer allows it.

## Boundaries

This covers **eligible images**, not every possible PDF image. Unsupported color
spaces, ICCBased images, JPEGs with embedded ICC profiles, non-identity Decode arrays, unsafe masks, JPEG 2000,
JBIG2, CCITT and other unsupported image decoders are kept unchanged. Lossy
conversion of grayscale JPEGs is conservatively skipped. Existing JXL images
are not repeatedly re-encoded, so changing a quality option does not silently
apply another lossy generation to them. RGB/gray static JXL must agree with the
PDF image's dimensions and component count. Embedded alpha, animation,
orientation transforms, HDR and high-precision JXL are rejected by this PDF path.

Encoding/PDF decoding is capped at 20 MP, encoded image data at 32 MiB, and
allocations routed through libjxl's memory manager at 512 MiB per operation.
These are not whole-process memory limits: input/output buffers and any library
allocations outside that manager are additional. Codec failures keep the
original image through the optimizer's existing fail-safe behavior.

New output requires both `experimentalJpegXl(true)` and the JXL adapter.
Low-level creation uses `ImageData.fromExperimentalJpegXl(...)` plus
`WriterProperties.withExperimentalJpegXl()`. Output is marked with
`/SkaldExperimentalJXL true`; this is a Skald marker, not a registered standard
extension declaration. Unvalidated conformance declarations are rejected by
canonical extended-output rewriting. This does not produce PDF/A or PDF/X.
Ordinary JPEG XL photo ingest via `NativeImages.prepare` still converts to a
conventional JPEG; its behavior has not become experimental output implicitly.

## Verification and readiness

Tests cover lossless RGB/gray samples, JPEG byte reconstruction with `djxl`,
quality variants, explicit gates, dimensions, masks, repeat optimization and
complete lossless page rendering through independently decoded image streams
and PDFBox. `scripts/verify-jxl.py` is a test-only normalizer for Skald output;
it is not a general PDF parser. CI installs libjxl/tools, requires the native
cases to run, and uploads generated experimental viewer samples.

These tests establish codec/Skald integration, **not browser interoperability**.
Before enabling this in ReAI, reconcile the final PDF embedding specification,
verify the target PDF viewers, and run representative visual acceptance tests.
The provisional filter/color contract may require a library update first.
