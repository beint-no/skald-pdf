# Security model

PDF is a container with recursive structures and compressed data. Skald treats
parsed page content, actions, annotations, and imported resources as inert data;
it does not execute scripts or resolve external resources.

Current parser limits include:

- 256 MiB source files;
- 1,000,000 indirect objects;
- 100,000 pages;
- 128 levels of structural nesting;
- 128 MiB for any decoded structural stream;
- 1 MiB backward search for `startxref`;
- 100 million decoded pixels per input image;
- 32 MiB encoded image payload;
- JPEG, PNG, GIF, and BMP signatures only — dimensions are read from the
  container header before `ImageIO` allocates a raster.

Object cycles, invalid references, unsupported filters required for structure,
malformed page trees, invalid font tables, non-finite PDF numbers, and encrypted
documents fail closed with an exception. Characters the embedded face does not
contain use glyph 0 (`.notdef`) instead of aborting a business document.

Imported pages are treated as inert data. Page `/AA`, `/JS`, and `/PresSteps`
are dropped. Annotation `/A` entries are kept only when they are `/URI` or
`/GoTo`; Launch, JavaScript, SubmitForm, and remote-goto actions are removed.

Consumers should still apply their own upload-size, media-type, timeout, and
storage policies. A PDF parser is not a malware scanner.
