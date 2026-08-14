# Module layers

Skald is split so an application pays only for the capability it uses.
Engine modules are the building blocks. Published **components** live under
[`skald-components/`](../skald-components/README.md) as separate artifacts.

```text
Components           skald-components/          (folder, not one jar)
                     ├── label-sticker          skald-label-sticker
                     └── labels                 skald-labels (label-* umbrella)

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

## Document themes (invoices, later)

A generic `skald-invoice` is still the wrong shape. A *named* theme such
as `skald-invoice-no` can be a component once a second product wants the
same chrome. Until then ReAI and the example gallery are the invoices.

Document themes must not sit on the `skald-labels` umbrella. Print stock
and bookkeeping layout are different products.

See [skald-components/README.md](../skald-components/README.md).

## Rule for new published code

1. **Engine** — PDF, font, image, or symbology rule → existing engine module.
2. **Print stock** — physical page, stable fields → `skald-components/label-<name>`,
   published as `skald-label-<name>`. Add it to `skald-labels`.
3. **Document theme** — country- or style-specific invoice/slip → new
   `skald-components/invoice-<locale>` only after a second real consumer.
   Mark it as an opinionated template in the Javadoc.
4. **Application-only** — one company’s letterhead. Stays in that app.

Do not publish empty placeholders. Do not grow `skald-label-sticker` with
shipping or shelf code.
