package nu.marginalia.api.model;

import java.util.List;

public record ApiSimilarDomains(
        String domain,
        List<ApiSimilarDomain> results
) {}