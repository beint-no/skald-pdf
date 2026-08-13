package org.skaldpdf;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductStickerGalleryTest {
    @Test
    void writesEcomtoolsComparisonStickers() throws Exception {
        var downloads = Path.of(System.getProperty("user.home"), "Downloads");
        var target = Files.isDirectory(downloads)
            ? downloads.resolve("skald-ean-stickers")
            : Path.of("build", "skald-ean-stickers");
        ProductStickerTest.writeGallery(target);
        var replica = target.resolve("SOJA-BA-L_8123613319580_ean_sticker.pdf");
        assertTrue(Files.exists(replica), replica.toString());
        assertEqualsEan(replica);
        PdfTestSupport.saveArtifacts("ecomtools-gallery-soja-ba-l", Files.readAllBytes(replica));
    }

    private static void assertEqualsEan(Path pdf) throws Exception {
        assertTrue(ProductStickerTest.decode(Files.readAllBytes(pdf)).equals("8123613319580"));
    }
}
