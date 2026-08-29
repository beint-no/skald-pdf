# Private PDF corpus

Real customer PDFs used for performance and compatibility testing belong in
the repository-local `benchmark-corpus/` directory. Git ignores that entire
directory so production documents and generated reports cannot be committed.

The recommended layout is:

```text
benchmark-corpus/
  production-all/       # all available PDFs, or a symlink to them
  largest-250/          # deterministic size-ranked focus corpus
```

Run the opt-in corpus test against either directory:

```shell
SKALD_PDF_CORPUS=benchmark-corpus/largest-250 \
SKALD_PDF_OUTPUT_CORPUS=benchmark-corpus/optimized-largest-250 \
  ./gradlew :skald-optimize-jpegli:test \
  --tests '*PdfCorpusBenchmarkTest'
```

If `SKALD_PDF_CORPUS` is unset and `benchmark-corpus/largest-250` exists, the
test uses it automatically. Otherwise the test is skipped. Reports are written
to `skald-optimize-jpegli/build/benchmarks/` and contain only local file names,
sizes, timings, structural classifications, and aggregate exact-payload
duplication. Symbolic-link corpus roots are followed.

The focus corpus must be selected by byte size rather than by a hand-picked
list. Recreate it from a source directory with:

```shell
python3 tools/select_pdf_corpus.py \
  /path/to/production-pdfs benchmark-corpus/largest-250 --limit 250
```

The selector hard-links files when possible and falls back to copying. It
replaces only its destination directory, refuses a destination inside its
source, and writes `manifest.csv` with the original relative path and SHA-256.
Treat the manifest as private corpus data too; it remains below the ignored
directory.

`SKALD_PDF_OUTPUT_CORPUS` is optional. When set, changed candidates are written
there with their rank prefix so independent tools such as `qpdf --check`,
Poppler, MuPDF, and Ghostscript can validate the exact benchmark outputs.
