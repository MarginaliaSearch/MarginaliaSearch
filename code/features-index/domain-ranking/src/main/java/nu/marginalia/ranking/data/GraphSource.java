package nu.marginalia.ranking.data;

import org.jgrapht.Graph;

import java.util.List;

/** A source for the link graph (or pseudo-link graph)
 * to use when ranking domain. */
public interface GraphSource {

    /** Construct the graph */
    Graph<Integer, ?> getGraph();

    List<Integer> domainIds(List<String> domainNameList);
}
