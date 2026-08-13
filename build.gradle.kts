import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.tasks.Jar

plugins {
    base
}

val releaseVersion = "0.3.0-SNAPSHOT"
val moduleTitles = mapOf(
    "skald-core" to "Skald Core",
    "skald-layout" to "Skald Layout",
    "skald-barcode" to "Skald Barcode"
)
val moduleDescriptions = mapOf(
    "skald-core" to "Native PDF 2.0 writing, reading, fonts, images, and composition",
    "skald-layout" to "Flow layout and high-level document API for Skald PDF",
    "skald-barcode" to "EAN-13 barcode generation for Skald PDF"
)

allprojects {
    group = "org.skaldpdf"
    version = releaseVersion
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    description = moduleDescriptions.getValue(name)

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
        withSourcesJar()
        withJavadocJar()
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

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("library") {
                from(components["java"])
                pom {
                    name = moduleTitles.getValue(project.name)
                    description = moduleDescriptions.getValue(project.name)
                    url = "https://github.com/beint-no/skald-pdf"
                    licenses {
                        license {
                            name = "Apache License, Version 2.0"
                            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                            distribution = "repo"
                        }
                    }
                    developers {
                        developer {
                            id = "contributors"
                            name = "Skald PDF contributors"
                            url = "https://github.com/beint-no/skald-pdf/graphs/contributors"
                        }
                    }
                    scm {
                        connection = "scm:git:https://github.com/beint-no/skald-pdf.git"
                        developerConnection = "scm:git:ssh://git@github.com/beint-no/skald-pdf.git"
                        url = "https://github.com/beint-no/skald-pdf"
                    }
                }
            }
        }
    }
}

tasks.named("build") {
    dependsOn(subprojects.map { it.tasks.named("build") })
}
