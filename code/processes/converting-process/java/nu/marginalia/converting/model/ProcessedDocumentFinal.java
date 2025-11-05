package nu.marginalia.converting.model;

import nu.marginalia.keyword.model.DocumentKeywords;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.UrlIndexingState;

import javax.annotation.Nullable;
import java.util.OptionalDouble;

public class ProcessedDocumentFinal {
    public EdgeUrl url;

    @Nullable
    public ProcessedDocumentDetails details;
    @Nullable
    public DocumentKeywords words;

    public UrlIndexingState state;
    public String stateReason;

    public ProcessedDocumentFinal(ProcessedDocument processedDocument) {
        url = processedDocument.url;
        details = processedDocument.details;
        if (processedDocument.words != null) {
            words = processedDocument.words.build();
        }
        state = processedDocument.state;
        stateReason = processedDocument.stateReason;
    }

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

    public EdgeUrl getUrl() {
        return this.url;
    }

    @Nullable
    public ProcessedDocumentDetails getDetails() {
        return this.details;
    }

    @Nullable
    public DocumentKeywords getWords() {
        return this.words;
    }

    public UrlIndexingState getState() {
        return this.state;
    }

    public String getStateReason() {
        return this.stateReason;
    }

    public String toString() {
        return "ProcessedDocumentFinal(url=" + this.getUrl() + ", details=" + this.getDetails() + ", words=" + this.getWords() + ", state=" + this.getState() + ", stateReason=" + this.getStateReason() + ")";
    }
}
