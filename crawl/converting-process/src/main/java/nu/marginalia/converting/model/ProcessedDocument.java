package nu.marginalia.converting.model;

import lombok.ToString;
import nu.marginalia.model.crawl.EdgePageDocumentFlags;
import nu.marginalia.model.crawl.EdgeUrlState;
import nu.marginalia.model.EdgeUrl;

import java.util.OptionalDouble;

@ToString
public class ProcessedDocument {
    public EdgeUrl url;

    public ProcessedDocumentDetails details;
    public DocumentKeywordsBuilder words;

    public EdgeUrlState state;
    public String stateReason;

    public long lshHash;

    public boolean isOk() {
        return EdgeUrlState.OK == state;
    }

    public boolean isProcessedFully() {
        if (!isOk())
            return false;

        if (details == null)
            return false;

        return !details.metadata.hasFlag(EdgePageDocumentFlags.Simple);
    }

    public OptionalDouble quality() {
        if (details != null) {
            return OptionalDouble.of(details.quality);
        }
        return OptionalDouble.empty();
    }
}
