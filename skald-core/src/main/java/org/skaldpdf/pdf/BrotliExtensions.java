package org.skaldpdf.pdf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.skaldpdf.pdf.CosValue.*;

/** PDF Association EXTN-BROTLI-1 v1.3, clause 4. */
final class BrotliExtensions {
    private BrotliExtensions() {
    }

    static CosDictionary declare(NativePdfParser source, CosDictionary catalog) {
        var values = new LinkedHashMap<>(catalog.values());
        var extensions = new LinkedHashMap<String, CosValue>();
        if (catalog.get("Extensions") != null) {
            if (!(source.resolve(catalog.get("Extensions")) instanceof CosDictionary existing)) {
                throw new IllegalArgumentException("Catalog Extensions must be a dictionary");
            }
            extensions.putAll(existing.values());
        }
        var declaration = new CosDictionary(Map.of(
            "Type", new CosName("DeveloperExtensions"), "BaseVersion", new CosName("2.0"),
            "ExtensionLevel", new CosNumber("1"), "ExtensionRevision", text("2026"),
            "URL", text("https://pdfa.org/resource/extension-brotli")));
        var previous = extensions.get("PDFa");
        if (previous == null) {
            extensions.put("PDFa", declaration);
        } else {
            var resolved = source.resolve(previous);
            var entries = resolved instanceof CosArray array ? array.values() : java.util.List.of(previous);
            var present = entries.stream().map(source::resolve).anyMatch(value -> value instanceof CosDictionary item
                && source.resolve(item.get("BaseVersion")) instanceof CosName name && name.value().equals("2.0")
                && source.resolve(item.get("ExtensionLevel")) instanceof CosNumber level && level.lexicalValue().equals("1"));
            if (!present) {
                var all = new ArrayList<>(entries);
                all.add(declaration);
                extensions.put("PDFa", new CosArray(all));
            }
        }
        values.put("Version", new CosName("2.0"));
        values.put("Extensions", new CosDictionary(extensions));
        return new CosDictionary(values);
    }

    private static CosString text(String value) {
        return new CosString(value.getBytes(StandardCharsets.US_ASCII));
    }
}
