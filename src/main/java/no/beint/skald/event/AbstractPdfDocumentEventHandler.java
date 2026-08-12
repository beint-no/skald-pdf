package no.beint.skald.event;

public abstract class AbstractPdfDocumentEventHandler {
    protected abstract void onAcceptedEvent(AbstractPdfDocumentEvent event);

    public final void accept(AbstractPdfDocumentEvent event) {
        onAcceptedEvent(event);
    }
}
