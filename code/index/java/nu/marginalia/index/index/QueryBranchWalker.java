package nu.marginalia.index.index;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

class QueryBranchWalker {
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

    public static List<QueryBranchWalker> create(long[] priorityOrder, List<LongSet> paths) {

        List<QueryBranchWalker> ret = new ArrayList<>();
        List<LongSet> remainingPaths = new LinkedList<>(paths);

        remainingPaths.removeIf(LongSet::isEmpty);

        for (int i = 0; i < priorityOrder.length; i++) {
            long prio = priorityOrder[i];

            var it = remainingPaths.iterator();
            List<LongSet> pathsForPrio = new ArrayList<>();

            while (it.hasNext()) {
                var path = it.next();

                if (path.contains(prio)) {
                    path.remove(prio);
                    pathsForPrio.add(path);
                    it.remove();
                }
            }

            if (!pathsForPrio.isEmpty()) {
                LongArrayList remainingPrios = new LongArrayList(pathsForPrio.size());

                for (var p : priorityOrder) {
                    for (var path : pathsForPrio) {
                        if (path.contains(p)) {
                            remainingPrios.add(p);
                            break;
                        }
                    }
                }

                ret.add(new QueryBranchWalker(remainingPrios.elements(), pathsForPrio, prio));
            }
        }

        if (!remainingPaths.isEmpty()) {
            System.out.println("Dropping: " + remainingPaths);
        }

        return ret;
    }

    public List<QueryBranchWalker> next() {
        if (atEnd())
            return List.of();

        return create(priorityOrder, paths);
    }

}
