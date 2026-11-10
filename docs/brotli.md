# Optional Brotli compression in PDF 2.0

Skald supports the PDF Association's [Brotli extension, EXTN-BROTLI-1 v1.3](https://pdfa.org/resource/extension-brotli/).
Reading uses an optional codec service; writing requires an explicit Brotli
compression policy. Adding the module never changes the default output format.
Available since 1.21.0.

## Install

Add `no.beint.skaldpdf:skald-brotli` at the same version as your other Skald
modules. It depends only on `skald-core` and the JDK; it does not bundle native
binaries or add a third-party Java runtime dependency. Install the reference
libbrotli decoder and encoder on the host (64-bit systems):

```sh
# macOS
brew install brotli
# Debian / Ubuntu
sudo apt-get install libbrotli1
```

Use a current supported libbrotli release. The implementation is tested with
libbrotli 1.2.1 locally and the distribution's library in Linux CI. The module
uses the JDK FFM API and needs native access when actually using Brotli:

```sh
# Named modules
java --enable-native-access=org.skaldpdf.brotli ...
# Class path
java --enable-native-access=ALL-UNNAMED ...
```

The service works on both the module path and class path. On a module path,
ensure `org.skaldpdf.brotli` is resolved (for example with
`--add-modules=org.skaldpdf.brotli`). The module itself exports no application API.
Host lookup covers common Homebrew and Linux locations and the platform library
search path. Override with `SKALD_BROTLI_DEC` and `SKALD_BROTLI_ENC` containing
absolute library paths. An explicit missing override disables that native
component instead of falling back to a different installed library.

## Read

With the module and native decoder available, Skald transparently decodes
`BrotliDecode` in page contents, image XObjects, fonts/ToUnicode, metadata,
object streams and cross-reference streams. It supports raw standard Brotli and
large-window Brotli, TIFF predictor 2 (1/2/4/8/16-bit samples), PNG predictors
10–15, and per-stage parameters in filter chains. Image conversion remains
limited to the image/color/sample-depth subset documented by `EmbeddedImage`.

```java
boolean canReadBrotli = Compression.brotliDecodingAvailable();
```

Absent a decoder, conventional PDFs still work. Brotli images stay opaque and
cannot be recompressed; structural streams that need Brotli decoding fail with
an installation message. Preserving an encoded image is not the same as being
able to decode it. Skald's existing limitation on reading encrypted PDFs remains.

Shared dictionaries and RFC 9841 framing are not enabled, as required by the PDF
extension. One stream must consume all its input: truncated, malformed, trailing
and concatenated data are rejected. Decoded output is limited to the core's
128 MiB per-stream bound; decoder-owned native memory is separately capped at
that bound plus 32 MiB. Validation discards output instead of retaining it.
These limits intentionally also constrain otherwise-valid very large streams.

## Opt into writing

```java
var compression = Compression.BROTLI_BALANCED;
if (!compression.available()) {
    throw new IllegalStateException("Install the optional Brotli codec first");
}
var properties = new WriterProperties(compression);
try (var document = new PdfDocument(new PdfWriter(output, properties))) {
    // Add pages as usual.
}
```

`BROTLI_FAST`, `BROTLI_BALANCED` and `BROTLI_MAXIMUM` select reference encoder
qualities 1, 6 and 11. Maximum quality can be expensive. Encoding uses a standard
window of 22 for interoperability; decoding also accepts large-window input.
Missing codecs fail before PDF bytes are emitted instead of silently selecting
another output codec. `WriterProperties.defaults()` still uses Flate BALANCED.

New eligible streams use Brotli, including page contents, fonts, lossless images,
soft masks and object streams. JPEG images keep `DCTDecode`. Cross-reference
streams deliberately retain Flate, and the catalog stays outside object streams,
so processors can discover the extension before attempting Brotli decoding.
No Brotli inline images are emitted. Output declares the PDFa developer extension
with BaseVersion 2.0, ExtensionLevel 1 and ExtensionRevision 2026. Encryption is
applied after compression, including to the extension's string values.

For an imported document, `compressImportedStreamsLosslessly()` tries the chosen
codec on eligible streams and retains the original unless savings justify the
change. Predictors and their sample bytes are retained; JPEG/unknown filters and
external stream data are not transcoded. Canonical rewriting verifies the graph
against the intended stream replacements and extension declaration before
emitting output. Brotli writing upgrades a legacy input's header/catalog version
to PDF 2.0 and retains other developer-extension entries. It refuses to retain an
unvalidated conformance declaration such as PDF/A or PDF/X.

For received-PDF optimization:

```java
var options = OptimizeOptions.builder()
    .streamCompression(Compression.BROTLI_BALANCED)
    .build();
byte[] candidate = PdfOptimizer.recompress(input, options);
```

The optimizer's default remains Flate MAXIMUM. Its existing fail-safe behavior
and savings gates still apply: unsupported or protected inputs and failures
return the original array. Selecting Flate is not a general downgrade operation
for an imported Brotli PDF; preserved original streams can still require Brotli.

Enable Brotli output only when your recipients' PDF viewers support the
extension. Browser support for HTTP Brotli compression does not establish PDF
filter support. There is no automatic viewer detection or scheduled switch.

## Verification

Additional independently published inputs are available in the PDF Association's
[Brotli prototype PDF examples](https://pdfa.org/download-area/examples/Brotli-Prototype-PDFs.zip).
They are not downloaded during the build; verify each document's own license
before copying it into another repository.

```sh
SKALD_REQUIRE_BROTLI_TESTS=1 ./gradlew :skald-brotli:check
```

The tests generate a small viewer corpus in `build/brotli-pdfs`, also uploaded
as a CI artifact. The encrypted example uses the test password `brotli-test`.

CI installs the native libraries and reference CLI and requires the tests to
run. Core tests independently cover absence of the provider; a separate module
test process disables both native libraries. Tests cover exact ReAI invoice
samples, raw/large-window reference vectors, Brotli object/xref streams, filter
chains, predictors, malformed inputs, decoded limits, transparency, encryption
ordering, extension preservation and optimizer opt-in.

`scripts/verify-brotli.py` transcodes Skald-generated unencrypted output with the
reference Brotli CLI, preserves predictor parameters and appends a replacement
xref. PDFBox then independently checks complete-page pixel equality with a Flate
control. This is a test tool for Skald's writer format, not a general PDF parser
or production dependency. MuPDF 1.28.6 additionally verified native Brotli and
encrypted Brotli output during implementation. Baseline ISO PDF 2.0 validators
may not recognize this industry extension; their support must be distinguished
from the validity of the extension itself.
