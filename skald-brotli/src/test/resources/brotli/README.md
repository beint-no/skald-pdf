# Independent Brotli reference fixtures

These synthetic fixtures and `generate.py` are CC0-1.0:
https://creativecommons.org/publicdomain/zero/1.0/ . No private data is included.
Generated with the reference Brotli CLI 1.2.1, quality 6, using window 22 or
large-window 30. Regenerate with Python 3 and the CLI:

```sh
python3 generate.py
```

`standard.br` and `large-window.br` decode to ASCII
`Skald PDF Brotli stream regression.\n` repeated 10,000 times. Tests compare
all bytes, check exact output-limit boundaries, and reject truncated/trailing
input. They do not require a CLI encoder during normal vector decoding.

`structure-*.pdf` exercise Brotli in an object stream containing the catalog,
page-content stream, TIFF-predicted RGB image and PNG-predicted xref stream.
The xref has `[ASCIIHexDecode, BrotliDecode]` and matching
`[null, predictor-dictionary]` parameters. All three object-stream members are
referenced via type-2 xref entries. The image is 3 × 2 RGB8; its pixel rows are
red/green/blue, then yellow/cyan/magenta. These are decoder fixtures; the writer
intentionally uses a Flate xref and an unpacked catalog for extension discovery.
