rootProject.name = "skald-pdf"

include("skald-core", "skald-layout", "skald-barcode", "skald-sign", "skald-image")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
