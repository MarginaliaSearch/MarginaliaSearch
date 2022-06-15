package nu.marginalia.wmsa.edge.search.siteinfo;

import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.search.model.DomainInformation;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

@Singleton
public class DomainInformationService {

    private EdgeDataStoreDao dataStore;

    @Inject
    public DomainInformationService(EdgeDataStoreDao dataStore) {
        this.dataStore = dataStore;
    }


    public Optional<DomainInformation> domainInfo(String site) {

        EdgeId<EdgeDomain> domainId = getDomainFromPartial(site);
        if (domainId == null) {
            return Optional.empty();
        }
        EdgeDomain domain = dataStore.getDomain(domainId);

        boolean blacklisted = dataStore.isBlacklisted(domain);
        int pagesKnown = dataStore.getPagesKnown(domainId);
        int pagesVisited = dataStore.getPagesVisited(domainId);
        int pagesIndexed = dataStore.getPagesIndexed(domainId);
        int incomingLinks = dataStore.getIncomingLinks(domainId);
        int outboundLinks = dataStore.getOutboundLinks(domainId);
        double rank = Math.round(10000.0*(1.0-dataStore.getRank(domainId)))/100;
        EdgeDomainIndexingState state = dataStore.getDomainState(domainId);
        double nominalQuality = Math.round(100*100*Math.exp(dataStore.getDomainQuality(domainId)))/100.;
        List<EdgeDomain> linkingDomains = dataStore.getLinkingDomains(domainId);

        return Optional.of(new DomainInformation(domain, blacklisted, pagesKnown, pagesVisited, pagesIndexed, incomingLinks, outboundLinks, nominalQuality, rank, state, linkingDomains));
    }

    private EdgeId<EdgeDomain> getDomainFromPartial(String site) {
        try {
            return dataStore.getDomainId(new EdgeDomain(site));
        }
        catch (Exception ex) {
            try {
                return dataStore.getDomainId(new EdgeDomain(site));
            }
            catch (Exception ex2) {
                return null;
            }
        }

    }
}
