# Components

Building blocks live in `skald-core`, `skald-layout`, `skald-barcode`,
`skald-sign`, `skald-image`, `skald-optimize`, and `skald-optimize-jpegli`.

This directory is a **component library** on top of those blocks: finished
pages other people can depend on, and living examples of how to use Skald.
It is a folder of independently published artifacts, not one jar.

```text
skald-components/
  invoice-no/        → no.beint.skaldpdf:skald-invoice-no
  packing-slip-no/   → skald-packing-slip-no
  reminder-no/       → skald-reminder-no
  statement-no/      → skald-statement-no
  receipt-no/        → skald-receipt-no
  purchase-order-no/ → skald-purchase-order-no
  label-sticker/     → skald-label-sticker
  label-shipping/    → skald-label-shipping
```

Take one artifact. There is no umbrella that pulls every component.

## What belongs here

A component is allowed when all of these are true:

1. It composes engine APIs; it does not add PDF syntax.
2. The page size and field set are stable enough to publish (physical
   stock, or a *named, opinionated* document style).
3. Someone can use it without forking on day one.
4. It is not empty. No placeholder modules.

## Document themes

Country-specific commercial pages are separate artifacts, not one
`Invoice` class. `skald-invoice-no` is the ReAI-style Norwegian theme
(faktura, kreditnota, betalt kopi, tilbud, ordrebekreftelse, proforma).
Packing slips, reminders, statements, receipts, and purchase orders in
the same family reuse that letterhead.

They are a starting point — high quality, documented as a template you
may copy — not a promise that Skald owns Norwegian bookkeeping layout.

Do not publish `skald-invoice` (generic). Do not put every country in
one jar.

## Print stock

A 93×35 mm clothing EAN sticker is the same across brands. A 100×150 mm
shipping label is its own artifact. Future stock (GS1, shelf tags) gets
another `skald-label-*` module. Do not grow the clothing sticker with
shipping fields.

## Adding a component

1. Create `skald-components/<kind>-<name>/` with its own `module-info`
   and tests that decode or fingerprint the output.
2. `include` it in `settings.gradle.kts` and map `projectDir`.
3. Keep the Maven name `skald-<kind>-<name>` so a clothing printer never
   downloads a shipping label or an invoice.
4. Document themes are not labels. Print stock is not an invoice.
