# Optional native image codecs

`skald-image` (`org.skaldpdf.codec`) binds **TurboJPEG** and **libheif** with
the JDK FFM API. It is not on the core classpath. Invoices, stickers, and
signatures work without it.

```kotlin
implementation("no.beint.skaldpdf:skald-image:1.5.0")
```

```text
java --enable-native-access=org.skaldpdf.codec ...
```

On the class path use `--enable-native-access=ALL-UNNAMED`.

## What it is for

- HEIC/AVIF from phones → packed RGB → JPEG `ImageData`
- Faster / smaller JPEG encode than `ImageIO` for noisy photos
- Explicit `prepare(bytes, PrepareOptions.photos())` so quality and max edge
  are a policy, not a surprise

## What it is not

- Not ImageMagick
- Not a rewriter of images already inside a received PDF (that is still
  `skald-optimize`, after the parser can replace XObjects)
- Not required at runtime. `NativeImages.jpegAvailable()` /
  `heifAvailable()` are false when the `.dylib` / `.so` is missing.

Override search paths with `SKALD_TURBOJPEG` and `SKALD_HEIF`.
