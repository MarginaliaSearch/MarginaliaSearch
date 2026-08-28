package nu.marginalia.api.searchquery.model.compiled;

import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntImmutableList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public class CompiledQueryTopology {
    public final List<IntList> paths;
    public final int[] variantClasses;

    public CompiledQueryTopology(List<IntList> paths, int[] variantClasses) {
        this.paths = normalizePaths(paths);
        this.variantClasses = variantClasses;
    }

    /** Share the topology of another query */
    protected CompiledQueryTopology(CompiledQueryTopology other) {
        this.paths = other.paths;
        this.variantClasses = other.variantClasses;
    }


    // Terms should only appear one time in a path, and should be unique
    private static List<IntList> normalizePaths(List<IntList> paths) {
        Set<IntList> ret = new LinkedHashSet<>(paths.size());

        for (IntList path : paths) {
            if (path.isEmpty())
                continue;

            ret.add(new IntImmutableList(new IntAVLTreeSet(path)));
        }

        return List.copyOf(ret);
    }
}
