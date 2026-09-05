val glimtVersion = "0.5.1"

dependencies {
    api(project(":skald-optimize"))
    api("no.beint.glimt:jpegli:$glimtVersion")
    runtimeOnly("no.beint.glimt:jpeg:$glimtVersion")
    runtimeOnly("no.beint.glimt:resize:$glimtVersion")

    for (platform in listOf("macos-arm64", "linux-x64-glibc")) {
        for (codec in listOf("jpegli", "jpeg", "resize")) {
            testRuntimeOnly("no.beint.glimt:$codec-$platform:$glimtVersion")
        }
    }

    testImplementation("org.apache.pdfbox:pdfbox:3.0.8")
}

tasks.test {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
