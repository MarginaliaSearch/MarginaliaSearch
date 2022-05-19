package nu.marginalia.wmsa.edge.data.dao;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import edu.stanford.nlp.parser.lexparser.Edge;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.crawler.domain.UrlsCache;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.model.*;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainLink;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlVisit;
import nu.marginalia.wmsa.edge.model.search.EdgePageScoreAdjustment;
import nu.marginalia.wmsa.edge.model.search.EdgeUrlDetails;
import nu.marginalia.wmsa.edge.search.BrowseResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static nu.marginalia.wmsa.edge.crawler.worker.UploaderWorker.QUALITY_LOWER_BOUND_CUTOFF;

public class EdgeDataStoreDaoImpl implements EdgeDataStoreDao {
    private static final int DB_LOCK_RETRIES = 3;

    private final HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Cache<EdgeUrl, EdgeId<EdgeUrl>> urlIdCache = CacheBuilder.newBuilder().maximumSize(100_000).build();
    private final Cache<EdgeDomain, EdgeId<EdgeDomain>> domainIdCache = CacheBuilder.newBuilder().maximumSize(10_000).build();

    private final UrlsCache<EdgeUrl> URLS_INSERTED_CACHE = new UrlsCache<>();
    private final UrlsCache<EdgeDomain> DOMAINS_INSERTED_CACHE = new UrlsCache<>();

    private static final String DEFAULT_PROTOCOL = "http";

    @Inject
    public EdgeDataStoreDaoImpl(HikariDataSource dataSource)
    {
        this.dataSource = dataSource;
    }


    public synchronized void clearCaches()
    {
        urlIdCache.invalidateAll();
        domainIdCache.invalidateAll();
        URLS_INSERTED_CACHE.clear();
        DOMAINS_INSERTED_CACHE.clear();
    }

    @Override
    @SneakyThrows
    public void putUrl(double quality, EdgeUrl... urls) {
        if (quality > 0.5) {
            logger.warn("Put URL q={} {}", quality, urls);
        }

        if (urls.length == 0) {
            return;
        }

        try (var connection = dataSource.getConnection()) {

            connection.setAutoCommit(false);

            for (int i = 0; i < DB_LOCK_RETRIES; i++) {
                try {
                    var domains = Arrays.stream(urls)
                            .map(EdgeUrl::getDomain)
                            .distinct().toArray(EdgeDomain[]::new);

                    insert(connection, domains, quality);
                    insert(connection, urls);
                    connection.commit();
                    break;
                } catch (Exception ex) {
                    logger.error("DB error", ex);
                    connection.rollback();
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        }
    }


    @Override
    @SneakyThrows
    public void putFeeds(EdgeUrl... urls) {
        if (urls.length == 0) {
            return;
        }

        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            for (int i = 0; i < DB_LOCK_RETRIES; i++) {
                try {
                    insertFeed(connection, urls);
                    connection.commit();
                    break;
                } catch (Exception ex) {
                    logger.error("DB error", ex);
                    connection.rollback();
                }
                finally {
                    connection.setAutoCommit(true);
                }
            }
        }
    }

    @Override
    @SneakyThrows
    public void putUrlVisited(EdgeUrlVisit... urls) {
        if (urls.length == 0) {
            return;
        }

        try (var connection = dataSource.getConnection()) {

            connection.setAutoCommit(false);

            for (int i = 0; i < DB_LOCK_RETRIES; i++) {
                try {
                    insert(connection, Arrays.stream(urls).map(url -> url.getUrl().domain).toArray(EdgeDomain[]::new), Optional.ofNullable(urls[0].quality).orElse(-2.));
                    visited(connection, urls);
                    connection.commit();
                    break;
                } catch (Exception ex) {
                    logger.error("DB error", ex);
                    connection.rollback();
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        }
    }

    @SneakyThrows
    private void insert(Connection connection, EdgeUrl[] urls) {

        EdgeUrl[] toCommitUrls = Arrays
                .stream(urls)
                .filter(URLS_INSERTED_CACHE::isMissing)
                .sorted(Comparator.comparing(url -> url.toString().length()))
                .distinct()
                .toArray(EdgeUrl[]::new);

        int size = 0;
        try (var stmt =
                     connection.prepareStatement("INSERT IGNORE INTO EC_URL (URL, DOMAIN_ID, PROTO, PORT) SELECT ? AS URL, ID, ?, ? AS DOMAIN_ID FROM EC_DOMAIN WHERE URL_PART=?")) {
            for (var url : toCommitUrls) {
                logger.trace("insert({})", url);

                if (url.path.length() > 255) {
                    logger.warn("(insert) URL too long: {}", url);
                    continue;
                }

                stmt.setString(1, url.path);
                stmt.setString(2, url.proto);
                if (url.port != null) {
                    stmt.setInt(3, url.port);
                }
                else {
                    stmt.setNull(3, Types.INTEGER);
                }
                stmt.setString(4, url.domain.toString());
                stmt.addBatch();
                if ((++size % 100) == 0) {
                    int[] status = stmt.executeBatch();
                    checkExecuteStatus("insert", status);
                }
            }
            int[] status = stmt.executeBatch();
            checkExecuteStatus("insert", status);

            URLS_INSERTED_CACHE.addAll(toCommitUrls);
        }

    }

    @SneakyThrows
    private void visited(Connection connection, EdgeUrlVisit[] visits) {
        int size = 0;

        try (var stmt =
                     connection.prepareStatement(
                             "UPDATE EC_URL INNER JOIN EC_DOMAIN ON DOMAIN_ID=EC_DOMAIN.ID " +
                                     "SET QUALITY_MEASURE=?, DATA_HASH=?, IP=?, EC_URL.STATE=?, VISITED=TRUE " +
                                     " WHERE URL_PART=? AND URL=?")) {
            for (var visit : visits) {
                logger.trace("(visit) insert({})", visit);

                if (visit.url.path.length() > 255) {
                    logger.warn("URL too long: {}", visit.url);
                    continue;
                }

                if (visit.quality != null) {
                    stmt.setDouble(1, visit.quality);
                } else {
                    stmt.setNull(1, Types.DOUBLE);
                }

                if (visit.data_hash_code != null) {
                    stmt.setInt(2, visit.data_hash_code);
                } else {
                    stmt.setNull(2, Types.INTEGER);
                }

                stmt.setString(3, visit.ipAddress);
                stmt.setString(4, visit.urlState.toString());
                stmt.setString(5, visit.url.domain.toString());
                stmt.setString(6, visit.url.path);
                stmt.addBatch();
                if ((++size % 100) == 0) {
                    int[] status = stmt.executeBatch();
                    checkExecuteStatus("set-visited", status);
                }
            }
            var status = stmt.executeBatch();

            checkExecuteStatus("set-visited", status);
        }

        try (var stmt =
                     connection.prepareStatement("REPLACE INTO EC_PAGE_DATA (ID, TITLE, DESCRIPTION, WORDS_DISTINCT, WORDS_TOTAL, FORMAT, FEATURES) SELECT ID, ?,?,?,?,?,? FROM EC_URL_VIEW WHERE URL_DOMAIN=? AND URL_PATH=? AND URL_PROTO=? AND IFNULL(URL_PORT,-1)=IFNULL(?,-1)")) {
            for (var visit : visits) {


                if (visit.title != null) {
                    stmt.setString(1, StringUtils.truncate(visit.title, 255));
                }
                else {
                    stmt.setNull(1, Types.VARCHAR);
                }

                if (visit.description != null) {
                    stmt.setString(2, StringUtils.truncate(visit.description, 255));
                }
                else {
                    stmt.setNull(2, Types.VARCHAR);
                }

                stmt.setInt(3, visit.wordCountDistinct);
                stmt.setInt(4, visit.wordCountTotal);
                stmt.setString(5, visit.format);
                stmt.setInt(6, visit.features);
                stmt.setString(7, visit.url.domain.toString());
                stmt.setString(8, visit.url.path);
                stmt.setString(9, visit.url.proto);
                if (visit.url.port == null) {
                    stmt.setNull(10, Types.INTEGER);
                } else {
                    stmt.setInt(10, visit.url.port);
                }
                stmt.addBatch();
            }
            var status = stmt.executeBatch();
            checkExecuteStatus("set-visited2", status);

        }
    }

    private void checkExecuteStatus(String operation, int[] status) {
    }

    @SneakyThrows
    private void insertFeed(Connection connection, EdgeUrl[] urls) {

        int size = 0;
        try (var stmt =
                     connection.prepareStatement("INSERT IGNORE INTO EC_FEED_URL (URL, DOMAIN_ID, PROTO, PORT) SELECT ? AS URL, ID, ?, ? AS DOMAIN_ID FROM EC_DOMAIN WHERE URL_PART=?")) {
            for (var url : urls) {
                logger.trace("insert({})", url);

                if (url.path.length() > 255) {
                    logger.warn("(insert) URL too long: {}", url);
                    continue;
                }

                stmt.setString(1, url.path);
                stmt.setString(2, url.proto);
                if (url.port != null) {
                    stmt.setInt(3, url.port);
                }
                else {
                    stmt.setNull(3, Types.INTEGER);
                }
                stmt.setString(4, url.domain.toString());
                stmt.addBatch();
                if ((++size % 100) == 0) {
                    int[] status = stmt.executeBatch();
                    checkExecuteStatus("insert", status);
                }
            }
            int[] status = stmt.executeBatch();
            checkExecuteStatus("insert", status);
        }

    }
    @SneakyThrows
    private void insert(Connection connection, EdgeDomain[] domains, double quality) {
        EdgeDomain[] toCommitDomains = Arrays.stream(domains).filter(DOMAINS_INSERTED_CACHE::isMissing).distinct().toArray(EdgeDomain[]::new);

        try (var stmt =
                     connection.prepareStatement("INSERT IGNORE INTO EC_TOP_DOMAIN (URL_PART) VALUES (?)")) {
            for (var domain : toCommitDomains) {
                stmt.setString(1, domain.getDomain());
                stmt.addBatch();
            }
            var status = stmt.executeBatch();
            checkExecuteStatus("insert", status);
        }

        int size = 0;

        if (quality > 0.5) {
            logger.warn("1 quality insert? {}", quality);
        }

        try (var stmt =
                     connection.prepareStatement("INSERT IGNORE INTO EC_DOMAIN (URL_PART, QUALITY, QUALITY_ORIGINAL, URL_TOP_DOMAIN_ID, URL_SUBDOMAIN, RANK) SELECT ?, IFNULL(EC_DOMAIN_HISTORY.QUALITY_MEASURE*IFNULL(EC_DOMAIN_HISTORY.RANK, 1), ?), ?, EC_TOP_DOMAIN.ID, ?, IFNULL(EC_DOMAIN_HISTORY.RANK,1) FROM EC_TOP_DOMAIN LEFT JOIN EC_DOMAIN_HISTORY ON EC_DOMAIN_HISTORY.URL_PART=? WHERE EC_TOP_DOMAIN.URL_PART=?")) {
            for (var domain : toCommitDomains) {
                logger.trace("insert({})", domain);
                stmt.setString(1, domain.toString());
                stmt.setDouble(2, quality);
                stmt.setDouble(3, quality);

                stmt.setString(4, domain.subDomain);

                stmt.setString(5, domain.toString());
                stmt.setString(6, domain.domain);

                stmt.addBatch();

                if ((++size % 100) == 0) {
                    int[] status = stmt.executeBatch();
                    checkExecuteStatus("insert", status);
                }
            }
            var status = stmt.executeBatch();
            checkExecuteStatus("insert", status);

            DOMAINS_INSERTED_CACHE.addAll(toCommitDomains);
        }

    }

    @Override
    @SneakyThrows
    public void putLink(boolean wipeExisting, EdgeDomainLink... links) {
        if (links.length == 0) {
            return;
        }

        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            var domains = Arrays.stream(links).flatMap(link ->
                    Stream.concat(Stream.of(link.destination),
                            Stream.of(link.source)))
                    .distinct().toArray(EdgeDomain[]::new);

            for (int i = 0; i < DB_LOCK_RETRIES; i++) {
                try {
                    insert(connection, domains, -5);
                    insert(connection, links, wipeExisting);
                    connection.commit();
                    break;

                } catch (Exception ex) {
                    logger.error("DB error", ex);
                    connection.rollback();
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        }
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
    private void insert(Connection connection, EdgeDomainLink[] links, boolean wipeExisting) {

        int size = 0;
        if (wipeExisting) {
            try (var stmt = connection.prepareStatement("DELETE FROM EC_DOMAIN_LINK WHERE SOURCE_DOMAIN_ID=?")) {
                EdgeDomain[] sources = Arrays.stream(links).map(EdgeDomainLink::getSource).distinct().toArray(EdgeDomain[]::new);
                for (var source : sources) {
                    stmt.setInt(1, getDomainId(source).getId());
                    stmt.executeUpdate();
                }
            }
        }

        try (var stmt =
                     connection.prepareStatement(
                             "INSERT IGNORE INTO EC_DOMAIN_LINK (SOURCE_DOMAIN_ID, DEST_DOMAIN_ID) SELECT SRC.DOMAIN_ID, DEST.DOMAIN_ID FROM (SELECT EC_DOMAIN.ID AS DOMAIN_ID FROM EC_DOMAIN WHERE EC_DOMAIN.URL_PART=?) AS SRC, (SELECT EC_DOMAIN.ID AS DOMAIN_ID FROM EC_DOMAIN WHERE EC_DOMAIN.URL_PART=?) AS DEST")) {

            for (EdgeDomainLink link : links) {
                stmt.setString(1, link.source.toString());
                stmt.setString(2, link.destination.toString());

                stmt.addBatch();

                if ((++size % 100) == 0) {
                    int[] status = stmt.executeBatch();
                    checkExecuteStatus("insert", status);
                }
            }

            var status = stmt.executeBatch();
            checkExecuteStatus("insert", status);
        }
    }

    @SneakyThrows
    @Override
    public EdgeId<EdgeDomain> getDomainId(EdgeDomain domain) {
        try (var connection = dataSource.getConnection()) {

            return domainIdCache.get(domain, () -> {
                try (var stmt = connection.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE URL_PART=?")) {
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

    @Override
    @SneakyThrows
    public EdgeId<EdgeUrl> getUrlId(EdgeUrl url) {
        try (var connection = dataSource.getConnection()) {

            return urlIdCache.get(url, () -> {
                try (var stmt = connection.prepareStatement("SELECT ID FROM EC_URL_VIEW WHERE URL_PATH=? AND URL_DOMAIN=? AND URL_PROTO=?")) {
                    stmt.setString(1, url.path);
                    stmt.setString(2, url.domain.toString());
                    stmt.setString(3, url.proto);

                    var rsp = stmt.executeQuery();
                    if (rsp.next()) {
                        return new EdgeId<>(rsp.getInt(1));
                    }
                }
                // Lenient mode for http->https upgrades etc
                try (var stmt = connection.prepareStatement("SELECT ID FROM EC_URL_VIEW WHERE URL_PATH=? AND URL_DOMAIN=?")) {
                    stmt.setString(1, url.path);
                    stmt.setString(2, url.domain.toString());

                    var rsp = stmt.executeQuery();
                    if (rsp.next()) {
                        return new EdgeId<>(rsp.getInt(1));
                    }
                }
                throw new NoSuchElementException(url.toString());
            });
        }
        catch (UncheckedExecutionException ex) {
            throw ex.getCause();
        }
    }


    @SneakyThrows
    @Override
    public List<EdgeId<EdgeDomain>> getDomainIdsFromUrlIds(Collection<EdgeId<EdgeUrl>> urlIds) {
        List<EdgeId<EdgeDomain>> results = new ArrayList<>(urlIds.size());

        if (urlIds.isEmpty())
            return results;

        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT DOMAIN_ID FROM EC_URL WHERE ID IN " + urlIds
                    .stream()
                    .map(EdgeId::getId)
                    .map(Object::toString)
                    .collect(Collectors.joining(",", "(", ")"))))
            {
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    results.add(new EdgeId<>(rsp.getInt(1)));
                }

            }
        }

        return results;
    }

    static Pattern badChars = Pattern.compile("[';\\\\]");
    private String saneString(String s) {
        return "\'"+badChars.matcher(s).replaceAll("?")+"\'";
    }
    @SneakyThrows
    @Override
    public EdgeUrl getUrl(EdgeId<EdgeUrl> id) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.createStatement()) {
                var rsp = stmt.executeQuery("SELECT URL_PROTO, URL_DOMAIN,URL_PORT,URL_PATH FROM EC_URL_VIEW WHERE ID=" + id.getId());
                if (rsp.next()) {
                    return new EdgeUrl(rsp.getString(1), new EdgeDomain(rsp.getString(2)), rsp.getInt(3), rsp.getString(4));
                }
                throw new NoSuchElementException();
            }
        }
    }

    @SneakyThrows
    @Override
    public EdgeUrlDetails getUrlDetails(EdgeId<EdgeUrl> id) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.createStatement()) {
                var rsp = stmt.executeQuery("SELECT ID,URL_PROTO,URL_DOMAIN,URL_PORT,URL_PATH,TITLE,DESCRIPTION,URL_QUALITY_MEASURE,DOMAIN_QUALITY_MEASURE,IFNULL(EC_DOMAIN_LINK_AGGREGATE.LINKS,1),WORDS_TOTAL,FORMAT,FEATURES,\"\",QUALITY_RAW,DOMAIN_STATE,DATA_HASH FROM EC_URL_VIEW LEFT JOIN EC_DOMAIN_LINK_AGGREGATE ON EC_DOMAIN_LINK_AGGREGATE.DOMAIN_ID=EC_URL_VIEW.DOMAIN_ID WHERE ID=" + id.getId());
                if (rsp.next()) {
                    EdgeUrl url = new EdgeUrl(rsp.getString(2), new EdgeDomain(rsp.getString(3)), rsp.getInt(4), rsp.getString(5));
                    return new EdgeUrlDetails(rsp.getInt(1), url, rsp.getString(6), rsp.getString(7), rsp.getDouble(8), rsp.getDouble(15), rsp.getDouble(9), rsp.getInt(10), rsp.getInt(11), rsp.getString(12), rsp.getInt(13),  EdgePageScoreAdjustment.zero(), Integer.MAX_VALUE, Double.MAX_VALUE, rsp.getString(14), rsp.getInt(16), 0, rsp.getInt(17));
                }
                throw new NoSuchElementException();
            }
        }
    }


    @SneakyThrows
    @Override
    public List<EdgeUrlDetails> getUrlDetailsMulti(List<EdgeId<EdgeUrl>> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<EdgeUrlDetails> result = new ArrayList<>(ids.size());

        try (var connection = dataSource.getConnection()) {
            // This is SQL-injection safe, the IDs are of type int
            String idString = ids.stream().map(EdgeId::getId).map(Objects::toString).collect(Collectors.joining(",", "(", ")"));

            try (var stmt = connection.prepareStatement("SELECT ID,URL_PROTO,URL_DOMAIN,URL_PORT,URL_PATH,TITLE,DESCRIPTION,URL_QUALITY_MEASURE,DOMAIN_QUALITY_MEASURE,IFNULL(EC_DOMAIN_LINK_AGGREGATE.LINKS,1),WORDS_TOTAL,FORMAT,FEATURES,\"\",QUALITY_RAW,DOMAIN_STATE,DATA_HASH FROM EC_URL_VIEW LEFT JOIN EC_DOMAIN_LINK_AGGREGATE ON EC_DOMAIN_LINK_AGGREGATE.DOMAIN_ID=EC_URL_VIEW.DOMAIN_ID WHERE ID IN " + idString)) {
                stmt.setFetchSize(ids.size());

                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    EdgeUrl url = new EdgeUrl(rsp.getString(2), new EdgeDomain(rsp.getString(3)), rsp.getInt(4), rsp.getString(5));
                    var val = new EdgeUrlDetails(rsp.getInt(1), url, rsp.getString(6), rsp.getString(7), rsp.getDouble(8), rsp.getDouble(15), rsp.getDouble(9), rsp.getInt(10), rsp.getInt(11), rsp.getString(12), rsp.getInt(13),  EdgePageScoreAdjustment.zero(), Integer.MAX_VALUE, Double.MAX_VALUE, rsp.getString(14), rsp.getInt(16), 0, rsp.getInt(17));
                    if (val.urlQuality >= QUALITY_LOWER_BOUND_CUTOFF) {
                        result.add(val);
                    }

                }
            }
        }

        return result;
    }

    @Override
    public List<BrowseResult> getDomainNeighbors(EdgeId<EdgeDomain> domainId, EdgeDomainBlacklist blacklist, int count) {
        final Set<BrowseResult> domains = new HashSet<>(count*3);

        final String q = "SELECT EC_DOMAIN.ID AS NEIGHBOR_ID, URL_PART from EC_DOMAIN_NEIGHBORS INNER JOIN EC_DOMAIN ON NEIGHBOR_ID=EC_DOMAIN.ID WHERE STATE<2 AND DOMAIN_ALIAS IS NULL AND EC_DOMAIN_NEIGHBORS.DOMAIN_ID = ? ORDER BY ADJ_IDX LIMIT ?";

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

            final String q2 = "SELECT EC_DOMAIN.ID, URL_PART FROM EC_DOMAIN_LINK INNER JOIN EC_DOMAIN ON DEST_DOMAIN_ID=EC_DOMAIN.ID WHERE SOURCE_DOMAIN_ID=? AND STATE<2 AND DOMAIN_ALIAS IS NULL GROUP BY EC_DOMAIN.ID ORDER BY RANK ASC LIMIT ?";
            try (var stmt = connection.prepareStatement(q2)) {

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

            final String q3 = "SELECT EC_DOMAIN.ID, URL_PART FROM EC_DOMAIN_LINK INNER JOIN EC_DOMAIN ON DEST_DOMAIN_ID=EC_DOMAIN.ID WHERE DEST_DOMAIN_ID=? AND STATE<2 AND DOMAIN_ALIAS IS NULL GROUP BY EC_DOMAIN.ID ORDER BY RANK ASC LIMIT ?";
            try (var stmt = connection.prepareStatement(q3)) {
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
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }


        return new ArrayList<>(domains);
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

        final String q = "SELECT DOMAIN_ID,URL_PART FROM EC_RANDOM_DOMAINS INNER JOIN EC_DOMAIN ON EC_DOMAIN.ID=DOMAIN_ID WHERE STATE<2 AND DOMAIN_ALIAS IS NULL ORDER BY RAND() LIMIT ?";
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

            try (var stmt = connection.prepareStatement("SELECT URL_PART FROM EC_DOMAIN WHERE ID=?")) {
                stmt.setInt(1, id.getId());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return new EdgeDomain(rsp.getString(1));
                }
                throw new NoSuchElementException();
            }
        }
    }

    @Override @SneakyThrows
    public List<EdgeId<EdgeUrl>> inboudUrls(EdgeId<EdgeUrl> id, int limit) {

        List<EdgeId<EdgeUrl>> ret = new ArrayList<>();
        try (var connection = dataSource.getConnection()) {

            try (var stmt =
                         connection.prepareStatement("SELECT SRC_URL_ID FROM EC_RELATED_LINKS_IN WHERE DEST_URL_ID=? ORDER BY SRC_URL_QUALITY DESC LIMIT ?")) {
                stmt.setFetchSize(limit);
                stmt.setInt(1, id.getId());
                stmt.setInt(2, limit);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    ret.add(new EdgeId<>(rsp.getInt(1)));
                }
            }

        }

        return ret;
    }


    @Override @SneakyThrows
    public List<EdgeId<EdgeUrl>> outboundUrls(EdgeId<EdgeUrl> id, int limit) {

        List<EdgeId<EdgeUrl>> ret = new ArrayList<>();
        try (var connection = dataSource.getConnection()) {

            try (var stmt =
                         connection.prepareStatement("SELECT DEST_URL_ID FROM EC_RELATED_LINKS_IN WHERE SRC_URL_ID=? ORDER BY SRC_URL_QUALITY DESC LIMIT ?")) {
                stmt.setFetchSize(limit);
                stmt.setInt(1, id.getId());
                stmt.setInt(2, limit);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    ret.add(new EdgeId<>(rsp.getInt(1)));
                }
            }

        }

        return ret;
    }

    @Override
    public Optional<EdgeId<EdgeUrl>> resolveAmbiguousDomain(String name) {
        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement("SELECT IFNULL(DOMAIN_ALIAS,ID) FROM EC_DOMAIN WHERE URL_PART=?")) {
                stmt.setString(1, name);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return Optional.of(new EdgeId<>(rsp.getInt(1)));
                }
            }

            try (var stmt = connection.prepareStatement("SELECT IFNULL(DOMAIN_ALIAS,ID) FROM EC_DOMAIN WHERE URL_PART=?")) {
                stmt.setString(1, "https://"+name);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return Optional.of(new EdgeId<>(rsp.getInt(1)));
                }
            }

            try (var stmt = connection.prepareStatement("SELECT IFNULL(DOMAIN_ALIAS,ID) FROM EC_DOMAIN WHERE URL_PART=?")) {
                stmt.setString(1, "http://"+name);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return Optional.of(new EdgeId<>(rsp.getInt(1)));
                }
            }

            try (var stmt = connection.prepareStatement("SELECT IFNULL(DOMAIN_ALIAS,ID) FROM EC_DOMAIN WHERE URL_PART=?")) {
                stmt.setString(1, "https://www."+name);
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return Optional.of(new EdgeId<>(rsp.getInt(1)));
                }
            }

            try (var stmt = connection.prepareStatement("SELECT IFNULL(DOMAIN_ALIAS,ID) FROM EC_DOMAIN WHERE URL_PART=?")) {
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
    public void putDomainAlias(EdgeDomain src, EdgeDomain dst) {
        try (var connection = dataSource.getConnection()) {

            for (int i = 0; i < DB_LOCK_RETRIES; i++) {
                connection.setAutoCommit(false);

                if (!DOMAINS_INSERTED_CACHE.contains(dst)) {
                    insert(connection, new EdgeDomain[] { dst }, getDomainQuality(connection, src));
                }

                try (var stmt = connection.prepareStatement("UPDATE EC_DOMAIN AS D, EC_DOMAIN AS S SET S.DOMAIN_ALIAS=D.ID WHERE S.URL_PART=? AND D.URL_PART=?")) {
                    stmt.setString(1, src.toString());
                    stmt.setString(2, dst.toString());
                    stmt.executeUpdate();
                    connection.commit();
                    break;
                } catch (SQLException ex) {
                    logger.error("DB Error", ex);
                    connection.rollback();
                }
                finally {
                    connection.setAutoCommit(true);
                }
            }

        }

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
    public List<EdgeUrl> getNewUrls(EdgeId<EdgeDomain> domainId, Collection<EdgeUrl> links) {
        Map<String, EdgeUrl> edgeUrlByPath = links.stream().collect(Collectors.toMap(EdgeUrl::getPath, Function.identity(), (a,b)->a));

        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement("SELECT URL FROM EC_URL WHERE DOMAIN_ID=?")) {
                stmt.setFetchSize(500);
                stmt.setInt(1, domainId.getId());
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    edgeUrlByPath.remove(rs.getString(1));
                }
            }
        }
        catch (Exception ex) {
            return Collections.emptyList();
        }
        return new ArrayList<>(edgeUrlByPath.values());

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

    @Override
    public void updateDomainIndexTimestamp(EdgeDomain domain, EdgeDomainIndexingState state, EdgeDomain alias, int minIndexed) {
        try (var connection = dataSource.getConnection();
             var stmt = connection.prepareStatement("UPDATE EC_DOMAIN SET INDEX_DATE=NOW(), STATE=?, DOMAIN_ALIAS=?, INDEXED=GREATEST(INDEXED,?) WHERE ID=?")) {
            stmt.setInt(1, state.code);
            if (null == alias) {
                stmt.setNull(2, Types.INTEGER);
            }
            else {
                stmt.setInt(2, getDomainId(alias).getId());
            }

            stmt.setInt(3, minIndexed);
            stmt.setInt(4, getDomainId(domain).getId());
            stmt.executeUpdate();
            connection.commit();
        }
        catch (SQLException throwables) {
            logger.error("SQL error", throwables);
        }
    }

    @SneakyThrows
    private double getDomainQuality(Connection connection, EdgeDomain src) {
        try (var stmt = connection.prepareStatement("SELECT QUALITY_RAW FROM EC_DOMAIN WHERE URL_PART=?")) {
            stmt.setString(1, src.toString());
            var res = stmt.executeQuery();

            if (res.next()) {
                var q = res.getDouble(1);
                if (q > 0.5) {
                    logger.warn("gDQ({}) -> 1", src);
                }
                return 0;
            }
        }
        catch (SQLException ex) {
            logger.error("DB error", ex);
        }

        return -5;
    }

}
