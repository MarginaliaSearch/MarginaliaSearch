package nu.marginalia.loading.domains;

public interface DomainIdRegistry {
    int getDomainId(String domainName);

    void add(String domainName, int id);
}
