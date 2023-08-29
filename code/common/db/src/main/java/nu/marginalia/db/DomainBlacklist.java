package nu.marginalia.db;

import com.google.inject.ImplementedBy;
import gnu.trove.set.hash.TIntHashSet;

@ImplementedBy(DomainBlacklistImpl.class)
public interface DomainBlacklist {
    boolean isBlacklisted(int domainId);
    default TIntHashSet getSpamDomains() {
        return new TIntHashSet();
    }
}
