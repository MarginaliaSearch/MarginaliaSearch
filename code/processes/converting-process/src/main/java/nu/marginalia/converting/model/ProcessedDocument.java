package nu.marginalia.converting.model;

import lombok.ToString;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.model.crawl.UrlIndexingState;
import nu.marginalia.model.EdgeUrl;

import java.util.OptionalDouble;

@ToString
public class ProcessedDocument {
    public EdgeUrl url;

    public ProcessedDocumentDetails details;
    public DocumentKeywordsBuilder words;

    public UrlIndexingState state;
    public String stateReason;

    public boolean isOk() {
        return UrlIndexingState.OK == state;
    }

    public boolean isProcessedFully() {
        if (!isOk())
            return false;

        if (details == null)
            return false;

        return true;
    }

    public OptionalDouble quality() {
        if (details != null) {
            return OptionalDouble.of(details.quality);
        }
        return OptionalDouble.empty();
    }
}
