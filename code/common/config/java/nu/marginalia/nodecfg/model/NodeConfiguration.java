package nu.marginalia.nodecfg.model;

public record NodeConfiguration(int node,
                                String description,
                                boolean acceptQueries,
                                boolean autoClean,
                                boolean includeInPrecession,
                                boolean keepWarcs,
                                NodeProfile profile,
                                boolean disabled
                                )
{
    public int getId() {
        return node;
    }
}
