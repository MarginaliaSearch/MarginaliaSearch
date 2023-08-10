package nu.marginalia.loading.loader;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
import java.util.Set;

import static java.sql.Statement.SUCCESS_NO_INFO;

public class SqlLoadUrls {

    private final HikariDataSource dataSource;
    private static final Logger logger = LoggerFactory.getLogger(SqlLoadUrls.class);

    @Inject
    public SqlLoadUrls(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }
    private final MurmurHash3_128 murmurHash = new MurmurHash3_128();

    public void load(LoaderData data, EdgeUrl[] urls) {
        Set<EdgeDomain> affectedDomains = new HashSet<>();

        if (urls.length == 0)
            return;

        int maxOldId = 0;
        try (var conn = dataSource.getConnection()) {

            try (var insertStmt = conn.prepareStatement("INSERT IGNORE INTO EC_URL (PROTO,DOMAIN_ID,PORT,PATH,PARAM,PATH_HASH) VALUES (?,?,?,?,?,?)");
                 var queryMaxId = conn.prepareStatement("SELECT MAX(ID) FROM EC_URL")) {

                conn.setAutoCommit(false);

                var rs = queryMaxId.executeQuery();
                if (rs.next()) {
                    maxOldId = rs.getInt(1);
                }

                int cnt = 0;
                int batchOffset = 0;

                for (var url : urls) {
                    if (data.getUrlId(url) != 0)
                        continue;

                    if (url.path.length() >= 255) {
                        logger.info("Skipping bad URL {}", url);
                        continue;
                    }
                    var domainId = data.getDomainId(url.domain);

                    affectedDomains.add(url.domain);

                    insertStmt.setString(1, url.proto);
                    insertStmt.setInt(2, domainId);
                    if (url.port != null) {
                        insertStmt.setInt(3, url.port);
                    } else {
                        insertStmt.setNull(3, Types.INTEGER);
                    }
                    insertStmt.setString(4, url.path);
                    insertStmt.setString(5, url.param);
                    insertStmt.setLong(6, hashPath(url.path, url.param));
                    insertStmt.addBatch();

                    if (++cnt == 1000) {
                        var ret = insertStmt.executeBatch();
                        for (int rv = 0; rv < cnt; rv++) {
                            if (ret[rv] < 0 && ret[rv] != SUCCESS_NO_INFO) {
                                logger.warn("load({}) -- bad row count {}", urls[batchOffset + rv], ret[rv]);
                            }
                        }

                        batchOffset += cnt;
                        cnt = 0;
                    }
                }

                if (cnt > 0) {
                    var ret = insertStmt.executeBatch();
                    for (int rv = 0; rv < cnt; rv++) {
                        if (ret[rv] < 0 && ret[rv] != SUCCESS_NO_INFO) {
                            logger.warn("load({}) -- bad row count {}", urls[batchOffset + rv], ret[rv]);
                        }
                    }
                }

                conn.commit();
                conn.setAutoCommit(true);

                for (var domain : affectedDomains) {
                    loadUrlsForDomain(data, domain, maxOldId);
                }
            }
            catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        }
        catch (SQLException ex) {
            logger.warn("SQL error inserting URLs", ex);

            if (getClass().desiredAssertionStatus())
                throw new RuntimeException(ex);
        }
    }

    /* We use a uniqueness constraint on DOMAIN_ID and this hash instead of on the PATH and PARAM
     * fields as the uniqueness index grows absurdly large for some reason, possibly due to the prevalent
     * shared leading substrings in paths?
     */
    private long hashPath(String path, String queryParam) {
        long hash = murmurHash.hashNearlyASCII(path);
        if (queryParam != null) {
            hash ^= murmurHash.hashNearlyASCII(queryParam);
        }
        return hash;
    }

    /** Loads urlIDs for the domain into `data` from the database, starting at URL ID minId. */
    public void loadUrlsForDomain(LoaderData data, EdgeDomain domain, int minId) throws SQLException {
        try (var conn = dataSource.getConnection();
             var queryCall = conn.prepareStatement("SELECT ID, PROTO, PATH, PARAM FROM EC_URL WHERE DOMAIN_ID=? AND ID > ?")) {

            queryCall.setFetchSize(1000);
            queryCall.setInt(1, data.getDomainId(domain));
            queryCall.setInt(2, minId);

            var rsp = queryCall.executeQuery();

            while (rsp.next()) {
                int urlId = rsp.getInt(1);
                String proto = rsp.getString(2);
                String path = rsp.getString(3);
                String param = rsp.getString(4);

                data.addUrl(new EdgeUrl(proto, domain, null, path, param), urlId);
            }
        }

    }
}
