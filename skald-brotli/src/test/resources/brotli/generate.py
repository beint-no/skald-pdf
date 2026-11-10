# SPDX-License-Identifier: CC0-1.0
"""Regenerate independent vectors with Python 3 and the reference brotli CLI."""
from pathlib import Path
import subprocess

root = Path(__file__).parent
rgb = bytes([255, 0, 0, 0, 255, 0, 0, 0, 255, 255, 255, 0, 0, 255, 255, 255, 0, 255])


def stream(dictionary, payload):
    return (f"<< {dictionary} /Length {len(payload)} >>\nstream\n".encode()
            + payload + b"\nendstream")


for name, window in [("standard", "--lgwin=22"), ("large-window", "--large_window=30")]:
    def compress(data):
        return subprocess.check_output(["brotli", "--stdout", "--quality=6", window], input=data)

    (root / f"{name}.br").write_bytes(compress(b"Skald PDF Brotli stream regression.\n" * 10000))
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 3 2] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>",
    ]
    header, bodies = b"", b""
    for number, body in enumerate(objects, 1):
        header += f"{number} {len(bodies)} ".encode()
        bodies += body + b"\n"
    predicted = bytearray(rgb)
    for row in range(2):
        for column in range(8, 2, -1):
            predicted[row * 9 + column] = (rgb[row * 9 + column] - rgb[row * 9 + column - 3]) % 256
    image = stream("/Type /XObject /Subtype /Image /Width 3 /Height 2 /ColorSpace /DeviceRGB "
                   "/BitsPerComponent 8 /Filter /BrotliDecode /DecodeParms "
                   "<< /Predictor 2 /Colors 3 /Columns 3 /BitsPerComponent 8 >>", compress(predicted))
    content = stream("/Filter /BrotliDecode", compress(b"q 3 0 0 2 0 0 cm /Im0 Do Q\n"))
    packed = stream(f"/Type /ObjStm /N 3 /First {len(header)} /Filter /BrotliDecode", compress(header + bodies))
    output, offsets = bytearray(b"%PDF-2.0\n"), {}
    for number, body in [(4, image), (5, content), (6, packed)]:
        offsets[number] = len(output)
        output += f"{number} 0 obj\n".encode() + body + b"\nendobj\n"
    offsets[7] = len(output)
    entries = [(0, 0, 65535)] + [(2, 6, i) for i in range(3)] + [(1, offsets[n], 0) for n in range(4, 8)]
    previous, predicted_xref = bytes(7), bytearray()
    for kind, offset, generation in entries:
        row = bytes([kind]) + offset.to_bytes(4, "big") + generation.to_bytes(2, "big")
        predicted_xref += bytes([2]) + bytes((a - b) % 256 for a, b in zip(row, previous))
        previous = row
    # A filter array with the predictor attached to the second stage.
    xref = stream("/Type /XRef /Size 8 /Root 1 0 R /W [1 4 2] /Index [0 8] "
                  "/Filter [/ASCIIHexDecode /BrotliDecode] "
                  "/DecodeParms [null << /Predictor 15 /Columns 7 >>]",
                  compress(predicted_xref).hex().encode() + b">")
    output += b"7 0 obj\n" + xref + b"\nendobj\n"
    output += f"startxref\n{offsets[7]}\n%%EOF\n".encode()
    (root / f"structure-{name}.pdf").write_bytes(output)
