rootProject.name = "skald-pdf"

include(
    "skald-core",
    "skald-layout",
    "skald-barcode",
    "skald-sign",
    "skald-image",
    "skald-optimize",
    "skald-label-sticker",
    "skald-label-shipping",
    "skald-invoice-no",
    "skald-packing-slip-no",
    "skald-reminder-no",
    "skald-statement-no",
    "skald-receipt-no",
    "skald-purchase-order-no"
)

project(":skald-label-sticker").projectDir = file("skald-components/label-sticker")
project(":skald-label-shipping").projectDir = file("skald-components/label-shipping")
project(":skald-invoice-no").projectDir = file("skald-components/invoice-no")
project(":skald-packing-slip-no").projectDir = file("skald-components/packing-slip-no")
project(":skald-reminder-no").projectDir = file("skald-components/reminder-no")
project(":skald-statement-no").projectDir = file("skald-components/statement-no")
project(":skald-receipt-no").projectDir = file("skald-components/receipt-no")
project(":skald-purchase-order-no").projectDir = file("skald-components/purchase-order-no")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
