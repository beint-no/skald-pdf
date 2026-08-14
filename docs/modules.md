# Module layers

Skald is split so an application pays only for the capability it uses.
Engine modules are the building blocks. Published **components** live under
[`skald-components/`](../skald-components/README.md) as separate artifacts.

```text
Components           skald-components/          (folder, not one jar)
                     ├── invoice-no             skald-invoice-no
                     ├── packing-slip-no        skald-packing-slip-no
                     ├── reminder-no            skald-reminder-no
                     ├── statement-no           skald-statement-no
                     ├── receipt-no             skald-receipt-no
                     ├── purchase-order-no      skald-purchase-order-no
                     ├── label-sticker          skald-label-sticker
                     └── label-shipping         skald-label-shipping

Engine               layout  barcode  sign  image  optimize
                       │       │      └───┬─────┘
                       └─ fonts           │
                            └─────────────┴── core
```

## Engine modules

These are the building blocks. They have stable, spec-shaped jobs.

| Artifact | Job |
|---|---|
| `skald-core` | PDF 2.0 syntax, custom-font engine, prepared images, read/write, merge; `java.base` only |
| `skald-fonts` | Lazily loaded Skald Sans regular, bold, italic, and bold-italic faces |
| `skald-layout` | Flowing documents: paragraphs, tables, pagination |
| `skald-barcode` | EAN-13, UPC-A, Code 128, GS1-128, QR as `ImageSource` |
| `skald-sign` | PAdES-B-B integrity seal |
| `skald-image` | Optional JDK raster processing and native photo ingest; requires `java.desktop` |
| `skald-optimize` | Recompress images already inside a received PDF using `skald-image` |

Layout and human-readable barcodes depend on `skald-fonts`; low-level core,
signing, and composition do not include the bundled TTF resources.

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
| `skald-label-shipping` | 100 mm × 150 mm address + tracking label |

A new physical label (GS1, shelf tag) becomes `skald-label-<name>`,
not a class dumped into the sticker jar. We do not publish empty
placeholder modules. There is no labels umbrella: take the one stock
you print.

## Document themes

A generic `skald-invoice` is the wrong shape. Named themes live here:

| Artifact | Pages |
|---|---|
| `skald-invoice-no` | Faktura, kreditnota, betalt kopi, tilbud, ordrebekreftelse, proforma |
| `skald-packing-slip-no` | Pakkseddel, følgeseddel |
| `skald-reminder-no` | Purring, betalingsoppfordring |
| `skald-statement-no` | Kontooversikt |
| `skald-receipt-no` | A5 kvittering |
| `skald-purchase-order-no` | Innkjøpsordre |

`skald-invoice-no` also exports the shared Norwegian letterhead
(`Company`, `Party`, `Bank`, `NorwegianTheme`) that the other `*-no`
documents reuse. It is an opinionated ReAI-style template, not a claim
that Skald owns every Norwegian invoice.

See [skald-components/README.md](../skald-components/README.md).

## Rule for new published code

1. **Engine** — PDF, font, image, or symbology rule → existing engine module.
2. **Print stock** — physical page, stable fields → `skald-components/label-<name>`,
   published as `skald-label-<name>`.
3. **Document theme** — country- or style-specific invoice/slip → new
   `skald-components/<kind>-<locale>`. Mark it as an opinionated template.
4. **Application-only** — one company’s letterhead. Stays in that app.

Do not publish empty placeholders. Do not grow `skald-label-sticker` with
shipping or shelf code.
