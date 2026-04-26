package nu.marginalia.domainranking.data;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.api.linkgraph.AggregateLinkGraphClient;

/** A source for the inverted link graph,
 * which is the same as the regular graph except
 * the direction of the links have been inverted */
public class InvertedLinkGraphSource extends AbstractGraphSource {
    private final AggregateLinkGraphClient graphClient;

    @Inject
    public InvertedLinkGraphSource(HikariDataSource dataSource, AggregateLinkGraphClient graphClient) {
        super(dataSource);
        this.graphClient = graphClient;
    }

    @Override
    public DomainGraph getGraph() {
        try {
            DomainGraphBuilder builder = DomainGraphBuilder.directed();
            addVertices(builder);

            var allLinks = graphClient.getAllDomainLinks();
            return builder.build(consumer -> {
                var iter = allLinks.iterator();
                while (iter.advance()) {
                    // Invert the edge
                    consumer.accept(iter.dest(), iter.source());
                }
            });
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
