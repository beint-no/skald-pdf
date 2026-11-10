# SPDX-License-Identifier: CC0-1.0
# /// script
# requires-python = ">=3.10"
# dependencies = []
# ///
"""Losslessly transcode the logo of a ReAI-generated fictional invoice using qpdf JSON."""
import base64
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import zlib

root = Path(__file__).parent
source = json.loads(subprocess.check_output([
    "qpdf", "--json-output", "--json-stream-data=inline", str(root / "reai-invoice-flate.pdf")
]))
objects = source["qpdf"][1]
images = [(key, value["stream"]) for key, value in objects.items()
          if value.get("stream", {}).get("dict", {}).get("/Subtype") == "/Image"]
assert len(images) == 1
key, image = images[0]
params = image["dict"]["/DecodeParms"]
assert params == {"/BitsPerComponent": 8, "/Colors": 3, "/Columns": 320, "/Predictor": 15}
encoded = zlib.decompress(base64.b64decode(image["data"]))
width, height, colors = 320, 100, 3
stride = width * colors
previous = bytes(stride)
pixels = bytearray()
filters = set()
for offset in range(0, len(encoded), stride + 1):
    kind = encoded[offset]
    filters.add(kind)
    row = bytearray(encoded[offset + 1:offset + 1 + stride])
    assert len(row) == stride
    for x in range(stride):
        a = row[x - colors] if x >= colors else 0
        b = previous[x]
        c = previous[x - colors] if x >= colors else 0
        if kind == 0:
            predictor = 0
        elif kind == 1:
            predictor = a
        elif kind == 2:
            predictor = b
        elif kind == 3:
            predictor = (a + b) // 2
        else:
            assert kind == 4
            p = a + b - c
            predictor = min((a, b, c), key=lambda v: abs(p - v))
        row[x] = (row[x] + predictor) % 256
    pixels.extend(row)
    previous = row
assert len(pixels) == width * height * colors
assert hashlib.sha256(pixels).hexdigest() == (
    "c7dd2e8943b3727e303c3f54907db88033ec9b7120eec48c399571401a2e4b72"
)
tiff = bytearray(pixels)
for offset in range(0, len(pixels), stride):
    for x in range(stride - 1, colors - 1, -1):
        tiff[offset + x] = (pixels[offset + x] - pixels[offset + x - colors]) % 256
for predictor, payload in [(15, encoded), (2, tiff)]:
    candidate = copy.deepcopy(source)
    stream = candidate["qpdf"][1][key]["stream"]
    stream["dict"]["/Filter"] = "/BrotliDecode"
    stream["dict"]["/DecodeParms"]["/Predictor"] = predictor
    stream["data"] = base64.b64encode(subprocess.check_output(
        ["brotli", "--quality=11", "--lgwin=22", "--stdout"], input=payload
    )).decode("ascii")
    for value in candidate["qpdf"][1].values():
        catalog = value.get("value", {})
        if catalog.get("/Type") == "/Catalog":
            catalog.setdefault("/Extensions", {})["/PDFa"] = {
                "/Type": "/DeveloperExtensions", "/BaseVersion": "/2.0", "/ExtensionLevel": 1,
                "/ExtensionRevision": "u:2026", "/URL": "u:https://pdfa.org/resource/extension-brotli/",
            }
    target = root / f"reai-invoice-brotli-predictor-{predictor}.pdf"
    temporary = target.with_suffix(".json")
    try:
        temporary.write_text(json.dumps(candidate))
        subprocess.run(["qpdf", "--json-input", str(temporary), str(target)], check=True)
    finally:
        temporary.unlink(missing_ok=True)
print(json.dumps({"image_object": key, "width": width, "height": height,
                  "colors": colors, "png_filters_used": sorted(filters), "image_sample_bytes": len(pixels)}))
