package nu.marginalia.wmsa.edge.data.dao.task;

import com.google.inject.ImplementedBy;
import gnu.trove.set.hash.TIntHashSet;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;

@ImplementedBy(EdgeDomainBlacklistImpl.class)
public interface EdgeDomainBlacklist {
    boolean isBlacklisted(int domainId);
    default boolean isBlacklisted(EdgeId<EdgeDomain> domainId) {
        return isBlacklisted(domainId.id());
    }
    default TIntHashSet getSpamDomains() {
        return new TIntHashSet();
    }
}
