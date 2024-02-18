package nu.marginalia.ranking.data;

import org.jgrapht.Graph;

import java.util.List;

/** A source for the link graph (or pseudo-link graph)
 * to use when ranking domain. */
public interface GraphSource {

    /** Construct the graph */
    Graph<Integer, ?> getGraph();

    /** Return a list of domain ids for the given domain names.
     *  The function will also accept SQL-style wildcards,
     *  e.g. "%marginalia.nu" will match "marginalia.nu" and "memex.marginalia.nu".
     * <p></p>
     * If multiple wildcards are provided, and overlapping domains are matched,
     * they will be included only once.  The returned list will be sorted in
     * numerical order of the domain IDs.
     */
    List<Integer> domainIds(List<String> domainNameList);
}
