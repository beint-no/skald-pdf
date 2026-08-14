rootProject.name = "skald-pdf"

include(
    "skald-core",
    "skald-layout",
    "skald-barcode",
    "skald-sign",
    "skald-image",
    "skald-optimize",
    "skald-label-sticker",
    "skald-labels"
)

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
