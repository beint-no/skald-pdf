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

The normal release path is a matching version tag. Set the version in
`build.gradle.kts`, merge the change to `main`, and push the tag:

```shell
git tag v1.11.0
git push origin v1.11.0
```

The workflow verifies that the tag matches the Gradle version, then builds and
publishes that exact tagged commit. No local Maven credentials are needed.

For an API/manual fallback, run the workflow from `main` and provide the
version input. An agent can do this with:

```shell
gh workflow run publish.yml --repo beint-no/skald-pdf --ref main \
  -f version=1.11.0
```

## Release

Set the version in `build.gradle.kts`, then:

```shell
./gradlew clean build
./gradlew publishAndReleaseToMavenCentral
```

The Central Portal typically takes 10–30 minutes after a successful deployment
before the artifacts are downloadable from Maven Central.
