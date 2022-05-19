package nu.marginalia.wmsa.edge.model.crawl;

import lombok.*;
import nu.marginalia.wmsa.edge.model.EdgeDomain;

@AllArgsConstructor @EqualsAndHashCode @Getter @Setter @Builder @ToString
public class EdgeDomainLink {
    public final EdgeDomain source;
    public final EdgeDomain destination;
}
