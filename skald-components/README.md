# Components

Building blocks live in `skald-core`, `skald-layout`, `skald-barcode`,
`skald-sign`, `skald-image`, and `skald-optimize`.

This directory is a **component library** on top of those blocks: finished
pages other people can depend on, and living examples of how to use Skald.
It is a folder of independently published artifacts, not one jar.

```text
skald-components/
  label-sticker/     → no.beint.skaldpdf:skald-label-sticker
  labels/            → no.beint.skaldpdf:skald-labels   (umbrella of every label-*)
  # later, when a real consumer exists:
  # label-gs1/       → skald-label-gs1
  # invoice-no/      → skald-invoice-no
```

Take one artifact. Do not take the umbrella unless you want every current
print-stock type.

## What belongs here

A component is allowed when all of these are true:

1. It composes engine APIs; it does not add PDF syntax.
2. The page size and field set are stable enough to publish (physical
   stock, or a *named, opinionated* document style).
3. Someone can use it without forking on day one.
4. It is not empty. No placeholder modules.

Labels qualify. A 93×35 mm clothing EAN sticker is the same across brands.

## Invoices

Country-specific invoices can live here **later**, as separate artifacts
(`skald-invoice-no`, `skald-invoice-se`, …), not as one `Invoice` class.

They are a different contract from labels:

- A label is print stock. Changing wrap or quiet zones is a bugfix.
- An invoice is a **theme**. Norwegian VAT lines, KID, and “Betalingsinformasjon”
  are shared; letterhead, column weights, and tone are not. ReAI’s Respiro
  invoice is not every Norwegian invoice.

So an `skald-invoice-no` would be an opinionated starting point — high
quality, documented as a template you may copy — not a promise that
Skald owns Norwegian bookkeeping layout. Promote one from ReAI only
when a *second* product wants the same chrome. Until then the gallery
and ReAI stay the examples.

Do not publish `skald-invoice` (generic). Do not put every country in
one jar.

## Adding a component

1. Create `skald-components/<kind>-<name>/` with its own `module-info`
   and tests that decode or fingerprint the output.
2. `include` it in `settings.gradle.kts` and map `projectDir`.
3. Keep the Maven name `skald-<kind>-<name>` so a clothing printer never
   downloads a shipping label.
4. If it is print stock, add it to the `skald-labels` umbrella.
5. If it is a document theme, leave it off that umbrella. Document
   themes are not labels.
