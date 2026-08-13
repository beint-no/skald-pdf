package org.skaldpdf.layout.element;

import org.skaldpdf.geom.PageSize;

public final class AreaBreak extends AbstractElement<AreaBreak> {
    private final PageSize nextPageSize;

    public AreaBreak() {
        this(null);
    }

    public AreaBreak(PageSize nextPageSize) {
        this.nextPageSize = nextPageSize;
    }

    public PageSize nextPageSize() {
        return nextPageSize;
    }

    @Override
    protected AreaBreak self() {
        return this;
    }
}
