package nu.marginalia.ranking.data;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.query.client.QueryClient;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/** A source for the regular link graph. */
public class LinkGraphSource extends AbstractGraphSource {
    private final QueryClient queryClient;

    @Inject
    public LinkGraphSource(HikariDataSource dataSource, QueryClient queryClient) {
        super(dataSource);
        this.queryClient = queryClient;
    }

    @SneakyThrows
    @Override
    public Graph<Integer, ?> getGraph() {
        Graph<Integer, ?> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        addVertices(graph);

        var allLinks = queryClient.getAllDomainLinks();
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
}
