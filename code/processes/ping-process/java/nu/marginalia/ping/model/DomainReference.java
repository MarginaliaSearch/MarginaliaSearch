package nu.marginalia.ping.model;

import nu.marginalia.model.EdgeDomain;

public record DomainReference(int domainId, int nodeId, String domainName) {
    public EdgeDomain asEdgeDomain() {
        return new EdgeDomain(domainName);
    }

}
