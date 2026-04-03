package nu.marginalia.api.model;

public record ApiSimilarDomain(
        String domain,
        double relatedness,
        double ranking,
        String linkType,
        boolean indexed,
        boolean active
) {}