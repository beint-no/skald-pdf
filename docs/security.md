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
- 100 million decoded pixels per input image.

Object cycles, invalid references, unsupported filters required for structure,
malformed page trees, invalid font tables, missing glyphs, non-finite PDF numbers,
and encrypted documents fail closed with an exception.

Consumers should still apply their own upload-size, media-type, timeout, and
storage policies. A PDF parser is not a malware scanner.
