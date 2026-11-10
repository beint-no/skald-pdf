// The core engine intentionally has no production dependencies outside the JDK.

dependencies {
    testImplementation(project(":skald-fonts"))
    testImplementation(project(":skald-image"))
    testImplementation("org.apache.pdfbox:pdfbox:3.0.8")
}

tasks.register<JavaExec>("importPerformanceBenchmark") {
    group = "verification"
    description = "Measure imported image inspection, graph hashing and rewrite allocations (opt-in)"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.skaldpdf.pdf.ImportPerformanceBenchmark")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    jvmArgs("-Xms512m", "-Xmx512m", "-Djava.awt.headless=true")
}
