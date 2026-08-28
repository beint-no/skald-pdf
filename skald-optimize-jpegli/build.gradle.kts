dependencies {
    api(project(":skald-optimize"))
    api("no.beint.glimt:jpegli:0.4.1")
    runtimeOnly("no.beint.glimt:jpeg:0.4.1")
    runtimeOnly("no.beint.glimt:resize:0.4.1")

    testImplementation("org.apache.pdfbox:pdfbox:3.0.8")
}

tasks.test {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
