# Publishing

Skald publishes `skald-core`, `skald-fonts`, `skald-layout`, `skald-barcode`,
`skald-sign`, `skald-image`, `skald-optimize`, `skald-optimize-jpegli`, and each
`skald-components/` artifact (`skald-invoice-no`, `skald-label-sticker`,
…) to Maven Central under `no.beint.skaldpdf`. Java packages remain
`org.skaldpdf`.
`org.skaldpdf` is not a registered Central namespace.

## Secrets

Do not put tokens or the private signing key in the repository. Set these
environment variables in the shell or CI environment:

```text
MAVEN_CENTRAL_USERNAME
MAVEN_CENTRAL_PASSWORD
SIGNING_IN_MEMORY_KEY
SIGNING_IN_MEMORY_KEY_ID
SIGNING_IN_MEMORY_KEY_PASSWORD
```

Generate a Central Portal user token at
<https://central.sonatype.com/usertoken>.

## Release

Set the version in `build.gradle.kts`, merge the change to `main`, and push a
matching tag:

```shell
git tag v1.12.0
git push origin v1.12.0
```

GitHub Actions verifies that the tag matches the Gradle version, then builds
and publishes that exact tagged commit. No local Maven credentials are needed.
