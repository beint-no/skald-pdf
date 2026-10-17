# Recompressing received PDFs: what the codecs are actually worth

Measured October 2026 on documents from a real Norwegian accounting-system
export (486 PDFs, 327 MB) plus 40 JPEG streams lifted out of received business
PDFs. Every figure below is **bytes at equal measured quality**, not bytes at
equal quality *setting*; comparing encoders at the same quality number compares
nothing. Quality is SSIMULACRA2, and each image's own rate-distortion curve is
interpolated to the target score.

## What can go in a PDF

ISO 32000-2 offers `DCTDecode` and `JPXDecode` for continuous-tone images.
That is the whole menu, so the only codec decision available is which JPEG
encoder writes the `DCTDecode` stream.

| encoder | bytes vs the stored JPEG at SSIMULACRA2 80 | note |
|---|---|---|
| `jpegtran -optimize` | −5.5 %, pixel-identical | free, no quality question |
| libjpeg-turbo | −0.5 % | what Ghostscript, MuPDF and qpdf use |
| **JPEGli (Glimt)** | **−9.0 %** | what `skald-optimize-jpegli` uses |
| JPEG 2000 | −24 % at 80 but falls off a cliff below it | and `JPXDecode` support in readers is patchy |

Over the strict subset where both encoders bracket the target, JPEGli is 12.9 %
smaller than libjpeg at SSIMULACRA2 75, 9.0 % at 80 and 3.9 % at 85.

Chroma subsampling was re-checked for document scans rather than assumed:
4:2:0 is 7.6 % smaller than 4:4:4 at SSIMULACRA2 80, 9.3 % at 85 and 10.6 % at
88. `JpegliImageRecompressor` is right to fix 4:2:0.

## What cannot go in a PDF, and what it would be worth

| encoder | bytes vs the stored JPEG at SSIMULACRA2 80 |
|---|---|
| libjxl | −17.1 % |
| jixel | −23.5 % |
| AVIF (libaom, 4:4:4) | −24.4 % |

Against JPEGli at equal quality a JXL filter would save a further 9.9 % at
SSIMULACRA2 75, 10.3 % at 80 and 11.0 % at 85. That is the entire price of the
deferral in [standards.md](standards.md), and it is small enough to wait for a
published filter rather than guess at `/JXLDecode`.

Two things worth recording because they contradict the usual claims:

* **jixel beats libjxl on SSIMULACRA2** — 4.4 % to 8.3 % smaller at equal score
  on this content. It loses on butteraugli, so the ranking is a statement about
  the metric, not about the encoder.
* **AVIF beats both**, by 7.2 % to 16.7 % over libjxl at equal SSIMULACRA2. For
  anything that does not have to be a PDF, AVIF is already the right answer and
  Glimt already encodes it.

Lossless JPEG → JXL transcoding, which reconstructs the original bitstream byte
for byte, saves 19.2 % with libjxl `-e 9` and 17.6 % with jixel; jixel is 16×
faster at it. It was 40/40 byte-exact for both. It is still not used here,
because a PDF cannot carry the result: it only helps an archive format that
unwraps the PDF, and a bespoke container is a poor bet for documents with a
statutory retention period.

## The whole-document ladder

50 PDFs from the export, 93.6 MB, rendered at 150 dpi and scored page by page
against the original rendering. The export attaches the same scan to many
vouchers, so the totals are weighted towards a handful of distinct pictures;
the per-document ordering below was the same for every one of them.

| policy | bytes | worst page | mean page |
|---|---|---|---|
| original | 93.60 MB | 100 | 100 |
| lossless only (`qpdf` + `jpegtran`) | 93.56 MB (−0.04 %) | 100 | 100 |
| 150 dpi + libjpeg quality 65, via Ghostscript/MuPDF | 2.94 MB (−96.9 %) | **55.3** | 88.7 |
| `OptimizeOptions.attachments()` — 2400 px, quality 75 | 3.26 MB (−96.5 %) | 89.3 | 96.2 |
| `OptimizeOptions.archive()` — 1754 px, quality 68 | 3.00 MB (−96.8 %) | 88.5 | 95.9 |
| 1240 px, quality 55 | 2.79 MB (−97.0 %) | 84.8 | 94.9 |

Two things follow. Lossless recompression is worth nothing on this corpus: the
images arrive as full-resolution `FlateDecode` rasters, so the cost is
resolution and format, not entropy coding. And the conventional 150 dpi
Ghostscript pass is strictly dominated — `archive()` lands on the same bytes
while holding the worst page 33 points higher, and 1240 px/55 is *smaller*
than the Ghostscript output at a worst page of 84.8 against 55.3.

## Dropping the PDF entirely

For the four distinct pictures in that sample, encoding the picture as AVIF
instead of wrapping a JPEG in a PDF needed 63–74 % fewer bytes than the JPEG
stream alone, at an equal or better SSIMULACRA2, and 78–85 % fewer than the
whole PDF file — the container was 44 % of these files. The gap is much wider
than the −24 % AVIF showed at SSIMULACRA2 80 because a 68-quality downsampled
scan sits at 76–82, and AVIF's advantage widens as quality falls (−53 % at 75,
−37 % at 70).

That is a storage argument, not a document argument. A PDF that *arrived* as a
PDF is the received record and should stay one. Where the PDF is only a wrapper
the application added around a photograph, the picture is the record and the
wrapper is a choice.

Archiving afterwards still helps, because near-duplicate documents remain:
`tar --zstd -19 --long=29` took the `archive()` output from 3.00 MB to 1.59 MB.
Compressing the originals instead gives 8.21 MB, so recompressing first and
archiving second is worth about 5× over archiving alone. The ordering matters
more than the codec.

## A correctness bug this found

One document rendered at SSIMULACRA2 −14 after a 1240 px pass, against 93 for
the same file at 1754 px. Its producer
had registered the image's soft mask in the page resources under its own name
as well as referencing it from `/SMask`. Enumerated on its own the mask looks
like an ordinary `DeviceGray` raster, so it was resampled to 1190×1240 and
re-encoded as a `DeviceRGB` JPEG while its parent still pointed at it and still
claimed 1523×1587. `requiresOriginalDimensions()` protected the parent; nothing
protected the mask.

`NativePdfParser` now collects every stream reached through `/SMask` or `/Mask`
during traversal and excludes those from `importedImages()`, which is what
`safeSoftMask`'s "a preserved soft mask remains byte exact" already promised.
Filtering happens after the whole traversal because a mask can be enumerated
before its parent.

The trigger needs the mask to be reachable as a named resource *and* for the
policy to want to change it: any `maxEdge` below the mask's long edge resamples
it, and a mask whose JPEG is smaller than its Flate form gets converted to
`DeviceRGB` even at full size. A 2400 px policy over gradient masks therefore
often escapes unharmed, which is why this survived until a 1240 px pass.
