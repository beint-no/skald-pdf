#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Independent test-only normalization of Skald experimental JXL PDFs via djxl.

Reads Skald's Flate bootstrap xref and replaces /JXLDecode streams with RGB8 or
Gray8 Flate streams in an incremental revision. This is not a general PDF parser.
"""
import re
import subprocess
import sys
import zlib
import tempfile
from pathlib import Path

source, target = map(Path, sys.argv[1:])
pdf = source.read_bytes()
start = int(re.search(rb"startxref\s+(\d+)\s+%%EOF\s*$", pdf)[1])
header, _, rest = pdf[start:].partition(b"\nstream\n")
assert b"/Type /XRef" in header and b"/Filter /FlateDecode" in header
assert b"/W [1 8 4]" in header and b"/Encrypt" not in header
size = int(re.search(rb"/Size (\d+)", header)[1])
root = int(re.search(rb"/Root (\d+) 0 R", header)[1])
length = int(re.search(rb"/Length (\d+)", header)[1])
entries = zlib.decompress(rest[:length])
assert len(entries) == size * 13
output, changed = bytearray(pdf), []
for number in range(1, size):
    entry = entries[number * 13:(number + 1) * 13]
    if entry[0] != 1 or number == size - 1:
        continue
    offset = int.from_bytes(entry[1:9], "big")
    body = pdf[offset:]
    dictionary, separator, payload = body.partition(b"\nstream\n")
    # Only inspect the first indirect object's dictionary; binary bodies can contain tokens.
    if not separator or b"endobj" in dictionary or (b"/Filter /JXLDecode" not in dictionary and b"/Filter /BrotliDecode" not in dictionary):
        continue
    assert dictionary.startswith(f"{number} 0 obj\n".encode())
    length = int(re.search(rb"/Length (\d+)", dictionary)[1])
    assert payload[length:].startswith(b"\nendstream")
    if b"/Filter /BrotliDecode" in dictionary:
        decoded = subprocess.check_output(["brotli", "--decompress", "--stdout"], input=payload[:length])
    else:
        with tempfile.TemporaryDirectory(prefix="skald-jxl-reference-") as directory:
            image = Path(directory) / "image.jxl"
            pixels = Path(directory) / "image.pnm"
            image.write_bytes(payload[:length])
            subprocess.run(["djxl", str(image), str(pixels), "--bits_per_sample=8"], check=True,
                           stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
            pnm = pixels.read_bytes()
        # Binary PGM/PPM, with whitespace/comments permitted in the header.
        tokens, position = [], 0
        while len(tokens) < 4:
            while pnm[position:position+1].isspace(): position += 1
            if pnm[position:position+1] == b"#":
                position = pnm.index(b"\n", position) + 1
                continue
            end = position
            while not pnm[end:end+1].isspace(): end += 1
            tokens.append(pnm[position:end]); position = end
        assert pnm[position:position+1].isspace()
        position += 1
        magic, width, height, maximum = tokens
        assert magic in (b"P5", b"P6") and maximum == b"255"
        components = 1 if magic == b"P5" else 3
        assert (b"/ColorSpace /DeviceGray" if components == 1 else b"/ColorSpace /DeviceRGB") in dictionary
        assert int(re.search(rb"/Width (\d+)", dictionary)[1]) == int(width)
        assert int(re.search(rb"/Height (\d+)", dictionary)[1]) == int(height)
        decoded = pnm[position:]
        assert len(decoded) == int(width) * int(height) * components
    encoded = zlib.compress(decoded)
    dictionary = dictionary.replace(b"/Filter /JXLDecode", b"/Filter /FlateDecode").replace(b"/Filter /BrotliDecode", b"/Filter /FlateDecode")
    dictionary = re.sub(rb"/Length \d+", f"/Length {len(encoded)}".encode(), dictionary)
    changed.append((number, len(output)))
    output += dictionary + b"\nstream\n" + encoded + b"\nendstream\nendobj\n"
assert changed, "No JPEG XL streams found; verification did not exercise the codec"
xref = len(output)
output += b"xref\n"
for number, offset in changed:
    output += f"{number} 1\n{offset:010d} 00000 n \n".encode()
output += f"trailer\n<< /Size {size} /Root {root} 0 R /Prev {start} >>\nstartxref\n{xref}\n%%EOF\n".encode()
target.write_bytes(output)
