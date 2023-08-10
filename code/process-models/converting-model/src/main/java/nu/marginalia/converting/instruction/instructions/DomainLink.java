package nu.marginalia.converting.instruction.instructions;

import nu.marginalia.model.EdgeDomain;

import java.io.Serializable;

public record DomainLink(EdgeDomain from, EdgeDomain to) implements Serializable {
}
