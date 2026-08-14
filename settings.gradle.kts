rootProject.name = "skald-pdf"

include("skald-core", "skald-layout", "skald-barcode", "skald-sign", "skald-image", "skald-optimize")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
