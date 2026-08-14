# Module layers

Skald is split so an application pays only for the capability it uses. There
are two published layers, and a third that is deliberately *not* a Maven
artifact.

```text
Print stock          skald-labels          (optional finished labels)
                     │
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

`skald-barcode` draws **symbols**. It does not know SKU, composition, or
label stock. A warehouse app that only needs a QR on an invoice takes
barcode (or just puts `new QrCode(...)` on a layout `Image`) and never
sees a clothing sticker.

## Print-stock modules

A finished label is a *composition* of symbols + type + a physical page
size. That is not a barcode primitive.

`skald-labels` is the optional batteries-included place for print stock
that is physically standardized:

- 93 mm × 35 mm clothing EAN sticker (`ProductSticker`)
- A4 n-up sheets of the same label
- later: shelf tags, GS1 shipping labels, if they share that nature

It depends on core and barcode, not on layout. Fixed-position print stock
does not need the flow engine.

Take `skald-labels` when you want the finished sticker. Take only
`skald-barcode` when you want to place an EAN on your own page.

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
2. **Print stock** — is the page size physical (label/thermal/ticket) and
   the field set stable across companies? `skald-labels`, or a *second*
   print-stock module only if it would force unrelated apps to take a
   large unused surface.
3. **Business document** — example or application code. Not a Skald
   artifact.

New engine modules stay justified the way sign/image/optimize were:
they make a substantial capability optional. They are not a place to
park every document we have generated in a test.
