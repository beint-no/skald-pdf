import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    base
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
}

val releaseVersion = providers.gradleProperty("releaseVersion").orElse("1.15.0").get()
val moduleTitles = mapOf(
    "skald-core" to "Skald Core",
    "skald-fonts" to "Skald Fonts",
    "skald-layout" to "Skald Layout",
    "skald-barcode" to "Skald Barcode",
    "skald-sign" to "Skald Sign",
    "skald-image" to "Skald Image",
    "skald-optimize" to "Skald Optimize",
    "skald-optimize-jpegli" to "Skald Optimize JPEGli",
    "skald-label-sticker" to "Skald Clothing Sticker",
    "skald-label-shipping" to "Skald Shipping Label",
    "skald-invoice-no" to "Skald Norwegian Invoice",
    "skald-packing-slip-no" to "Skald Norwegian Packing Slip",
    "skald-reminder-no" to "Skald Norwegian Reminder",
    "skald-statement-no" to "Skald Norwegian Statement",
    "skald-receipt-no" to "Skald Norwegian Receipt",
    "skald-purchase-order-no" to "Skald Norwegian Purchase Order"
)
val moduleDescriptions = mapOf(
    "skald-core" to "Native PDF 2.0 writing, reading, custom fonts, prepared images, and composition",
    "skald-fonts" to "Lazily loaded Skald Sans faces for Skald PDF",
    "skald-layout" to "Flow layout and high-level document API for Skald PDF",
    "skald-barcode" to "Immutable EAN-13, UPC-A, Code 128, GS1-128, and QR symbols for Skald PDF",
    "skald-sign" to "Optional PKCS#7/CMS PAdES-B-B sealing for Skald PDF documents",
    "skald-image" to "Optional JDK and native decoding, scaling, and photo ingest for Skald PDF",
    "skald-optimize" to "Optional recompression of image XObjects already stored in a received PDF",
    "skald-optimize-jpegli" to "Optional JPEGli image encoder for Skald PDF optimization",
    "skald-label-sticker" to "93×35 mm clothing EAN-13 sticker print stock",
    "skald-label-shipping" to "100×150 mm shipping label with address and tracking",
    "skald-invoice-no" to "Opinionated Norwegian invoice theme (faktura, kreditnota, tilbud, ordrebekreftelse)",
    "skald-packing-slip-no" to "Norwegian packing slip and delivery note",
    "skald-reminder-no" to "Norwegian payment reminder and collection notice",
    "skald-statement-no" to "Norwegian statement of account",
    "skald-receipt-no" to "Norwegian A5 sales receipt",
    "skald-purchase-order-no" to "Norwegian purchase order"
)

allprojects {
    group = "no.beint.skaldpdf"
    version = releaseVersion
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.vanniktech.maven.publish")

    description = moduleDescriptions.getValue(name)

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(26))
    }

    dependencies {
        // Named-module consumers must see the annotation module while javac
        // resolves our `requires static transitive` descriptors. It remains
        // absent from every runtime classpath.
        add("compileOnlyApi", "org.jspecify:jspecify:1.0.0")
        add("testImplementation", "org.jspecify:jspecify:1.0.0")
        add("testImplementation", platform("org.junit:junit-bom:6.0.3"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 26
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        workingDir = rootProject.projectDir
        systemProperty("java.awt.headless", "true")
    }

    val verifyNoRuntimeDependencies = tasks.register("verifyNoRuntimeDependencies") {
        group = "verification"
        description = "Fails when production runtimeClasspath contains a third-party module"
        doLast {
            val externalModules = configurations.getByName("runtimeClasspath")
                .incoming.resolutionResult.allComponents
                .map { it.id }
                .filterIsInstance<ModuleComponentIdentifier>()
            val unexpected = externalModules.filterNot {
                project.name == "skald-optimize-jpegli" && it.group == "no.beint.glimt"
            }
            check(unexpected.isEmpty()) {
                "$name has unexpected third-party runtime dependencies: ${unexpected.joinToString()}"
            }
        }
    }

    tasks.named("check") {
        dependsOn(verifyNoRuntimeDependencies)
    }

    tasks.withType<Jar>().configureEach {
        from(rootProject.files("NOTICE", "LICENSE")) {
            into("META-INF")
        }
        manifest {
            attributes(
                "Implementation-Title" to moduleTitles.getValue(project.name),
                "Implementation-Version" to project.version
            )
        }
    }

    extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
        publishToMavenCentral()
        if (hasInMemorySigningKey()) {
            signAllPublications()
        }
        pom {
            name.set(moduleTitles.getValue(project.name))
            description.set(moduleDescriptions.getValue(project.name))
            inceptionYear.set("2026")
            url.set("https://github.com/beint-no/skald-pdf")
            licenses {
                license {
                    name.set("Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("beint-no")
                    name.set("Beint")
                    url.set("https://github.com/beint-no")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/beint-no/skald-pdf.git")
                developerConnection.set("scm:git:ssh://git@github.com/beint-no/skald-pdf.git")
                url.set("https://github.com/beint-no/skald-pdf")
            }
        }
    }
}

fun Project.hasInMemorySigningKey(): Boolean {
    return providers.gradleProperty("signingInMemoryKey").orNull?.isNotBlank() == true
        || System.getenv("SIGNING_IN_MEMORY_KEY")?.isNotBlank() == true
        || System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey")?.isNotBlank() == true
}

tasks.named("build") {
    dependsOn(subprojects.map { it.tasks.named("build") })
}

tasks.register("printReleaseVersion") {
    doLast { println(releaseVersion) }
}
