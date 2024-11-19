package nu.marginalia.loading.domains;

import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.model.EdgeDomain;

public class DbDomainIdRegistry implements DomainIdRegistry {
    private final DbDomainQueries dbDomainQueries;

    public DbDomainIdRegistry(DbDomainQueries dbDomainQueries) {
        this.dbDomainQueries = dbDomainQueries;
    }

    @Override
    public int getDomainId(String domainName) {
        return dbDomainQueries.getDomainId(new EdgeDomain(domainName));
    }

    @Override
    public void add(String domainName, int id) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
