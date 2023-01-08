package nu.marginalia.wmsa.edge.integration.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainLink;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWords;


@Data
@AllArgsConstructor
public class BasicDocumentData {
    public final EdgeUrl url;

    public final String title;
    public final String description;
    public int hashCode;

    public final EdgePageWords words;
    public final EdgeDomainLink[] domainLinks;
    public final int wordCount;
}
