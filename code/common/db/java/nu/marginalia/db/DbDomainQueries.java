package nu.marginalia.db;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import nu.marginalia.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Singleton
public class DbDomainQueries {
    private final HikariDataSource dataSource;

    private static final Logger logger = LoggerFactory.getLogger(DbDomainQueries.class);

    private volatile DomainData domainData = null;
    private volatile SiblingData siblingData = null;

    @Inject
    public DbDomainQueries(HikariDataSource dataSource)
    {
        this.dataSource = dataSource;
        Thread.ofPlatform().daemon().start(this::updateData);
    }

    private void updateData() {
        while (true) {
            try {
                synchronized (this) {
                    domainData = new DomainData();

                    notifyAll();

                    siblingData = new SiblingData(domainData);

                    notifyAll();
                }

                // Add jitter to avoid thundering herd
                TimeUnit.MINUTES.sleep(60 + ThreadLocalRandom.current().nextInt(0, 120));
            }
            catch (SQLException ex) {
                logger.error("SQL error", ex);
            }
            catch (InterruptedException ex) {
                logger.error("updateData() was interrupted", ex);
                return;
            }
        }
    }


    record Entry(String domainName, int domainId, int nodeAffinity) { }

    private class DomainData {
        private final Map<String, Entry> byDomain;
        private final Int2ObjectMap<Entry> byId;

        private DomainData() throws SQLException {

            final Map<String, Entry> byDomain = new HashMap<>(1_000_000);
            final Int2ObjectOpenHashMap<Entry> byId = new Int2ObjectOpenHashMap<>(1_000_000);

            try (var connection = dataSource.getConnection();
                 var idStmt = connection.prepareStatement("SELECT ID, DOMAIN_NAME, NODE_AFFINITY FROM EC_DOMAIN"))
            {

                var rsp = idStmt.executeQuery();

                while (rsp.next()) {
                    int domainId = rsp.getInt(1);
                    String domainName = rsp.getString(2).toLowerCase();
                    int nodeAffinity = rsp.getInt(1);

                    Entry entry = new Entry(domainName, domainId, nodeAffinity);
                    byDomain.put(domainName, entry);
                    byId.put(domainId, entry);
                }

            }

            this.byDomain = Collections.unmodifiableMap(byDomain);
            this.byId = Int2ObjectMaps.unmodifiable(byId);
        }
    }

    private class SiblingData {
        private final Map<String, List<Entry>> byDomainTop;

        private SiblingData(DomainData domainData) throws SQLException {

            final Map<String, List<Entry>> byDomainTop = new HashMap<>(1_000_000);

            try (var connection = dataSource.getConnection();
                 var topStmt = connection.prepareStatement("SELECT ID, DOMAIN_TOP FROM EC_DOMAIN")
            ) {

                var rsp = topStmt.executeQuery();
                while (rsp.next()) {
                    int domainId = rsp.getInt(1);
                    String domainTop = rsp.getString(2);

                    byDomainTop.computeIfAbsent(domainTop, (v) -> new ArrayList<>()).add(domainData.byId.get(domainId));
                }
            }

            this.byDomainTop = Collections.unmodifiableMap(byDomainTop);
        }
    }

    private DomainData getDomainData() {
        // Block during initialization

        if (domainData == null) {
            synchronized (this) {
                while (domainData == null) {
                    try {
                        wait(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        return domainData;
    }

    private SiblingData getSiblingData() {
        // Block during initialization

        if (siblingData == null) {
            synchronized (this) {
                while (siblingData == null) {
                    try {
                        wait(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        return siblingData;
    }

    public Integer getDomainId(EdgeDomain domain) throws NoSuchElementException {
        Entry entry = getDomainData().byDomain.get(domain.toString());

        if (entry == null)
            throw new NoSuchElementException();

        return entry.domainId;
    }


    public DomainIdWithNode getDomainIdWithNode(EdgeDomain domain) throws NoSuchElementException {
        Entry entry = getDomainData().byDomain.get(domain.toString());

        if (entry == null)
            throw new NoSuchElementException();

        return new DomainIdWithNode(entry.domainId, entry.nodeAffinity);
    }

    public OptionalInt tryGetDomainId(EdgeDomain domain) {
        Entry entry = getDomainData().byDomain.get(domain.toString());

        if (entry == null)
            return OptionalInt.empty();

        return OptionalInt.of(entry.domainId);
    }

    public Optional<EdgeDomain> getDomain(int id) {

        return Optional.ofNullable(getDomainData().byId.get(id))
                .map(Entry::domainName)
                .map(EdgeDomain::new);

    }

    public List<DomainWithNode> otherSubdomains(EdgeDomain domain, int cnt) throws ExecutionException {
        String topDomain = domain.topDomain;

        List<Entry> entries = getSiblingData().byDomainTop.get(topDomain);
        if (null == entries)
            return List.of();

        return entries.stream().map(entry -> new DomainWithNode(new EdgeDomain(entry.domainName), entry.nodeAffinity)).toList();
    }

    public record DomainWithNode (EdgeDomain domain, int nodeAffinity) {
        public boolean isIndexed() {
            return nodeAffinity > 0;
        }
    }

    public record DomainIdWithNode (int domainId, int nodeAffinity) { }
}
