// The core engine intentionally has no production dependencies outside the JDK.

dependencies {
    testImplementation(project(":skald-fonts"))
    testImplementation("org.apache.pdfbox:pdfbox:3.0.8")
}
