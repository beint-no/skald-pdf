package org.skaldpdf.optimize.jpegli;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;

/** Independent high-level fingerprint; image payload and dimensions are the only omissions. */
record PdfBoxInvariant(
    int pageCount,
    List<String> pages,
    List<String> content,
    List<String> annotations,
    List<String> imagePaths,
    List<String> fields,
    boolean form,
    boolean outline,
    boolean structure,
    boolean markInfo,
    boolean optionalContent,
    boolean embeddedFiles,
    boolean javascript,
    int signatures,
    String metadata,
    String text
) {
    static PdfBoxInvariant capture(PDDocument document) throws Exception {
        var pages = new ArrayList<String>();
        var content = new ArrayList<String>();
        var annotations = new ArrayList<String>();
        var images = new ArrayList<String>();
        var seenForms = new IdentityHashMap<COSStream, Boolean>();
        var pageIndex = 0;
        for (var page : document.getPages()) {
            pageIndex++;
            pages.add(box(page.getMediaBox()) + '|' + box(page.getCropBox()) + '|'
                + box(page.getBleedBox()) + '|' + box(page.getTrimBox()) + '|'
                + box(page.getArtBox()) + '|' + page.getRotation());
            var digest = sha256();
            var streams = page.getContentStreams();
            while (streams.hasNext()) {
                try (var stream = streams.next().createInputStream()) {
                    stream.transferTo(new DigestSink(digest));
                }
                digest.update((byte) 0xff);
            }
            content.add(hex(digest.digest()));
            for (var annotation : page.getAnnotations()) {
                annotations.add(pageIndex + "|" + annotation.getSubtype() + '|' + box(annotation.getRectangle()));
            }
            resources(page.getResources(), "p" + pageIndex, seenForms, images);
        }
        Collections.sort(images);
        Collections.sort(annotations);
        var catalog = document.getDocumentCatalog();
        var catalogDictionary = catalog.getCOSObject();
        var names = catalogDictionary.getCOSDictionary(COSName.NAMES);
        var embedded = names != null && names.containsKey(COSName.EMBEDDED_FILES);
        var javascript = names != null && names.containsKey(COSName.JAVA_SCRIPT);
        var acroForm = catalog.getAcroForm();
        var fields = new ArrayList<String>();
        if (acroForm != null) {
            acroForm.getFieldTree().forEach(field -> fields.add(
                field.getFullyQualifiedName() + '|' + field.getFieldType()));
        }
        Collections.sort(fields);
        var metadata = catalog.getMetadata() == null ? "" : digest(catalog.getMetadata().createInputStream());
        var text = hex(sha256().digest(new PDFTextStripper().getText(document).getBytes(StandardCharsets.UTF_8)));
        return new PdfBoxInvariant(document.getNumberOfPages(), pages, content, annotations, images, fields,
            acroForm != null, catalog.getDocumentOutline() != null, catalog.getStructureTreeRoot() != null,
            catalog.getMarkInfo() != null, catalog.getOCProperties() != null, embedded, javascript,
            document.getSignatureDictionaries().size(), metadata, text);
    }

    private static void resources(PDResources resources, String path,
                                  IdentityHashMap<COSStream, Boolean> seenForms,
                                  List<String> images) throws Exception {
        if (resources == null) {
            return;
        }
        for (var name : resources.getXObjectNames()) {
            var object = resources.getXObject(name);
            var child = path + '/' + name.getName();
            if (object instanceof PDImageXObject image) {
                var dictionary = image.getCOSObject();
                images.add(child + '|' + image.isStencil() + '|'
                    + present(dictionary, COSName.MASK, COSName.SMASK, COSName.DECODE,
                    COSName.INTERPOLATE, COSName.INTENT, COSName.OC, COSName.STRUCT_PARENT));
            } else if (object instanceof PDFormXObject form
                && seenForms.put(form.getCOSObject(), Boolean.TRUE) == null) {
                resources(form.getResources(), child, seenForms, images);
            }
        }
    }

    private static String present(COSDictionary dictionary, COSName... keys) {
        var result = new StringBuilder();
        for (var key : keys) {
            if (dictionary.containsKey(key)) {
                result.append(key.getName()).append(';');
            }
        }
        return result.toString();
    }

    private static String digest(InputStream input) throws IOException {
        try (input) {
            var digest = sha256();
            input.transferTo(new DigestSink(digest));
            return hex(digest.digest());
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private static String box(PDRectangle rectangle) {
        return rectangle == null ? "null" : rectangle.getLowerLeftX() + ":" + rectangle.getLowerLeftY()
            + ':' + rectangle.getUpperRightX() + ':' + rectangle.getUpperRightY();
    }

    private static final class DigestSink extends OutputStream {
        private final MessageDigest digest;

        DigestSink(MessageDigest digest) {
            this.digest = digest;
        }

        @Override
        public void write(int value) {
            digest.update((byte) value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            digest.update(bytes, offset, length);
        }
    }
}
