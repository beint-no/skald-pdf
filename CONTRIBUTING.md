# Contributing

Skald welcomes focused bug reports, conformance fixtures, documentation fixes,
and implementation proposals.

## Before opening a change

- Search existing issues and describe the PDF use case or specification rule.
- For a public API proposal, explain why the capability belongs in core, layout,
  barcode, sign, image, optimize, or a new optional module.
- Do not add a production dependency without first showing why the JDK and a
  small native implementation are insufficient.

## Build and test

Use JDK 25 or newer:

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

## Design principles

- Emit PDF 2.0 only.
- Fail closed on malformed or unsupported input.
- Prefer immutable values and explicit policy over global configuration.
- Keep optional capabilities out of the core dependency graph.
- Optimize generation directly instead of relying on a whole-file cleanup pass.
- Cite the relevant specification section when behavior is not self-evident.

By contributing, you agree that your contribution is licensed under Apache 2.0.
