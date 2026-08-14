# Module layers

Skald is split so an application pays only for the capability it uses. There
are two published layers, and a third that is deliberately *not* a Maven
artifact.

```text
Print stock          skald-labels              (optional umbrella)
                     └── skald-label-sticker   (93×35 clothing EAN)
Engine               layout  barcode  sign  image  optimize
                     └────────────┬────────────────────────┘
                                  core
```

## Engine modules

These are the building blocks. They have stable, spec-shaped jobs.

| Artifact | Job |
|---|---|
| `skald-core` | PDF 2.0 syntax, fonts, images, read/write, merge |
| `skald-layout` | Flowing documents: paragraphs, tables, pagination |
| `skald-barcode` | EAN-13, UPC-A, Code 128, GS1-128, QR as `ImageSource` |
| `skald-sign` | PAdES-B-B integrity seal |
| `skald-image` | Optional native photo ingest |
| `skald-optimize` | Recompress images already inside a received PDF |
| `skald-label-sticker` | One print-stock type: 93×35 mm clothing EAN sticker |

`skald-barcode` draws **symbols**. It does not know SKU, composition, or
label stock. A warehouse app that only needs a QR on an invoice takes
barcode (or just puts `new QrCode(...)` on a layout `Image`) and never
sees a clothing sticker.

## Print-stock modules

A finished label is a *composition* of symbols + type + a physical page
size. That is not a barcode primitive.

Each print-stock *type* is its own tiny artifact so a warehouse app that
only prints clothing stickers does not take a future GS1 shipping label.

| Artifact | Type |
|---|---|
| `skald-label-sticker` | 93 mm × 35 mm clothing EAN (`ProductSticker`, A4 n-up) |
| `skald-labels` | Umbrella that `api`-depends on every current `skald-label-*` |

A new physical label (GS1 shipping, shelf tag) becomes `skald-label-<name>`,
not a class dumped into the sticker jar. We do not publish empty
placeholder modules.

These depend on core and barcode, not on layout. Take
`skald-label-sticker` for one type, or `skald-labels` if you want
whatever print stock exists at that version.

## What does not become a module

Invoices, packing slips, credit notes, reminders, and payslips are
**application documents**. Their information architecture follows VAT
rules, brand, and locale. Publishing `skald-invoice` would either freeze
one company's look or become a worse layout API.

Those live as:

- copy-paste recipes in the example gallery and `TypicalBusinessDocuments`
- application code (ReAI, ecomtools)

Do not add `skald-components`, `skald-invoice`, or `skald-packing-slip`.
A grab-bag "components" module becomes a junk drawer. Per-document
modules pretend Skald owns business paperwork.

If ReAI wants shared invoice chrome, that belongs in a ReAI package, not
on Maven Central as Skald.

## Rule for new published code

Ask which layer it is:

1. **Engine** — does it implement a PDF, font, image, or symbology rule?
   Put it in the matching engine module.
2. **Print stock** — is the page size physical and the field set stable
   across companies? New `skald-label-<type>` artifact. Do not grow
   `skald-label-sticker`. The `skald-labels` umbrella may depend on the
   new type.
3. **Business document** — example or application code. Not a Skald
   artifact.

New engine modules stay justified the way sign/image/optimize were:
they make a substantial capability optional. They are not a place to
park every document we have generated in a test.
