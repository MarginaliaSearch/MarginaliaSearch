package nu.marginalia.db;


import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.id.EdgeId;

import java.util.NoSuchElementException;
import java.util.Optional;

@Singleton
public class DbDomainQueries {
    private final HikariDataSource dataSource;

    private final Cache<EdgeDomain, EdgeId<EdgeDomain>> domainIdCache = CacheBuilder.newBuilder().maximumSize(10_000).build();

    @Inject
    public DbDomainQueries(HikariDataSource dataSource)
    {
        this.dataSource = dataSource;
    }


    @SneakyThrows
    public EdgeId<EdgeDomain> getDomainId(EdgeDomain domain) {
        try (var connection = dataSource.getConnection()) {

            return domainIdCache.get(domain, () -> {
                try (var stmt = connection.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE DOMAIN_NAME=?")) {
                    stmt.setString(1, domain.toString());
                    var rsp = stmt.executeQuery();
                    if (rsp.next()) {
                        return new EdgeId<>(rsp.getInt(1));
                    }
                }
                throw new NoSuchElementException();
            });
        }
        catch (UncheckedExecutionException ex) {
            throw ex.getCause();
        }
    }

    @SneakyThrows
    public Optional<EdgeId<EdgeDomain>> tryGetDomainId(EdgeDomain domain) {

        var maybe = Optional.ofNullable(domainIdCache.getIfPresent(domain));

        if (maybe.isPresent())
            return maybe;

        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE DOMAIN_NAME=?")) {
                stmt.setString(1, domain.toString());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    var id = new EdgeId<EdgeDomain>(rsp.getInt(1));

                    domainIdCache.put(domain, id);
                    return Optional.of(id);
                }
            }
            return Optional.empty();
        }
        catch (UncheckedExecutionException ex) {
            return Optional.empty();
        }
    }

    @SneakyThrows
    public Optional<EdgeDomain> getDomain(EdgeId<EdgeDomain> id) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT DOMAIN_NAME FROM EC_DOMAIN WHERE ID=?")) {
                stmt.setInt(1, id.id());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return Optional.of(new EdgeDomain(rsp.getString(1)));
                }
                return Optional.empty();
            }
        }
    }
}
