package nu.marginalia.wmsa.edge.search.model;

import nu.marginalia.wmsa.edge.model.EdgeUrl;

public record BrowseResult (EdgeUrl url, int domainId, double relatedness) {

    public String domainHash() {
        var domain = url.domain;
        if ("www".equals(domain.subDomain)) {
            return domain.domain;
        }
        return domain.toString();
    }
}
