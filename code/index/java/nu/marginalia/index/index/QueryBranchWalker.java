package nu.marginalia.index.index;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/** Helper class for index query construction */
public class QueryBranchWalker {
    private static final Logger logger = LoggerFactory.getLogger(QueryBranchWalker.class);
    public final long[] priorityOrder;
    public final List<LongSet> paths;
    public final long termId;

    private QueryBranchWalker(long[] priorityOrder, List<LongSet> paths, long termId) {
        this.priorityOrder = priorityOrder;
        this.paths = paths;
        this.termId = termId;
    }

    public boolean atEnd() {
        return priorityOrder.length == 0;
    }

    /** Group the provided paths by the lowest termId they contain per the provided priorityOrder,
     * into a list of QueryBranchWalkers.  This can be performed iteratively on the resultant QBW:s
     * to traverse the tree via the next() method.
     * <p></p>
     * The paths can be extracted through the {@link nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates CompiledQueryAggregates}
     * queriesAggregate method.
     */
    public static List<QueryBranchWalker> create(long[] priorityOrder, List<LongSet> paths) {
        if (paths.isEmpty())
            return List.of();

        List<QueryBranchWalker> ret = new ArrayList<>();
        List<LongSet> remainingPaths = new LinkedList<>(paths);
        remainingPaths.removeIf(LongSet::isEmpty);

        List<LongSet> pathsForPrio = new ArrayList<>();

        for (int i = 0; i < priorityOrder.length; i++) {
            long termId = priorityOrder[i];

            var it = remainingPaths.iterator();

            while (it.hasNext()) {
                var path = it.next();

                if (path.contains(termId)) {
                    // Remove the current termId from the path
                    path.remove(termId);

                    // Add it to the set of paths associated with the termId
                    pathsForPrio.add(path);

                    // Remove it from consideration
                    it.remove();
                }
            }

            if (!pathsForPrio.isEmpty()) {
                long[] newPrios = keepRelevantPriorities(priorityOrder, pathsForPrio);
                ret.add(new QueryBranchWalker(newPrios, new ArrayList<>(pathsForPrio), termId));
                pathsForPrio.clear();
            }
        }

        // This happens if the priorityOrder array doesn't contain all items in the paths,
        // in practice only when an index doesn't contain all the search terms, so we can just
        // skip those paths
        if (!remainingPaths.isEmpty()) {
            logger.debug("Dropping: {}", remainingPaths);
        }

        return ret;
    }

    /** From the provided priorityOrder array, keep the elements that are present in any set in paths */
    private static long[] keepRelevantPriorities(long[] priorityOrder, List<LongSet> paths) {
        LongArrayList remainingPrios = new LongArrayList(paths.size());

        // these sets are typically very small so array set is a good choice
        LongSet allElements = new LongArraySet(priorityOrder.length);
        for (var path : paths) {
            allElements.addAll(path);
        }

        for (var p : priorityOrder) {
            if (allElements.contains(p))
                remainingPrios.add(p);
        }

        return remainingPrios.elements();
    }

    /** Convenience method that applies the create() method
     * to the priority order and paths associated with this instance */
    public List<QueryBranchWalker> next() {
        return create(priorityOrder, paths);
    }

}
