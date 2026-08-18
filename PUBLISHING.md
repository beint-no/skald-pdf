# Publishing

Skald publishes `skald-core`, `skald-fonts`, `skald-layout`, `skald-barcode`,
`skald-sign`, `skald-image`, `skald-optimize`, and each
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

## GitHub Actions release

An administrator should create a `maven-central` environment in the repository
settings, optionally require approval, and add these secrets to that
environment:

```text
MAVEN_CENTRAL_USERNAME
MAVEN_CENTRAL_PASSWORD
SIGNING_IN_MEMORY_KEY
SIGNING_IN_MEMORY_KEY_ID
SIGNING_IN_MEMORY_KEY_PASSWORD
```

Team members with permission to run repository workflows can then open
Actions → Publish to Maven Central → Run workflow, enter the version, and start
the release. The workflow always publishes the current `main` branch, so merge
the intended changes before running it. No local Maven credentials are needed.

## Release

Set the version in `build.gradle.kts`, then:

```shell
./gradlew clean build
./gradlew publishAndReleaseToMavenCentral
```

The Central Portal typically takes 10–30 minutes after a successful deployment
before the artifacts are downloadable from Maven Central.
