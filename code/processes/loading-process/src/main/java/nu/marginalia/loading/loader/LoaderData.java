package nu.marginalia.loading.loader;

import gnu.trove.map.hash.TObjectIntHashMap;
import nu.marginalia.model.EdgeDomain;

public class LoaderData {

    private final TObjectIntHashMap<EdgeDomain> domainIds;
    private EdgeDomain targetDomain;
    public final int sizeHint;
    private int targetDomainId = -1;

    public LoaderData(int sizeHint) {
        domainIds = new TObjectIntHashMap<>(10);
        this.sizeHint = sizeHint;
    }

    public void setTargetDomain(EdgeDomain domain) {
        this.targetDomain = domain;
    }
    public EdgeDomain getTargetDomain() {
        return targetDomain;
    }
    public int getTargetDomainId() {
        if (targetDomainId < 0)
            targetDomainId = domainIds.get(targetDomain);
        return targetDomainId;
    }

    public void addDomain(EdgeDomain domain, int id) {
        domainIds.put(domain, id);
    }

    public int getDomainId(EdgeDomain domain) {
        return domainIds.get(domain);
    }
}
