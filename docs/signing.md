# Signing, eIDAS, and what Skald actually does

This is the honest answer to the question customers ask after seeing a
signed PDF: *is this the same as Scrive, and is it qualified under eIDAS?*

**No. Neither ReAI nor Skald is a Qualified Trust Service Provider (QTSP).**
A document sealed with `skald-sign` is **not** a Qualified Electronic
Signature (QES). It does **not** replace BankID/Scrive QES, and it does
**not** by itself satisfy eIDAS qualified-signature requirements.

## What the optional `skald-sign` module is

`skald-sign` is a JDK-only CMS writer. It:

1. reserves a PDF 2.0 signature field in `skald-core` (`/Type /Sig`,
   `/ByteRange`, `/Contents`, `/AcroForm`, `/SigFlags 3`);
2. hashes the exact signed byte ranges with SHA-256;
3. wraps the digest in detached CMS SignedData (`adbe.pkcs7.detached` by
   default, or `ETSI.CAdES.detached`);
4. includes the PAdES-B-B signed attributes `content-type`,
   `message-digest`, and ESS `signing-certificate-v2`;
5. verifies the seal without BouncyCastle.

That is an **advanced electronic signature (AdES) integrity seal**: after
the file is issued, a flipped bit in the invoice body fails verification.
It proves *this exact PDF bytes were signed with this private key*. It
does not prove the signer was identified to QTSP standard, that a QSCD
was used, or that a qualified certificate was issued.

Bring the module in only when you need sealing:

```kotlin
implementation("no.beint.skaldpdf:skald-sign:1.5.0")
```

Core, layout, and barcode stay free of cryptography.

## eIDAS in one page

eIDAS (Regulation (EU) No 910/2014, as amended by 2024/1183) defines three
tiers. They are not interchangeable.

| Tier | What it is | Legal effect | Who can produce it |
|---|---|---|---|
| SES — simple electronic signature | Any electronic data attached to a document to sign (a typed name, a checkbox, a scanned autograph) | Admissible as evidence; easy to dispute | Anyone |
| AdES — advanced electronic signature | Uniquely linked to the signer, capable of identifying them, under their sole control, and detecting later changes | Stronger evidence; still not automatically equivalent to wet ink | Anyone with a proper AdES implementation and a suitable certificate |
| QES — qualified electronic signature | AdES **plus** a qualified certificate **plus** a qualified signature creation device, issued by a **QTSP** listed on the EU Trusted List | Automatically equivalent to a handwritten signature across the EU (Art. 25(2)) | Only a QTSP, using a QSCD |

A trust service is **qualified** only if it appears as qualified on the
[EU Trusted List](https://eidas.ec.europa.eu/efda/tl-browser/). Self-assertion
does not count. ReAI is not on that list. Skald is a library, not a trust
service at all.

PAdES (ETSI EN 319 142) is the PDF profile of CAdES. Baseline levels:

- **PAdES-B-B** — CMS + signed attributes (what Skald emits)
- **PAdES-B-T** — B-B plus a trusted timestamp
- **PAdES-B-LT / B-LTA** — revocation material and archive timestamps

Skald implements B-B. Timestamping (B-T) needs a TSA; long-term (LT/LTA)
needs revocation data. Those are later, optional work, not something to
claim today.

## US ESIGN, UETA, and “global contract law”

The US ESIGN Act and the Uniform Electronic Transactions Act do **not**
require a cryptographic PDF signature. A click-to-accept, an email
confirmation, or a logged audit trail can form a valid electronic
signature if the parties intended to sign and the record is retained.

A Skald/ReAI seal can be *part* of that evidence (integrity of the issued
PDF). It is not a special US statutory form. There is no single “global
contract law” that a PDF library can comply with. Local contract,
bookkeeping, and consumer rules still apply.

## How this compares to Scrive

Scrive is a **signing platform**. Their help centre states they are a
Trust Service Provider and a QTSP. In practice that means they can offer:

- identification (BankID, MitID, SMS, e-mail, eID)
- signing ceremonies and audit trails
- PAdES AdES and, where they use a QTSP-backed certificate / QSCD, QES
- optional KSI / timestamped evidence
- notification, reminders, and a viewer

Skald is a **PDF library**. `skald-sign` seals a file you already
generated. It does not identify a person, host a signing room, talk to
BankID, or appear on the EU Trusted List.

| Capability | Skald `skald-sign` | ReAI today | Scrive |
|---|---|---|---|
| Generate the invoice PDF | Yes | Yes (iText) | No |
| Byte-range integrity seal (PAdES-B-B) | Yes | Not yet | Yes |
| Viewer-visible signature dictionary | Yes | No | Yes |
| Qualified certificate / QSCD / QTSP | **No** | **No** | Yes, when that product path is used |
| BankID / eID identification | No | No | Yes |
| Signing ceremony + evidence package | No | No | Yes |
| Qualified timestamp | No | No | Available |
| Drop-in QES replacement | **No** | **No** | That is their product |

If a customer needs “this contract is a QES under eIDAS”, send them to a
QTSP (Scrive, Penneo, a bank eID broker, or another listed provider). If
they need “this issued invoice cannot be silently edited afterwards”,
Skald’s seal is the right, cheaper, in-process tool.

## What is a no-brainer in an accounting system

Relevant, and now implemented:

- Seal **issued** invoices, credit notes, reminders, and packing slips
  with the tenant’s (or ReAI’s) organizational key.
- Store the signed bytes as the archival original.
- Verify on download so a corrupted S3 object is obvious.
- Show reason / location (`Issued invoice`, `Oslo`) in the signature
  dictionary so Acrobat/Preview display a clear seal.
- Keep signing in its own artifact so POS label printing does not pull
  CMS code.

Not a no-brainer, and not claimed:

- Calling the seal a QES
- Replacing BankID
- Skipping a QTSP when the customer asked for qualified signing
- Embedding a timestamp from a random clock and calling it PAdES-B-T

## How to seal a document

```java
byte[] invoice = Pdf.create(document -> {
    document.setMargins(40);
    document.add(new Paragraph("Faktura 1001").bold());
});

var key = SigningKey.fromPkcs12(Path.of("tenant-seal.p12"), password);
byte[] sealed = PdfSigner.sign(invoice, key,
    SignatureField.invisible("InvoiceSeal")
        .withReason("Issued invoice 1001")
        .withLocation("Oslo, Norway")
        .withPdfDate("D:20260813120000Z"));

var check = PdfSigner.verifySingle(sealed);
if (!check.valid()) {
    throw new IllegalStateException(check.notes().toString());
}
```

Use a CA-issued organizational certificate in production. `SigningKey.selfSigned`
exists for tests and local development only.

For a validator that insists on the ETSI SubFilter:

```java
SignatureField.invisible("InvoiceSeal").withSubFilter(SignatureField.PADES_B_B);
```

The CMS attributes are the same either way. The default
`adbe.pkcs7.detached` is the more widely displayed envelope in Acrobat
and Preview.

## What this is not

- Not a QTSP
- Not QES
- Not a Scrive replacement for identification or qualified signing
- Not legal advice
- Not a guarantee that a court, auditor, or tax authority will treat the
  seal as sufficient for a particular filing
