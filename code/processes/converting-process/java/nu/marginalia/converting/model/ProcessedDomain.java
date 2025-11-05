package nu.marginalia.converting.model;

import nu.marginalia.converting.writer.ConverterBatchWritableIf;
import nu.marginalia.converting.writer.ConverterBatchWriter;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.crawl.DomainIndexingState;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class ProcessedDomain implements ConverterBatchWritableIf {
    public EdgeDomain domain;

    public List<ProcessedDocumentFinal> documents;
    public DomainIndexingState state;
    public EdgeDomain redirect;
    public String ip;


    /**
     * Used by the sideloader to give advice on how many documents are crawled
     * without actually having to count (which would take forever)
     */
    @Nullable
    public Integer sizeloadSizeAdvice;

    public int size() {
        return Optional.ofNullable(documents).map(List::size).orElse(1);
    }

    @Override
    public void write(ConverterBatchWriter writer) throws IOException {
        writer.writeProcessedDomain(this);
    }

    @Override
    public String id() {
        return domain.toString();
    }

    @Override
    public void close() {
    }

    public String toString() {
        return "ProcessedDomain(domain=" + this.domain + ", documents=" + this.documents + ", state=" + this.state + ", redirect=" + this.redirect + ", ip=" + this.ip + ", sizeloadSizeAdvice=" + this.sizeloadSizeAdvice + ")";
    }
}
