# Architecture

Skald is split at capability boundaries rather than by individual PDF object
types. This keeps dependencies small without turning ordinary document creation
into a graph of tiny artifacts.

```text
org.skaldpdf.layout   ──┐
org.skaldpdf.barcode  ──┼──> org.skaldpdf.core ──> java.desktop ──> java.base
org.skaldpdf.sign     ──┘
```

## Core

`skald-core` owns PDF syntax, COS values, pages, resource registration, embedded
fonts, raster images, metadata, reading, merging, and stamping. It emits only
PDF 2.0. Its parser accepts the older object and cross-reference structures still
encountered in received documents, with explicit resource limits.

The core uses `java.desktop` only for standard JDK image decoding. There are no
third-party production dependencies.

## Layout

`skald-layout` owns semantic elements and pagination. It consumes top-level
elements incrementally and translates them into core page operations. Layout
requires core transitively because its public document and image APIs expose core
types.

Layout depends on the `ImageSource` interface, not on optional image producers.
This lets barcode and future vector adapters supply drawable content without a
reverse dependency or runtime discovery mechanism.

## Barcode

`skald-barcode` provides immutable, validated EAN-13, UPC-A, Code 128, GS1-128, and QR
symbols, plus the 93 mm × 35 mm clothing sticker used in warehouse printing. It
depends only on core and can be used without the layout module by drawing its
`ImageSource` onto a page directly.

## Sign

`skald-sign` is optional. Core emits an unpacked `/Type /Sig` placeholder;
this module writes detached CMS SignedData, patches `/ByteRange` and
`/Contents`, and verifies the seal. It depends only on core and
`java.base` security APIs. Layout and barcode never see a private key.

The module is an AdES integrity seal. It is not a QTSP and does not mint
qualified certificates. See [signing.md](signing.md).

Workflow-to-module mapping against iText's suite is in [workflows.md](workflows.md).
The next optional module, if any, is `skald-optimize` for recompressing images
already stored in a received PDF.

## Dependency policy

Every published artifact has a build-time check that fails if a third-party
module enters its production runtime classpath. Test dependencies are kept out of
published POMs and are intentionally independent implementations: PDFBox renders
and extracts, ZXing decodes barcodes, and external validators check serialized
PDF structure.

New modules are justified only when they make a substantial capability optional,
such as accessibility, archival profiles, cryptography, or a complex shaping
engine. Internal packages remain unexported by JPMS.
