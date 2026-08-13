# Standards policy

## Output

Skald emits PDF 2.0 as defined by ISO 32000-2:2020 and applies the published
industry errata. The writer uses a `%PDF-2.0` header and a catalog `/Version /2.0`
declaration. It emits indirect objects, compressed content and resource streams,
object streams, a compressed cross-reference stream, a trailer file identifier,
and XMP metadata.

There is intentionally no option to emit PDF 1.x. A single output version removes
branches from the writer and prevents older serialization choices from becoming
part of the public API.

## Input

Composition accepts unencrypted PDF 1.x and PDF 2.0 inputs using classic xref
tables, xref streams, object streams, hybrid references, and revision chains. The
parser preserves imported page content and resources as inert PDF objects. Any
newly saved file is PDF 2.0.

Supporting older inputs is not the same as maintaining a legacy output mode. It
is required for practical stamping and merging because received documents are not
under the application's control.

## Verification

Generated fixtures are checked at several independent layers:

1. Unit and layout tests cover text, Unicode font subsetting, image alpha and
   compression, barcodes, pagination, merging, stamping, rotation, parser limits,
   virtual-thread isolation, and compact large reports.
2. Apache PDFBox is test-only and checks independent parsing, rendering, text
   extraction, font embedding, and page counts.
3. ZXing is test-only and decodes the rendered EAN-13, UPC-A, Code 128, GS1-128, and QR symbols.
4. qpdf checks syntax and stream encodings.
5. veraPDF's Arlington PDF 2.0 profile checks the machine-readable object model
   derived from ISO 32000-2 and its resolved errata.
6. Poppler, MuPDF, and Ghostscript provide additional rendering smoke tests during
   release acceptance.
7. `skald-sign` verifies its own CMS and, in tests, PDFBox reads the signature
   dictionary. That is an integrity check, not a qualified-status check.

PAdES-B-B attributes are documented in [signing.md](signing.md). QES is out of
scope without a listed QTSP.

The PDF specification itself remains authoritative. A validator is a second
implementation and not a substitute for standards review.

## References

- [ISO 32000-2:2020, Portable document format — Part 2: PDF 2.0](https://pdfa.org/resource/iso-32000-2/)
- [PDF Association ISO 32000-2 errata collections](https://pdf-issues.pdfa.org/32000-2-2020/)
- [Arlington PDF Model and its PDF 2.0 validation profile](https://pdfa.org/resource/arlington-pdf-model/)
- ISO/IEC 15420 for EAN/UPC symbol encoding
