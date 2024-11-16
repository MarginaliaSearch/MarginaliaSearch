package nu.marginalia.ranking.domains.data;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.api.linkgraph.AggregateLinkGraphClient;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/** A source for the regular link graph. */
public class LinkGraphSource extends AbstractGraphSource {
    private final AggregateLinkGraphClient graphClient;

    @Inject
    public LinkGraphSource(HikariDataSource dataSource, AggregateLinkGraphClient graphClient) {
        super(dataSource);
        this.graphClient = graphClient;
    }

    @Override
    public Graph<Integer, ?> getGraph() {
        try {
            Graph<Integer, ?> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

            addVertices(graph);

            var allLinks = graphClient.getAllDomainLinks();
            var iter = allLinks.iterator();
            while (iter.advance()) {
                if (!graph.containsVertex(iter.dest())) {
                    continue;
                }
                if (!graph.containsVertex(iter.source())) {
                    continue;
                }

                graph.addEdge(iter.source(), iter.dest());
            }

            return graph;

        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
