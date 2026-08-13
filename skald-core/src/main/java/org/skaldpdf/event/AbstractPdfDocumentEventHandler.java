package org.skaldpdf.event;

public abstract class AbstractPdfDocumentEventHandler {
    protected AbstractPdfDocumentEventHandler() {
    }

    protected abstract void onAcceptedEvent(AbstractPdfDocumentEvent event);

    public final void accept(AbstractPdfDocumentEvent event) {
        onAcceptedEvent(event);
    }
}
