# Application invoice codec fixtures

These fixtures exercise importing and rewriting a complete application-produced
PDF with a predicted RGB image, embedded fonts and extractable text.
`InvoiceCodecFixtureTest` uses them on every core test run.

- [Flate control](reai-invoice-flate.pdf): the original application output; PNG
  predictor 15, including Sub, Up and Paeth rows.
- [Brotli / PNG predictor 15](reai-invoice-brotli-predictor-15.pdf): the same
  predicted logo bytes compressed with Brotli.
- [Brotli / TIFF predictor 2](reai-invoice-brotli-predictor-2.pdf): horizontal
  differencing applied to the same RGB samples before Brotli compression.

All three logos are 320 × 100, DeviceRGB, 8 bits per component. The 96,000 decoded
RGB bytes have SHA-256
`c7dd2e8943b3727e303c3f54907db88033ec9b7120eec48c399571401a2e4b72`.
The test independently checks the Flate samples with PDFBox. For Brotli it checks
safe refusal to decode/recompress, unchanged encoded bytes and predictor
parameters through a Skald rewrite, and unchanged invoice text. **Core alone
keeps Brotli opaque; the optional `skald-brotli` module adds decoding and opt-in
encoding.** The Brotli variants declare the PDFa Brotli
extension; the control needs no such extension.

## Provenance and reuse

The control was generated offline using ReAI's actual
`no.reai.i.app.InvoicePdfGenerator` with Skald 1.16.5. All company, customer,
address, order, date, amount and logo inputs were fictional in-memory values.
No database or private document was accessed. The visible invoice states that
it is fictional and not payable. The private application is not needed to use
these committed fixtures.

ReAI did not emit the Brotli variants. `prepare_fixtures.py` independently
transcodes the control's logo through qpdf's JSON interface and the reference
Brotli CLI. They are application-rendered test PDFs transformed for feature
validation, not observed customer Brotli exports. MuPDF 1.28.6 rendered all
three pages identically at 96 DPI when the fixtures were prepared.

The generated document content and fictional logo are CC0-1.0:
<https://creativecommons.org/publicdomain/zero/1.0/>. Embedded IBM Plex font
subsets retain the [SIL Open Font License](IBM-Plex-OFL-1.1.txt). The reproduction
script is also offered under CC0-1.0 so other PDF projects may copy it and the
fixtures into their own tests without depending on Skald.

## Regeneration and testing

From this directory, with Python 3.10+, qpdf and the reference `brotli` CLI:

```sh
python3 prepare_fixtures.py
```

The script verifies the original samples' hash, then regenerates both Brotli
variants with quality 11 and window 22. It does not regenerate the application
layout or require private ReAI code. From the repository root:

```sh
./gradlew :skald-core:test --tests '*InvoiceCodecFixtureTest'
```

These files can also validate predictor decoding in other libraries: both Brotli
variants must decode to the same RGB bytes as the Flate control. The core-only
tests check opaque preservation; `skald-brotli` additionally checks exact decoded
samples and rewriting with both output codecs.
