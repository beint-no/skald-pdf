# Contributing

Skald welcomes focused bug reports, conformance fixtures, documentation fixes,
and implementation proposals.

## Before opening a change

- Search existing issues and describe the PDF use case or specification rule.
- For a public API proposal, explain why it is an engine primitive, print
  stock (`skald-label-*`), a document theme (`skald-invoice-no`, …),
  or application/example code. See [docs/modules.md](docs/modules.md).
- Do not add a production dependency without first showing why the JDK and a
  small native implementation are insufficient.

## Build and test

Use JDK 26 or newer. `main` is the tip: it always targets the current JDK
feature release. Do not lower `--release` to keep older JDKs on the latest
artifact, and do not add `--enable-preview` to production modules. Keep
JSpecify `@NullMarked` accurate: do not return or store null for optional
text (use empty), and mark `@Nullable` only when absence is a real value.

```shell
./gradlew clean build
scripts/validate-pdf2.sh build/use-case-pdfs
```

Changes that affect rendering should include a focused test and a generated
fixture. Parser changes should include a minimal input that demonstrates both the
accepted and rejected boundary. Keep test-only validators independent of Skald.

Website copy and demo documents live in `site/`. Preview images are generated,
not committed:

```shell
./gradlew :skald-layout:writeSiteDemos
```

The build treats Java compiler warnings as errors, runs Javadoc checks, verifies
that published modules have no third-party runtime dependencies, and compiles
explicit JPMS descriptors.

Configuration caching and parallel project execution are enabled by default.
Use `./gradlew build` for normal iteration; `clean` is useful for a full rebuild
but discards up-to-date outputs. The runtime-dependency check remains active when
the configuration cache is reused. Task-output caching is not enabled by default:
some tests generate PDFs outside their declared Gradle outputs or read optional
local corpora, which must be modeled before caching their results safely.

## Design principles

- Emit PDF 2.0 only.
- Fail closed on malformed or unsupported input.
- Prefer immutable values and explicit policy over global configuration.
- Keep optional capabilities out of the core dependency graph.
- Optimize generation directly instead of relying on a whole-file cleanup pass.
- Cite the relevant specification section when behavior is not self-evident.

By contributing, you agree that your contribution is licensed under Apache 2.0.
