package nu.marginalia.db;


import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Singleton
public class DbDomainQueries {
    private final HikariDataSource dataSource;

    private static final Logger logger = LoggerFactory.getLogger(DbDomainQueries.class);

    private final Cache<EdgeDomain, Integer> domainIdCache = CacheBuilder.newBuilder().maximumSize(10_000).build();
    private final Cache<EdgeDomain, DomainIdWithNode> domainWithNodeCache = CacheBuilder.newBuilder().maximumSize(10_000).build();
    private final Cache<Integer, EdgeDomain> domainNameCache = CacheBuilder.newBuilder().maximumSize(10_000).build();
    private final Cache<String, List<DomainWithNode>> siblingsCache = CacheBuilder.newBuilder().maximumSize(10_000).build();

    @Inject
    public DbDomainQueries(HikariDataSource dataSource)
    {
        this.dataSource = dataSource;
    }


    public Integer getDomainId(EdgeDomain domain) throws NoSuchElementException {
        try {
            return domainIdCache.get(domain, () -> {
                try (var connection = dataSource.getConnection();
                     var stmt = connection.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE DOMAIN_NAME=?")) {

                    stmt.setString(1, domain.toString());
                    var rsp = stmt.executeQuery();
                    if (rsp.next()) {
                        return rsp.getInt(1);
                    }
                }
                catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }

                throw new NoSuchElementException();
            });
        }
        catch (UncheckedExecutionException ex) {
            throw new NoSuchElementException();
        }
        catch (ExecutionException ex) {
            throw new RuntimeException(ex.getCause());
        }
    }


    public DomainIdWithNode getDomainIdWithNode(EdgeDomain domain) throws NoSuchElementException {
        try {
            return domainWithNodeCache.get(domain, () -> {
                try (var connection = dataSource.getConnection();
                     var stmt = connection.prepareStatement("SELECT ID, NODE_AFFINITY FROM EC_DOMAIN WHERE DOMAIN_NAME=?")) {

                    stmt.setString(1, domain.toString());
                    var rsp = stmt.executeQuery();
                    if (rsp.next()) {
                        return new DomainIdWithNode(rsp.getInt(1), rsp.getInt(2));
                    }
                }
                catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }

                throw new NoSuchElementException();
            });
        }
        catch (UncheckedExecutionException ex) {
            throw new NoSuchElementException();
        }
        catch (ExecutionException ex) {
            throw new RuntimeException(ex.getCause());
        }
    }

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
            throw new RuntimeException(ex.getCause());
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public Optional<EdgeDomain> getDomain(int id) {

        EdgeDomain existing = domainNameCache.getIfPresent(id);
        if (existing != null) {
            return Optional.of(existing);
        }

        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement("SELECT DOMAIN_NAME FROM EC_DOMAIN WHERE ID=?")) {
                stmt.setInt(1, id);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    var val = new EdgeDomain(rsp.getString(1));
                    domainNameCache.put(id, val);
                    return Optional.of(val);
                }
                return Optional.empty();
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<DomainWithNode> otherSubdomains(EdgeDomain domain, int cnt) throws ExecutionException {
        String topDomain = domain.topDomain;

        return siblingsCache.get(topDomain, () -> {
            List<DomainWithNode> ret = new ArrayList<>();

            try (var conn = dataSource.getConnection();
                 var stmt = conn.prepareStatement("SELECT DOMAIN_NAME, NODE_AFFINITY FROM EC_DOMAIN WHERE DOMAIN_TOP = ? LIMIT ?")) {
                stmt.setString(1, topDomain);
                stmt.setInt(2, cnt);

                var rs = stmt.executeQuery();
                while (rs.next()) {
                    var sibling = new EdgeDomain(rs.getString(1));

                    if (sibling.equals(domain))
                        continue;

                    ret.add(new DomainWithNode(sibling, rs.getInt(2)));
                }
            } catch (SQLException e) {
                logger.error("Failed to get domain neighbors");
            }
            return ret;
        });

    }

    public record DomainWithNode (EdgeDomain domain, int nodeAffinity) {
        public boolean isIndexed() {
            return nodeAffinity > 0;
        }
    }

    public record DomainIdWithNode (int domainId, int nodeAffinity) { }
}
