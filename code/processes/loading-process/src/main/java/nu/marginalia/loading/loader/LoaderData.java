package nu.marginalia.loading.loader;

import nu.marginalia.model.EdgeDomain;

public class LoaderData {

    private final OldDomains oldDomains;
    private EdgeDomain targetDomain;
    public final int sizeHint;
    private int targetDomainId = -1;

    public LoaderData(OldDomains oldDomains, int sizeHint) {
        this.oldDomains = oldDomains;
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
            targetDomainId = oldDomains.getId(targetDomain);
        return targetDomainId;
    }

    public void addDomain(EdgeDomain domain, int id) {
        oldDomains.add(domain, id);
    }

    public int getDomainId(EdgeDomain domain) {
        return oldDomains.getId(domain);
    }
}
