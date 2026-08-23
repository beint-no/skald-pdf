package org.skaldpdf.layout.element;

import org.jspecify.annotations.Nullable;
import org.skaldpdf.geom.PageSize;

public final class AreaBreak extends AbstractElement<AreaBreak> {
    private final @Nullable PageSize nextPageSize;

    public AreaBreak() {
        this(null);
    }

    public AreaBreak(@Nullable PageSize nextPageSize) {
        this.nextPageSize = nextPageSize;
    }

    public @Nullable PageSize nextPageSize() {
        return nextPageSize;
    }

    @Override
    protected AreaBreak self() {
        return this;
    }
}
