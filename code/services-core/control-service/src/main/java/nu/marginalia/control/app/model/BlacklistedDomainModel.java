package nu.marginalia.control.app.model;

import nu.marginalia.model.EdgeDomain;

public record BlacklistedDomainModel(EdgeDomain domain, String comment) {
}
