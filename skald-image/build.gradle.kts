import org.gradle.api.tasks.compile.JavaCompile

dependencies {
    api(project(":skald-core"))
    testImplementation("org.apache.pdfbox:pdfbox:3.0.8")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-restricted")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
