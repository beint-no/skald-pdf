dependencies {
    api(project(":skald-optimize"))
    api(project(":skald-image"))
    testImplementation(project(":skald-fonts"))
    testImplementation(project(":skald-brotli"))
    testImplementation("org.apache.pdfbox:pdfbox:3.0.8")
}
tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.register<JavaExec>("imageBenchmark") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.skaldpdf.optimize.jxl.JpegXlBenchmark")
    workingDir = rootProject.projectDir
    systemProperty("skald.benchmark.losslessOnly", providers.gradleProperty("benchmarkLosslessOnly").orElse("false").get())
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx2g")
    if (providers.gradleProperty("benchmarkCorpus").isPresent) args(providers.gradleProperty("benchmarkCorpus").get())
}
