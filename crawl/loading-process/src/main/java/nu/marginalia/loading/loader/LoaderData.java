package nu.marginalia.loading.loader;

import gnu.trove.map.hash.TObjectIntHashMap;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;

public class LoaderData {

    private final TObjectIntHashMap<EdgeUrl> urlIds;
    private final TObjectIntHashMap<EdgeDomain> domainIds;
    private EdgeDomain targetDomain;
    public final int sizeHint;

    public LoaderData(int sizeHint) {
        urlIds = new TObjectIntHashMap<>(sizeHint+1);
        domainIds = new TObjectIntHashMap<>(10);
        this.sizeHint = sizeHint;
    }

    public void setTargetDomain(EdgeDomain domain) {
        this.targetDomain = domain;
    }
    public EdgeDomain getTargetDomain() {
        return targetDomain;
    }


    public void addDomain(EdgeDomain domain, int id) {
        domainIds.put(domain, id);
    }

    public void addUrl(EdgeUrl url, int id) {
        urlIds.put(url, id);
    }

    public int getUrlId(EdgeUrl url) {
        return urlIds.get(url);
    }

    public int getDomainId(EdgeDomain domain) {
        return domainIds.get(domain);
    }
}
