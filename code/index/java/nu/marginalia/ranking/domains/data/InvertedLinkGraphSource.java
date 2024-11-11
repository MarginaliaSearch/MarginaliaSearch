package nu.marginalia.ranking.domains.data;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.api.linkgraph.AggregateLinkGraphClient;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;


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

                // Invert the edge
                graph.addEdge(iter.dest(), iter.source());
            }

            return graph;
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
