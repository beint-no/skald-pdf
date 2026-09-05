# Optional image processing and native codecs

`skald-image` (`org.skaldpdf.codec`) owns standard JDK image decoding,
downscaling, and JPEG re-encoding. It also binds **TurboJPEG**, **libheif**,
and **libjxl** with the JDK FFM API. It is not on the core classpath. Invoices,
stickers, signatures, and callers that provide prepared `ImageData` work without it.

```kotlin
implementation("no.beint.skaldpdf:skald-image:1.15.1")
```

```java
var logo = RasterImages.decode(pngBytes);
var photo = RasterImages.scaleToFit(RasterImages.decode(jpegBytes), 1600, 1600);
var compressed = RasterImages.asJpeg(photo, 0.82f);
```

The module requires `java.desktop`; `skald-core` requires only `java.base`.
The native methods below additionally need native-access permission.

```text
java --enable-native-access=org.skaldpdf.codec ...
```

On the class path use `--enable-native-access=ALL-UNNAMED`.

## What it is for

- HEIC/AVIF from phones → packed RGB → JPEG `ImageData`
- JPEG XL photos → packed RGB → JPEG `ImageData` (decode only)
- Faster / smaller JPEG encode than `ImageIO` for noisy photos
- Explicit `prepare(bytes, PrepareOptions.photos())` so quality and max edge
  are a policy, not a surprise

## JPEG XL and the PDF standard

JPEG XL is ISO/IEC 18181. It is **not** part of ISO 32000-2:2020 (PDF 2.0).
In October 2025 Peter Wyatt (CTO, PDF Association) said at PDF Days Europe
that the association has picked JPEG XL as the *preferred* HDR image format
for a future PDF specification. There is no published filter name that
Acrobat, Preview, or PDFBox must implement. Experimental PDFium work uses a
placeholder `/JXLDecode` that may change.

Therefore Skald:

- **does** decode JPEG XL as photo ingest, the same way it decodes HEIC;
- **does not** emit JPEG XL inside a PDF 2.0 file;
- **will** add a writer filter only after ISO publishes one and the major
  viewers ship it.

PDF/A and PDF/X still require converting JXL to an existing filter
(`DCTDecode` or Flate). `NativeImages.prepare` always returns a JPEG
`ImageData`.

## What it is not

- Not ImageMagick
- Not a rewriter of images already inside a received PDF (that is
  `skald-optimize`)
- Native libraries are not required for the JDK codec path.
  `NativeImages.jpegAvailable()` / `heifAvailable()` /
  `jpegXlAvailable()` are false when the `.dylib` / `.so` is missing.

Override search paths with `SKALD_TURBOJPEG`, `SKALD_HEIF`, and `SKALD_JXL`.

## Jpegli

Jpegli is a better *JPEG* encoder (still `DCTDecode`). It lives in the
libjxl tree as a static extra, not as a commonly installed `libjpegli`
shared library. Homebrew `jpeg-xl` 0.12 ships `libjxl` / `libjxl_cms` /
`libjxl_threads` only.

Received-PDF optimization uses the separate `skald-optimize-jpegli` adapter.
It delegates to Glimt's modular, bundled JPEGli FFM runtime, so consumers do
not need a system executable or a commonly installed `libjpegli` shared
library. `skald-image` keeps TurboJPEG for its smaller optional ingest path.
