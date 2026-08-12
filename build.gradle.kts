plugins {
    `java-library`
    `maven-publish`
}

group = "no.beint"
version = "0.1.0"

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

repositories {
    mavenCentral()
}

dependencies {
    api("org.apache.pdfbox:pdfbox:3.0.8")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.zxing:core:3.5.4")
    testImplementation("com.google.zxing:javase:3.5.4")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}

tasks.jar {
    manifest {
        attributes(
            "Automatic-Module-Name" to "no.beint.skald",
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
                description = "Modern, focused PDF generation for JDK 25+"
                url = "https://github.com/beint-no/skald-pdf"
                licenses {
                    license {
                        name = "Proprietary"
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
