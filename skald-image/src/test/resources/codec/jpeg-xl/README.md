# Lossless JPEG XL input fixtures

`rgb-raw.jxl` (raw codestream) and `rgb-boxed.jxl` (JPEG XL container) encode
identical 4 × 4 RGB8 pixels. At position `(x, y)`, the color is entry
`(x + y) % 4` of red, green, blue, yellow. All colors are full intensity with
zero in the other channels. These generated images are CC0-1.0:
<https://creativecommons.org/publicdomain/zero/1.0/>.

`JpegXlFixtureTest` verifies every decoded sample, rejects truncated inputs, then
runs both through `NativeImages.prepare`, PDF creation and independent PDFBox
rendering. The resulting PDF contains a conventional DCT/JPEG image: this tests
Skald's supported JPEG XL input conversion, not a PDF `JXLDecode` extension.
The render is compared with the decoded prepared JPEG, since that step is lossy.

Fixtures remove the encoder dependency from tests. A libjxl decoder is required;
tests skip when absent locally, while CI explicitly requires it with
`SKALD_REQUIRE_JXL_TESTS=1` and installs `libjxl-dev`.

## Regeneration

Generated with libjxl `cjxl` 0.12.0. From this directory, using Python 3 and `cjxl`:

```sh
python3 - <<'PY'
from pathlib import Path
import subprocess
import tempfile

colors = [(255, 0, 0), (0, 255, 0), (0, 0, 255), (255, 255, 0)]
pixels = bytes(c for y in range(4) for x in range(4) for c in colors[(x + y) % 4])
with tempfile.TemporaryDirectory() as directory:
    source = Path(directory) / 'rgb.ppm'
    source.write_bytes(b'P6\n4 4\n255\n' + pixels)
    for container, name in [(0, 'raw'), (1, 'boxed')]:
        subprocess.run(['cjxl', str(source), f'rgb-{name}.jxl', '--distance=0',
                        '--effort=4', '--num_threads=1', f'--container={container}'],
                       check=True)
PY
```

From the repository root, with libjxl installed:

```sh
SKALD_REQUIRE_JXL_TESTS=1 ./gradlew :skald-image:test --tests '*JpegXlFixtureTest'
```
