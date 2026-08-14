dependencies {
    api(project(":skald-core"))

    testImplementation(project(":skald-barcode"))
    testImplementation(project(":skald-label-sticker"))
    testImplementation(project(":skald-sign"))
    testImplementation("org.apache.pdfbox:pdfbox:3.0.8")
    testImplementation("com.google.zxing:core:3.5.4")
    testImplementation("com.google.zxing:javase:3.5.4")
}

tasks.register<JavaExec>("writeSiteDemos") {
    group = "documentation"
    description = "Generate website demo PDFs and first-page previews"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.skaldpdf.SiteDemos")
    args(rootProject.layout.projectDirectory.dir("site/demos").asFile.absolutePath)
    dependsOn(tasks.named("testClasses"))
}
