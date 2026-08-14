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

`skald-sign` uses the JCA (`SHA256withRSA` / `SHA256withECDSA`) and a small
DER writer. Private keys stay in the caller’s `SigningKey`. The library does
not contact a timestamp authority, a CA, or a QTSP. A valid CMS seal means
the ByteRange is intact and the signature verifies with the *embedded*
certificate; it is not a claim that the certificate is qualified or trusted
by a national list. A second seal is appended incrementally. Rewriting a
sealed file fails closed.

Payslips and similar confidential generated files can use PDF 2.0 revision 6
AES-256 (`PdfEncryption`, Standard Security Handler). Encrypted output cannot
be parsed, stamped, or signed by Skald. Passwords are never logged. There is
no RC4 and no PDF 1.x revision.

Consumers should still apply their own upload-size, media-type, timeout, and
storage policies. A PDF parser is not a malware scanner.
