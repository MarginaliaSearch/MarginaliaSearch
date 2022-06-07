package nu.marginalia.wmsa.edge.data.dao;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.search.EdgePageScoreAdjustment;
import nu.marginalia.wmsa.edge.model.search.EdgeUrlDetails;
import nu.marginalia.wmsa.edge.search.model.BrowseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class EdgeDataStoreDaoImpl implements EdgeDataStoreDao {
    private final HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Cache<EdgeUrl, EdgeId<EdgeUrl>> urlIdCache = CacheBuilder.newBuilder().maximumSize(100_000).build();
    private final Cache<EdgeDomain, EdgeId<EdgeDomain>> domainIdCache = CacheBuilder.newBuilder().maximumSize(10_000).build();

    private static final String DEFAULT_PROTOCOL = "http";
    public static double QUALITY_LOWER_BOUND_CUTOFF = -15.;
    @Inject
    public EdgeDataStoreDaoImpl(HikariDataSource dataSource)
    {
        this.dataSource = dataSource;
    }


    public synchronized void clearCaches()
    {
        urlIdCache.invalidateAll();
        domainIdCache.invalidateAll();
    }

    @SneakyThrows
    @Override
    public boolean isBlacklisted(EdgeDomain domain) {

        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement("SELECT ID FROM EC_DOMAIN_BLACKLIST WHERE URL_DOMAIN=?")) {
                stmt.setString(1, domain.domain);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return true;
                } else {
                    return false;
                }
            }
        }
    }

    @SneakyThrows
    @Override
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

    private <T> String idList(List<EdgeId<T>> ids) {
        StringJoiner j = new StringJoiner(",", "(", ")");
        for (var id : ids) {
            j.add(Integer.toString(id.getId()));
        }
        return j.toString();
    }

    @SneakyThrows
    @Override
    public List<EdgeUrlDetails> getUrlDetailsMulti(List<EdgeId<EdgeUrl>> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<EdgeUrlDetails> result = new ArrayList<>(ids.size());

        try (var connection = dataSource.getConnection()) {

            String idString = idList(ids);

            try (var stmt = connection.prepareStatement(
                    """
                            SELECT ID, URL, 
                                    TITLE, DESCRIPTION,
                                    WORDS_TOTAL, FORMAT, FEATURES, 
                                    IP, DOMAIN_STATE, DATA_HASH
                                    FROM EC_URL_VIEW WHERE ID IN
                            """ + idString)) {
//                    "SELECT ID,URL_PROTO,URL_DOMAIN,URL_PORT,URL_PATH,TITLE,DESCRIPTION,URL_QUALITY_MEASURE,DOMAIN_QUALITY_MEASURE,IFNULL(EC_DOMAIN_LINK_AGGREGATE.LINKS,1),WORDS_TOTAL,FORMAT,FEATURES,\"\",QUALITY_RAW,DOMAIN_STATE,DATA_HASH FROM EC_URL_VIEW LEFT JOIN EC_DOMAIN_LINK_AGGREGATE ON EC_DOMAIN_LINK_AGGREGATE.DOMAIN_ID=EC_URL_VIEW.DOMAIN_ID WHERE ID IN " + idString)) {
                stmt.setFetchSize(ids.size());

                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    EdgeUrl url = new EdgeUrl(rsp.getString(2));
                    var val = new EdgeUrlDetails(rsp.getInt(1), url,
                            rsp.getString(3), // title
                            rsp.getString(4), // description
                            -5, // quality
                            rsp.getInt(5), // wordsTotal
                            rsp.getString(6), // foramt
                            rsp.getInt(7), // features
                            rsp.getString(8), // ip
                            EdgeDomainIndexingState.valueOf(rsp.getString(9)), // domainState
                            rsp.getInt(10), // dataHash
                            EdgePageScoreAdjustment.zero(), // urlQualityAdjustment
                            Integer.MAX_VALUE, // rankingId
                            Double.MAX_VALUE, // termScore
                            0 // queryLength
                            );
                    if (val.urlQuality >= QUALITY_LOWER_BOUND_CUTOFF) {
                        result.add(val);
                    }

                }
            }
        }

        return result;
    }


    @Override
    public List<BrowseResult> getDomainNeighborsAdjacent(EdgeId<EdgeDomain> domainId, EdgeDomainBlacklist blacklist, int count) {
        final Set<BrowseResult> domains = new HashSet<>(count*3);

        final String q = """
                            SELECT EC_DOMAIN.ID AS NEIGHBOR_ID, URL_PART, COUNT(*) AS CNT 
                            FROM EC_DOMAIN_NEIGHBORS 
                            INNER JOIN EC_DOMAIN ON NEIGHBOR_ID=EC_DOMAIN.ID 
                            INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID 
                            INNER JOIN EC_DOMAIN_LINK ON DEST_DOMAIN_ID=EC_DOMAIN.ID 
                            WHERE 
                                STATE<2 
                            AND KNOWN_URLS<1000 
                            AND DOMAIN_ALIAS IS NULL 
                            AND EC_DOMAIN_NEIGHBORS.DOMAIN_ID = ? 
                            GROUP BY EC_DOMAIN.ID 
                            HAVING CNT < 100 
                            ORDER BY ADJ_IDX 
                            LIMIT ?
                            """;

        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement(q)) {
                stmt.setFetchSize(count);
                stmt.setInt(1, domainId.getId());
                stmt.setInt(2, count);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    int id = rsp.getInt(1);
                    String domain = rsp.getString(2);

                    if (!blacklist.isBlacklisted(id)) {
                        var url = new EdgeUrl(DEFAULT_PROTOCOL, new EdgeDomain(domain), null, "/");

                        domains.add(new BrowseResult(url, id));
                    }
                }
            }

            if (domains.size() < count/2) {
                final String q2 = """
                        SELECT EC_DOMAIN.ID, URL_PART
                        FROM EC_DOMAIN
                        INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID 
                        INNER JOIN EC_DOMAIN_LINK B ON DEST_DOMAIN_ID=EC_DOMAIN.ID 
                        INNER JOIN EC_DOMAIN_LINK O ON O.DEST_DOMAIN_ID=EC_DOMAIN.ID
                        WHERE B.SOURCE_DOMAIN_ID=? 
                        AND STATE<2 
                        AND KNOWN_URLS<1000 
                        AND DOMAIN_ALIAS IS NULL 
                        GROUP BY EC_DOMAIN.ID 
                        HAVING COUNT(*) < 100 ORDER BY RANK ASC LIMIT ?""";
                try (var stmt = connection.prepareStatement(q2)) {

                    stmt.setFetchSize(count/2);
                    stmt.setInt(1, domainId.getId());
                    stmt.setInt(2, count/2 - domains.size());
                    var rsp = stmt.executeQuery();
                    while (rsp.next()  && domains.size() < count/2) {
                        int id = rsp.getInt(1);
                        String domain = rsp.getString(2);

                        if (!blacklist.isBlacklisted(id)) {
                            var url = new EdgeUrl(DEFAULT_PROTOCOL, new EdgeDomain(domain), null, "/");

                            domains.add(new BrowseResult(url, id));
                        }
                    }
                }
            }

            if (domains.size() < count/2) {
                final String q3 = """
                    SELECT EC_DOMAIN.ID, URL_PART
                    FROM EC_DOMAIN 
                    INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID 
                    INNER JOIN EC_DOMAIN_LINK B ON B.SOURCE_DOMAIN_ID=EC_DOMAIN.ID
                    INNER JOIN EC_DOMAIN_LINK O ON O.DEST_DOMAIN_ID=EC_DOMAIN.ID  
                    WHERE B.DEST_DOMAIN_ID=? 
                    AND STATE<2 
                    AND KNOWN_URLS<1000 
                    AND DOMAIN_ALIAS IS NULL 
                    GROUP BY EC_DOMAIN.ID
                    HAVING COUNT(*) < 100 
                    ORDER BY RANK ASC 
                    LIMIT ?""";
                try (var stmt = connection.prepareStatement(q3)) {
                    stmt.setFetchSize(count/2);
                    stmt.setInt(1, domainId.getId());
                    stmt.setInt(2, count/2 - domains.size());

                    var rsp = stmt.executeQuery();
                    while (rsp.next() && domains.size() < count/2) {
                        int id = rsp.getInt(1);
                        String domain = rsp.getString(2);

                        if (!blacklist.isBlacklisted(id)) {
                            var url = new EdgeUrl(DEFAULT_PROTOCOL, new EdgeDomain(domain), null, "/");

                            domains.add(new BrowseResult(url, id));
                        }
                    }
                }
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }


        return new ArrayList<>(domains);
    }

    @Override
    public List<BrowseResult> getRandomDomains(int count, EdgeDomainBlacklist blacklist) {

        final String q = "SELECT DOMAIN_ID,DOMAIN_NAME FROM EC_RANDOM_DOMAINS INNER JOIN EC_DOMAIN ON EC_DOMAIN.ID=DOMAIN_ID WHERE STATE<2 AND DOMAIN_ALIAS IS NULL ORDER BY RAND() LIMIT ?";
        List<BrowseResult> domains = new ArrayList<>(count);
        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.prepareStatement(q)) {
                stmt.setInt(1, count);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    int id = rsp.getInt(1);
                    String domain = rsp.getString(2);

                    if (!blacklist.isBlacklisted(id)) {
                        var url = new EdgeUrl(DEFAULT_PROTOCOL, new EdgeDomain(domain), null, "/");

                        domains.add(new BrowseResult(url, id));
                    }
                 }
            }
        }
        catch (SQLException ex) {
            logger.error("SQL error", ex);
        }
        return domains;
    }

    @Override
    @SneakyThrows
    public EdgeDomain getDomain(EdgeId<EdgeDomain> id) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT DOMAIN_NAME FROM EC_DOMAIN WHERE ID=?")) {
                stmt.setInt(1, id.getId());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return new EdgeDomain(rsp.getString(1));
                }
                throw new NoSuchElementException();
            }
        }
    }


    @Override
    public Optional<EdgeId<EdgeUrl>> resolveAmbiguousDomain(String name) {
        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement("SELECT IFNULL(DOMAIN_ALIAS,ID) FROM EC_DOMAIN WHERE DOMAIN_NAME=?")) {
                stmt.setString(1, name);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return Optional.of(new EdgeId<>(rsp.getInt(1)));
                }
            }

            try (var stmt = connection.prepareStatement("SELECT IFNULL(DOMAIN_ALIAS,ID) FROM EC_DOMAIN WHERE DOMAIN_NAME=?")) {
                stmt.setString(1, "https://"+name);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return Optional.of(new EdgeId<>(rsp.getInt(1)));
                }
            }

            try (var stmt = connection.prepareStatement("SELECT IFNULL(DOMAIN_ALIAS,ID) FROM EC_DOMAIN WHERE DOMAIN_NAME=?")) {
                stmt.setString(1, "http://"+name);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return Optional.of(new EdgeId<>(rsp.getInt(1)));
                }
            }

            try (var stmt = connection.prepareStatement("SELECT IFNULL(DOMAIN_ALIAS,ID) FROM EC_DOMAIN WHERE DOMAIN_NAME=?")) {
                stmt.setString(1, "https://www."+name);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return Optional.of(new EdgeId<>(rsp.getInt(1)));
                }
            }

            try (var stmt = connection.prepareStatement("SELECT IFNULL(DOMAIN_ALIAS,ID) FROM EC_DOMAIN WHERE DOMAIN_NAME=?")) {
                stmt.setString(1, "http://www."+name);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return Optional.of(new EdgeId<>(rsp.getInt(1)));
                }
            }

        } catch (SQLException throwables) {
            logger.info("Could not resolve domain id for  {}", name);
        }

        return Optional.empty();
    }

    @SneakyThrows
    @Override
    public int getPagesKnown(EdgeId<EdgeDomain> domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT KNOWN_URLS FROM DOMAIN_METADATA WHERE ID=?")) {
                stmt.setInt(1, domainId.getId());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getInt(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return 0;
        }
    }

    @SneakyThrows
    @Override
    public int getPagesVisited(EdgeId<EdgeDomain> domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT VISITED_URLS FROM DOMAIN_METADATA WHERE ID=?")) {
                stmt.setInt(1, domainId.getId());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getInt(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return 0;
        }
    }


    @SneakyThrows
    @Override
    public int getPagesIndexed(EdgeId<EdgeDomain> domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT GOOD_URLS FROM DOMAIN_METADATA WHERE ID=?")) {
                stmt.setInt(1, domainId.getId());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getInt(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return 0;
        }
    }

    @SneakyThrows
    @Override
    public int getIncomingLinks(EdgeId<EdgeDomain> domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT COUNT(ID) FROM EC_DOMAIN_LINK WHERE DEST_DOMAIN_ID=?")) {
                stmt.setInt(1, domainId.getId());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getInt(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return 0;
        }
    }
    @SneakyThrows
    @Override
    public int getOutboundLinks(EdgeId<EdgeDomain> domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT COUNT(ID) FROM EC_DOMAIN_LINK WHERE SOURCE_DOMAIN_ID=?")) {
                stmt.setInt(1, domainId.getId());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getInt(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return 0;
        }
    }

    @SneakyThrows
    @Override
    public double getDomainQuality(EdgeId<EdgeDomain> domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT QUALITY FROM EC_DOMAIN WHERE ID=?")) {
                stmt.setInt(1, domainId.getId());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getDouble(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
            return -5;
        }
    }

    @Override
    public EdgeDomainIndexingState getDomainState(EdgeId<EdgeDomain> domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT STATE FROM EC_DOMAIN WHERE ID=?")) {
                stmt.setInt(1, domainId.getId());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return EdgeDomainIndexingState.fromCode(rsp.getInt(1));
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return EdgeDomainIndexingState.ERROR;
    }

    @Override
    public List<EdgeDomain> getLinkingDomains(EdgeId<EdgeDomain> domainId) {
        try (var connection = dataSource.getConnection()) {
            List<EdgeDomain> results = new ArrayList<>(25);
            try (var stmt = connection.prepareStatement("SELECT SOURCE_URL FROM EC_RELATED_LINKS_VIEW WHERE DEST_DOMAIN_ID=? ORDER BY SOURCE_DOMAIN_ID LIMIT 25")) {
                stmt.setInt(1, domainId.getId());
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    results.add(new EdgeDomain(rsp.getString(1)));
                }
                return results;
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }

        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return Collections.emptyList();
    }

    @Override
    public double getRank(EdgeId<EdgeDomain> domainId) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT IFNULL(RANK, 1) FROM EC_DOMAIN WHERE ID=?")) {
                stmt.setInt(1, domainId.getId());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return rsp.getDouble(1);
                }
            } catch (Exception ex) {
                logger.error("DB error", ex);
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return 1;
    }


}
