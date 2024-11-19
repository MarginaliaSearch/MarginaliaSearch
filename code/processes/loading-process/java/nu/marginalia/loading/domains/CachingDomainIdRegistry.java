package nu.marginalia.loading.domains;

import java.util.HashMap;
import java.util.Map;

/** Maps domain names to domain ids */
public class CachingDomainIdRegistry implements DomainIdRegistry {
    private final Map<String, Integer> domainIds = new HashMap<>(10_000);

    @Override
    public int getDomainId(String domainName) {
        Integer id = domainIds.get(domainName.toLowerCase());

        if (id == null) {
            // This is a very severe problem
            throw new IllegalStateException("Unknown domain id for domain " + domainName);
        }

        return id;
    }

    @Override
    public void add(String domainName, int id) {
        domainIds.put(domainName, id);
    }

}
