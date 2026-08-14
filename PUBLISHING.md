# Publishing

Skald publishes `skald-core`, `skald-layout`, `skald-barcode`,
`skald-label-sticker`, `skald-labels`, `skald-sign`, `skald-image`, and
`skald-optimize` to Maven Central under `no.beint.skaldpdf`. Java packages remain
`org.skaldpdf`.
`org.skaldpdf` is not a registered Central namespace.

## Secrets

Do not put tokens or the private signing key in the repository. Export them as
environment variables (see `~/.config/skald/maven-central.env` on a release
machine):

```text
MAVEN_CENTRAL_USERNAME
MAVEN_CENTRAL_PASSWORD
SIGNING_IN_MEMORY_KEY
SIGNING_IN_MEMORY_KEY_ID
SIGNING_IN_MEMORY_KEY_PASSWORD
```

Gradle also accepts the same values as `ORG_GRADLE_PROJECT_mavenCentralUsername`,
`ORG_GRADLE_PROJECT_mavenCentralPassword`, `ORG_GRADLE_PROJECT_signingInMemoryKey`,
`ORG_GRADLE_PROJECT_signingInMemoryKeyId`, and
`ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`.

Generate a Central Portal user token at
<https://central.sonatype.com/usertoken>.

## Release

Set the version in `build.gradle.kts`, then:

```shell
./gradlew clean build
./gradlew publishAndReleaseToMavenCentral
```

The Central Portal typically takes 10–30 minutes after a successful deployment
before the artifacts are downloadable from Maven Central.
