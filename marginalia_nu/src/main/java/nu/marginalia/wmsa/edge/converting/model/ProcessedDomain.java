package nu.marginalia.wmsa.edge.converting.model;

import lombok.ToString;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

@ToString
public class ProcessedDomain {
    public EdgeDomain domain;

    public List<ProcessedDocument> documents;
    public EdgeDomainIndexingState state;
    public EdgeDomain redirect;
    public String ip;

    public OptionalDouble averageQuality() {
        if (documents == null) {
            return OptionalDouble.empty();
        }
        return documents.stream()
                .map(ProcessedDocument::quality)
                .filter(OptionalDouble::isPresent)
                .mapToDouble(OptionalDouble::getAsDouble)
                .average();
    }

    public int size() {
        return Optional.ofNullable(documents).map(List::size).orElse(1);
    }
}
