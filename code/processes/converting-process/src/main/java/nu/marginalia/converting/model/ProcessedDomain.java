package nu.marginalia.converting.model;

import lombok.ToString;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.crawl.DomainIndexingState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

@ToString
public class ProcessedDomain {
    public EdgeDomain domain;

    public List<ProcessedDocument> documents;
    public DomainIndexingState state;
    public EdgeDomain redirect;
    public String ip;


    /** Used by the sideloader to give advice on how many documents are crawled
     * without actually having to count (which would take forever) */
    @Nullable
    public Integer sizeloadSizeAdvice;

    public int size() {
        return Optional.ofNullable(documents).map(List::size).orElse(1);
    }
}
