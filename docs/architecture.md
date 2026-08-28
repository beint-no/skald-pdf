# Architecture

Skald is split at capability boundaries rather than by individual PDF object
types. This keeps dependencies small without turning ordinary document creation
into a graph of tiny artifacts.

```text
org.skaldpdf.packing.no ──┐
org.skaldpdf.reminder.no ─┼──> org.skaldpdf.invoice.no ──> org.skaldpdf.layout ──┐
org.skaldpdf.statement.no ┘         │                        org.skaldpdf.barcode ┼──> org.skaldpdf.core
org.skaldpdf.labels                 ┘                             │               │
org.skaldpdf.labels.shipping                         org.skaldpdf.fonts          │
org.skaldpdf.sign / codec / optimize ────────────────────────────────────────────┘
```

## Core

`skald-core` owns PDF syntax, COS values, pages, resource registration, the
custom-font engine, prepared raster data, metadata, reading, merging, and stamping. It emits only
PDF 2.0. Its parser accepts the older object and cross-reference structures still
encountered in received documents, with explicit resource limits.

Core requires only `java.base`. Standard JDK image decoding and transformation
live in `skald-image`, so text-only generation and composition do not bring
`java.desktop` into a linked runtime. There are no third-party production dependencies.

## Fonts

`skald-fonts` owns the bundled Skald Sans resources. Each face is loaded on its
first use. Layout and human-readable barcodes consume it internally, while a
core-only application can provide its own OpenType program without shipping the
bundled family.

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
symbols as `ImageSource` values. It depends on core and uses the standard font
module for human-readable barcode text. Place a symbol on any
page without taking label templates.

## Labels and document themes

`skald-label-sticker` is the 93 mm × 35 mm clothing EAN label.
`skald-label-shipping` is the 100 mm × 150 mm tracking label.
`skald-invoice-no` is the Norwegian commercial theme; packing slip,
reminder, statement, receipt, and purchase order reuse its letterhead.
The layering rule is in [modules.md](modules.md).

## Sign

`skald-sign` is optional. Core emits an unpacked `/Type /Sig` placeholder;
this module writes detached CMS SignedData, patches `/ByteRange` and
`/Contents`, and verifies the seal. It depends only on core and
`java.base` security APIs. Layout and barcode never see a private key.

The module is an AdES integrity seal. It is not a QTSP and does not mint
qualified certificates. See [signing.md](signing.md).

## Optimize

`skald-optimize` traverses nested image and Form XObjects, then asks an
`ImageRecompressor` for approved replacements. The canonical core writer copies
the complete reachable COS graph, preserves unknown dictionaries and streams,
packs small objects, reparses its output, and compares an object-number-
independent SHA-256 semantic digest before returning it. JPEG XL is never
written into the file.

The default recompressor uses JDK ImageIO. `skald-optimize-jpegli` is a separate
adapter around Glimt so PDFBox and native codec concerns never enter core.
Signed, encrypted, linearized, incrementally revised, and declared conformance
profiles are returned untouched.

Workflow-to-module mapping against iText's suite is in [workflows.md](workflows.md).

## Dependency policy

Every ordinary published artifact has a build-time check that fails if a third-party
module enters its production runtime classpath. Test dependencies are kept out of
published POMs and are intentionally independent implementations: PDFBox renders
and extracts, ZXing decodes barcodes, and external validators check serialized
PDF structure.

The explicit exception is `skald-optimize-jpegli`, which permits only
`no.beint.glimt` runtime modules. New modules are justified only when they make a substantial capability optional,
such as accessibility, archival profiles, cryptography, or a complex shaping
engine. Internal packages remain unexported by JPMS.
