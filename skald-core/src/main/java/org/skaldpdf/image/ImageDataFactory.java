package org.skaldpdf.image;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class ImageDataFactory {
    private ImageDataFactory() {
    }

    public static ImageData create(byte[] bytes) {
        return new ImageData(bytes);
    }

    public static ImageData create(InputStream input) {
        Objects.requireNonNull(input, "input");
        try {
            return create(input.readAllBytes());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read image", exception);
        }
    }

    public static ImageData create(Path path) {
        try (var input = Files.newInputStream(Objects.requireNonNull(path, "path"))) {
            return create(input);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read image", exception);
        }
    }
}
