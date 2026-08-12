plugins {
    `java-library`
    `maven-publish`
}

group = "org.skaldpdf"
version = "0.2.0"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.withType<Javadoc>().configureEach {
    (options as org.gradle.external.javadoc.StandardJavadocDocletOptions)
        .addBooleanOption("Xdoclint:all,-missing", true)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.apache.pdfbox:pdfbox:3.0.8")
    testImplementation("com.google.zxing:core:3.5.4")
    testImplementation("com.google.zxing:javase:3.5.4")
}

val verifyNoRuntimeDependencies = tasks.register("verifyNoRuntimeDependencies") {
    group = "verification"
    description = "Fails when production runtimeClasspath contains an external module"
    doLast {
        val externalModules = configurations.runtimeClasspath.get()
            .incoming.resolutionResult.allComponents
            .map { it.id }
            .filterIsInstance<org.gradle.api.artifacts.component.ModuleComponentIdentifier>()
        check(externalModules.isEmpty()) {
            "Skald must have zero external runtime dependencies: ${externalModules.joinToString()}"
        }
    }
}

tasks.check {
    dependsOn(verifyNoRuntimeDependencies)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}

tasks.jar {
    from(listOf("NOTICE", "LICENSE")) {
        into("META-INF")
    }
    manifest {
        attributes(
            "Automatic-Module-Name" to "org.skaldpdf",
            "Implementation-Title" to "Skald PDF",
            "Implementation-Version" to project.version
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            pom {
                name = "Skald PDF"
                description = "Modern PDF 2.0 generation and composition for JDK 25+"
                url = "https://github.com/beint-no/skald-pdf"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://github.com/beint-no/skald-pdf/blob/main/LICENSE"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/beint-no/skald-pdf.git"
                    developerConnection = "scm:git:git@github.com:beint-no/skald-pdf.git"
                    url = "https://github.com/beint-no/skald-pdf"
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/beint-no/skald-pdf")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: "gregjotau"
                password = System.getenv("GITHUB_TOKEN") ?: System.getenv("GH_TOKEN")
            }
        }
    }
}
