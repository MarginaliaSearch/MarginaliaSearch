package nu.marginalia.api.searchquery.model.compiled;

import it.unimi.dsi.fastutil.ints.IntList;

import java.util.List;

public class CompiledQueryTopology {
    public final CqExpression root;
    public final List<IntList> paths;

    public CompiledQueryTopology(CqExpression root, List<IntList> paths) {
        this.root = root;
        this.paths = paths;
    }
}
