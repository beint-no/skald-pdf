import org.gradle.api.tasks.compile.JavaCompile

dependencies {
    api(project(":skald-core"))
    testImplementation(project(":skald-fonts"))
    testImplementation(project(":skald-image"))
    testImplementation(project(":skald-optimize"))
    testImplementation("org.apache.pdfbox:pdfbox:3.0.8")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-restricted")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

sourceSets.test {
    resources.srcDir(project(":skald-core").file("src/test/resources"))
}

// Exercise service discovery with the optional module present but both libraries absent.
tasks.named<Test>("test") {
    exclude("**/MissingNativeLibrariesTest.class")
}

val testWithoutNativeLibraries = tasks.register<Test>("testWithoutNativeLibraries") {
    dependsOn(tasks.named("testClasses"))
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/MissingNativeLibrariesTest.class")
    environment("SKALD_BROTLI_DEC", "/skald-test-missing/libbrotlidec")
    environment("SKALD_BROTLI_ENC", "/skald-test-missing/libbrotlienc")
}

tasks.named("check") {
    dependsOn(testWithoutNativeLibraries)
}

// Run :skald-layout:test first to generate the representative public corpus.
tasks.register<JavaExec>("compressionBenchmark") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.skaldpdf.brotli.PdfCompressionBenchmark")
    workingDir = rootProject.projectDir
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx2g")
    if (providers.gradleProperty("benchmarkCorpus").isPresent) args(providers.gradleProperty("benchmarkCorpus").get())
}
