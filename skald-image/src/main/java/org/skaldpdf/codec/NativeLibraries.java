package org.skaldpdf.codec;

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Finds optional native codecs. Missing libraries are not an error. */
final class NativeLibraries {
    private static final Arena ARENA = Arena.global();

    private NativeLibraries() {
    }

    static Optional<SymbolLookup> turboJpeg() {
        return load("SKALD_TURBOJPEG", List.of(
            "/opt/homebrew/opt/jpeg-turbo/lib/libturbojpeg.dylib",
            "/opt/homebrew/lib/libturbojpeg.dylib",
            "/usr/local/lib/libturbojpeg.dylib",
            "/usr/lib/libturbojpeg.so.0",
            "/usr/lib/x86_64-linux-gnu/libturbojpeg.so.0",
            "/usr/lib/aarch64-linux-gnu/libturbojpeg.so.0"
        ), "turbojpeg");
    }

    static Optional<SymbolLookup> heif() {
        return load("SKALD_HEIF", List.of(
            "/opt/homebrew/opt/libheif/lib/libheif.dylib",
            "/opt/homebrew/lib/libheif.dylib",
            "/usr/local/lib/libheif.dylib",
            "/usr/lib/libheif.so.1",
            "/usr/lib/x86_64-linux-gnu/libheif.so.1",
            "/usr/lib/aarch64-linux-gnu/libheif.so.1"
        ), "heif");
    }

    static Optional<SymbolLookup> jxl() {
        return load("SKALD_JXL", List.of(
            "/opt/homebrew/opt/jpeg-xl/lib/libjxl.dylib",
            "/opt/homebrew/lib/libjxl.dylib",
            "/usr/local/lib/libjxl.dylib",
            "/usr/lib/libjxl.so.0.12",
            "/usr/lib/libjxl.so.0.11",
            "/usr/lib/libjxl.so.0.10",
            "/usr/lib/x86_64-linux-gnu/libjxl.so.0.12",
            "/usr/lib/x86_64-linux-gnu/libjxl.so.0.11",
            "/usr/lib/aarch64-linux-gnu/libjxl.so.0.12",
            "/usr/lib/aarch64-linux-gnu/libjxl.so.0.11"
        ), "jxl");
    }

    private static Optional<SymbolLookup> load(String envName, List<String> paths, String libraryName) {
        var override = System.getenv(envName);
        if (override != null && !override.isBlank()) {
            var path = Path.of(override);
            if (Files.isRegularFile(path)) {
                return Optional.of(SymbolLookup.libraryLookup(path, ARENA));
            }
            return Optional.empty();
        }
        for (var candidate : paths) {
            var path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                return Optional.of(SymbolLookup.libraryLookup(path, ARENA));
            }
        }
        try {
            return Optional.of(SymbolLookup.libraryLookup(libraryName, ARENA));
        } catch (IllegalArgumentException ignored) {
            var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            var prefix = os.contains("win") ? "" : "lib";
            var suffix = os.contains("mac") || os.contains("darwin") ? ".dylib"
                : os.contains("win") ? ".dll" : ".so";
            try {
                return Optional.of(SymbolLookup.libraryLookup(prefix + libraryName + suffix, ARENA));
            } catch (IllegalArgumentException alsoIgnored) {
                return Optional.empty();
            }
        }
    }
}
