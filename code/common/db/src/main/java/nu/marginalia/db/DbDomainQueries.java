package nu.marginalia.db;


import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.model.EdgeDomain;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalInt;

@Singleton
public class DbDomainQueries {
    private final HikariDataSource dataSource;

    private final Cache<EdgeDomain, Integer> domainIdCache = CacheBuilder.newBuilder().maximumSize(10_000).build();

    @Inject
    public DbDomainQueries(HikariDataSource dataSource)
    {
        this.dataSource = dataSource;
    }


    @SneakyThrows
    public Integer getDomainId(EdgeDomain domain) {
        try (var connection = dataSource.getConnection()) {

            return domainIdCache.get(domain, () -> {
                try (var stmt = connection.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE DOMAIN_NAME=?")) {
                    stmt.setString(1, domain.toString());
                    var rsp = stmt.executeQuery();
                    if (rsp.next()) {
                        return rsp.getInt(1);
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
    public OptionalInt tryGetDomainId(EdgeDomain domain) {

        Integer maybeId = domainIdCache.getIfPresent(domain);
        if (maybeId != null) {
            return OptionalInt.of(maybeId);
        }

        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE DOMAIN_NAME=?")) {
                stmt.setString(1, domain.toString());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    var id = rsp.getInt(1);

                    domainIdCache.put(domain, id);
                    return OptionalInt.of(id);
                }
            }
            return OptionalInt.empty();
        }
        catch (UncheckedExecutionException ex) {
            return OptionalInt.empty();
        }
    }

    @SneakyThrows
    public Optional<EdgeDomain> getDomain(int id) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT DOMAIN_NAME FROM EC_DOMAIN WHERE ID=?")) {
                stmt.setInt(1, id);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return Optional.of(new EdgeDomain(rsp.getString(1)));
                }
                return Optional.empty();
            }
        }
    }
}
