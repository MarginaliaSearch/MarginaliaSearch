package nu.marginalia.model.crawl;

import lombok.*;
import nu.marginalia.model.EdgeDomain;

@AllArgsConstructor @EqualsAndHashCode @Getter @Setter @Builder @ToString
public class EdgeDomainLink {
    public final EdgeDomain source;
    public final EdgeDomain destination;
}
