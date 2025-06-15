package nu.marginalia.ping.model;

public sealed interface RootDomainReference {
    record ByIdAndName(long id, String name) implements RootDomainReference { }
    record ByName(String name) implements RootDomainReference { }
}
