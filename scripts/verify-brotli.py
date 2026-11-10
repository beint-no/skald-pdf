#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Independently normalize Skald-generated, unencrypted Brotli PDFs for PDFBox.

This is a test tool for Skald's writer format, not a general PDF parser. It reads
Skald's Flate bootstrap xref, transcodes raw Brotli streams with the reference CLI,
and appends an incremental xref. Original object numbers, predictor parameters
and compressed-object references remain intact. No Skald decoder is used.
"""
import re
import subprocess
import sys
import zlib
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
    if not separator or b"endobj" in dictionary or b"/Filter /BrotliDecode" not in dictionary:
        continue
    assert dictionary.startswith(f"{number} 0 obj\n".encode())
    length = int(re.search(rb"/Length (\d+)", dictionary)[1])
    assert payload[length:].startswith(b"\nendstream")
    decoded = subprocess.check_output(["brotli", "--decompress", "--stdout"], input=payload[:length])
    encoded = zlib.compress(decoded)
    dictionary = dictionary.replace(b"/Filter /BrotliDecode", b"/Filter /FlateDecode")
    dictionary = re.sub(rb"/Length \d+", f"/Length {len(encoded)}".encode(), dictionary)
    changed.append((number, len(output)))
    output += dictionary + b"\nstream\n" + encoded + b"\nendstream\nendobj\n"
assert changed, "No Brotli streams found; verification did not exercise the codec"
xref = len(output)
output += b"xref\n"
for number, offset in changed:
    output += f"{number} 1\n{offset:010d} 00000 n \n".encode()
output += f"trailer\n<< /Size {size} /Root {root} 0 R /Prev {start} >>\nstartxref\n{xref}\n%%EOF\n".encode()
target.write_bytes(output)
