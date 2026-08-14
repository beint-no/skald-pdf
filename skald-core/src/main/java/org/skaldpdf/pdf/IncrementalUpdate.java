package org.skaldpdf.pdf;

import static org.skaldpdf.pdf.CosValue.CosArray;
import static org.skaldpdf.pdf.CosValue.CosBoolean;
import static org.skaldpdf.pdf.CosValue.CosDictionary;
import static org.skaldpdf.pdf.CosValue.CosName;
import static org.skaldpdf.pdf.CosValue.CosNull;
import static org.skaldpdf.pdf.CosValue.CosNumber;
import static org.skaldpdf.pdf.CosValue.CosReference;
import static org.skaldpdf.pdf.CosValue.CosStream;
import static org.skaldpdf.pdf.CosValue.CosString;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Appends a signature placeholder as a new PDF revision so earlier seals stay
 * valid. Used by {@code skald-sign} when the input is already sealed.
 */
public final class IncrementalUpdate {
    private IncrementalUpdate() {
    }

    public static boolean isSealed(byte[] pdf) {
        return NativePdfParser.containsSealedSignature(pdf);
    }

    public static byte[] appendSignaturePlaceholder(byte[] pdf, SignatureField field) {
        if (field.visible()) {
            throw new IllegalArgumentException(
                "A second signature on a sealed file must be invisible; visible widgets need a page rewrite");
        }
        var parser = new NativePdfParser(pdf);
        var sigNumber = parser.maximumObjectNumber() + 1;
        var widgetNumber = sigNumber + 1;
        var catalogNumber = parser.catalogReference().objectNumber();
        var pageNumber = parser.pages().get(field.pageNumber() - 1).reference().objectNumber();

        var fieldRefs = new ArrayList<CosValue>(parser.acroFormFields());
        fieldRefs.add(new CosReference(widgetNumber, 0));

        var catalog = (CosDictionary) parser.resolve(parser.catalogReference());
        var catalogValues = new LinkedHashMap<>(catalog.values());
        var acroForm = new LinkedHashMap<String, CosValue>();
        acroForm.put("Fields", new CosArray(fieldRefs));
        acroForm.put("SigFlags", new CosNumber("3"));
        catalogValues.put("AcroForm", new CosDictionary(acroForm));

        var sig = signatureDictionary(field);
        var widget = widgetDictionary(field, sigNumber, pageNumber);
        var catalogBody = "<< " + dictionaryBody(catalogValues) + " >>";

        var output = new ByteArrayOutputStream(pdf.length + 2048);
        try {
            output.write(pdf);
            if (pdf.length == 0 || pdf[pdf.length - 1] != '\n') {
                output.write('\n');
            }
            var offsets = new LinkedHashMap<Integer, Integer>();
            offsets.put(sigNumber, output.size());
            writeObject(output, sigNumber, sig);
            offsets.put(widgetNumber, output.size());
            writeObject(output, widgetNumber, widget);
            offsets.put(catalogNumber, output.size());
            writeObject(output, catalogNumber, catalogBody.getBytes(StandardCharsets.US_ASCII));

            var xrefOffset = output.size();
            var xref = new StringBuilder("xref\n0 1\n0000000000 65535 f \n");
            offsets.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry ->
                xref.append(entry.getKey()).append(" 1\n")
                    .append(String.format(Locale.ROOT, "%010d 00000 n \n", entry.getValue())));
            var size = Math.max(parser.maximumObjectNumber(), widgetNumber) + 1;
            xref.append("trailer\n<< /Size ").append(size)
                .append(" /Root ").append(catalogNumber).append(" 0 R")
                .append(" /Prev ").append(parser.startXref())
                .append(" /ID [<").append(revisionId(pdf)).append("> <")
                .append(revisionId(output.toByteArray())).append(">] >>\n")
                .append("startxref\n").append(xrefOffset).append("\n%%EOF\n");
            output.write(xref.toString().getBytes(StandardCharsets.US_ASCII));
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
        return output.toByteArray();
    }

    private static String signatureDictionary(SignatureField field) {
        var body = new StringBuilder("<< /Type /Sig /Filter /Adobe.PPKLite /SubFilter /")
            .append(field.subFilter())
            .append(" /ByteRange [0 0000000000 0000000000 0000000000] /Contents <")
            .append("0".repeat(field.reservedContentBytes() * 2))
            .append('>');
        if (field.pdfDate() != null) {
            body.append(" /M ").append(plainLiteral(field.pdfDate()));
        }
        if (field.reason() != null) {
            body.append(" /Reason ").append(plainLiteral(field.reason()));
        }
        if (field.location() != null) {
            body.append(" /Location ").append(plainLiteral(field.location()));
        }
        if (field.contact() != null) {
            body.append(" /ContactInfo ").append(plainLiteral(field.contact()));
        }
        return body.append(" >>").toString();
    }

    private static String widgetDictionary(SignatureField field, int signature, int page) {
        return "<< /Type /Annot /Subtype /Widget /FT /Sig /F 132 /T "
            + plainLiteral(field.fieldName())
            + " /V " + signature + " 0 R /P " + page + " 0 R /Rect [0 0 0 0] >>";
    }

    private static String dictionaryBody(java.util.Map<String, CosValue> values) {
        var result = new StringBuilder();
        values.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry ->
            result.append('/').append(entry.getKey()).append(' ')
                .append(direct(entry.getValue())).append(' '));
        return result.toString();
    }

    private static String direct(CosValue value) {
        return switch (value) {
            case CosNull ignored -> "null";
            case CosBoolean bool -> Boolean.toString(bool.value());
            case CosNumber number -> number.lexicalValue();
            case CosName name -> "/" + name.value();
            case CosString string -> "<" + hex(string.bytes()) + ">";
            case CosArray array -> {
                var result = new StringBuilder("[");
                array.values().forEach(item -> result.append(direct(item)).append(' '));
                yield result.append(']').toString();
            }
            case CosDictionary dictionary -> "<< " + dictionaryBody(dictionary.values()) + " >>";
            case CosStream ignored -> throw new IllegalArgumentException("A stream cannot be inlined");
            case CosReference reference -> reference.objectNumber() + " 0 R";
        };
    }

    private static String plainLiteral(String value) {
        var result = new StringBuilder("(");
        for (int index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (character == '(' || character == ')' || character == '\\') {
                result.append('\\');
            }
            result.append(character);
        }
        return result.append(')').toString();
    }

    private static void writeObject(ByteArrayOutputStream output, int number, String body) throws IOException {
        writeObject(output, number, body.getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeObject(ByteArrayOutputStream output, int number, byte[] body) throws IOException {
        output.write((number + " 0 obj\n").getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.write("\nendobj\n".getBytes(StandardCharsets.US_ASCII));
    }

    private static String revisionId(byte[] bytes) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            var hex = new StringBuilder(32);
            for (int index = 0; index < 16; index++) {
                hex.append(String.format(Locale.ROOT, "%02X", digest[index] & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String hex(byte[] bytes) {
        var result = new StringBuilder(bytes.length * 2);
        for (var item : bytes) {
            result.append(String.format(Locale.ROOT, "%02X", item & 0xff));
        }
        return result.toString();
    }
}
