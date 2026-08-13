import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.tasks.Jar

plugins {
    base
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
}

val releaseVersion = "1.2.0"
val moduleTitles = mapOf(
    "skald-core" to "Skald Core",
    "skald-layout" to "Skald Layout",
    "skald-barcode" to "Skald Barcode"
)
val moduleDescriptions = mapOf(
    "skald-core" to "Native PDF 2.0 writing, reading, fonts, images, and composition",
    "skald-layout" to "Flow layout and high-level document API for Skald PDF",
    "skald-barcode" to "EAN-13, Code 128, GS1-128, QR, and product-sticker generation for Skald PDF"
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
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    dependencies {
        add("testImplementation", platform("org.junit:junit-bom:6.0.3"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 25
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
            check(externalModules.isEmpty()) {
                "$name must have zero third-party runtime dependencies: ${externalModules.joinToString()}"
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
