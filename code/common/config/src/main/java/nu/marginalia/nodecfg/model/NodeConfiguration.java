package nu.marginalia.nodecfg.model;

public record NodeConfiguration(int node,
                                String description,
                                boolean acceptQueries,
                                boolean disabled
                                )
{
}
