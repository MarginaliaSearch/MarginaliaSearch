package nu.marginalia.wmsa.edge.model.crawl;

import lombok.*;
import nu.marginalia.wmsa.edge.model.EdgeDomain;

@AllArgsConstructor @EqualsAndHashCode @Getter @Setter @Builder
public class EdgeRobotsTxt {
    public final EdgeDomain domain;
    public final String robotsTxt;
}
