package nu.marginalia.wmsa.edge.converting.model;

import lombok.ToString;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWords;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlState;

import java.util.OptionalDouble;

@ToString
public class ProcessedDocument {
    public EdgeUrl url;

    public ProcessedDocumentDetails details;
    public EdgePageWords words;

    public EdgeUrlState state;
    public String stateReason;

    public boolean isOk() {
        return EdgeUrlState.OK == state;
    }

    public OptionalDouble quality() {
        if (details != null) {
            return OptionalDouble.of(details.quality);
        }
        return OptionalDouble.empty();
    }
}
