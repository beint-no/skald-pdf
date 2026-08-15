import org.gradle.api.tasks.compile.JavaCompile

dependencies {
    api(project(":skald-core"))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-restricted")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
