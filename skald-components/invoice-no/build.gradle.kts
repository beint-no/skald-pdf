dependencies {
    api(project(":skald-layout"))
    implementation(project(":skald-fonts"))
    implementation(project(":skald-barcode"))

    testImplementation("org.apache.pdfbox:pdfbox:3.0.8")
    testImplementation("com.google.zxing:core:3.5.4")
    testImplementation("com.google.zxing:javase:3.5.4")
}
