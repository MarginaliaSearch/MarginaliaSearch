package nu.marginalia.ping.model;

public sealed interface RootDomainReference {
    record ById(long id) implements RootDomainReference { }
    record ByName(String name) implements RootDomainReference { }
}
