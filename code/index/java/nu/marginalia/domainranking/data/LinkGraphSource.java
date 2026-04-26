package nu.marginalia.domainranking.data;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.api.linkgraph.AggregateLinkGraphClient;

/** A source for the regular link graph. */
public class LinkGraphSource extends AbstractGraphSource {
    private final AggregateLinkGraphClient graphClient;

    @Inject
    public LinkGraphSource(HikariDataSource dataSource, AggregateLinkGraphClient graphClient) {
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
                    consumer.accept(iter.source(), iter.dest());
                }
            });
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
