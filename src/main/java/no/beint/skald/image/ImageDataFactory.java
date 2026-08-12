package no.beint.skald.image;

public final class ImageDataFactory {
    private ImageDataFactory() {
    }

    public static ImageData create(byte[] bytes) {
        return new ImageData(bytes);
    }
}
