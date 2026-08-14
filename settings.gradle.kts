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

project(":skald-label-sticker").projectDir = file("skald-components/label-sticker")
project(":skald-labels").projectDir = file("skald-components/labels")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
