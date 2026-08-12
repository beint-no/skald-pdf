package org.skaldpdf.pdf;

import static org.skaldpdf.pdf.CosValue.CosReference;

import org.skaldpdf.geom.PageSize;
import org.skaldpdf.geom.Rectangle;

import java.util.Map;
import java.util.Set;

record ImportedPage(NativePdfParser source, CosReference reference, Map<String, CosValue> dictionary,
                    PageSize pageSize, Rectangle cropBox, int rotation, Set<String> resourceNames) {
}
