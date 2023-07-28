package nu.marginalia.loading.loader;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
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

    public void load(LoaderData data, EdgeUrl[] urls) {
        Set<EdgeDomain> affectedDomains = new HashSet<>();

        if (urls.length == 0)
            return;

        int maxOldId = 0;
        try (var conn = dataSource.getConnection();
             var insertCall = conn.prepareStatement("INSERT IGNORE INTO EC_URL (PROTO,DOMAIN_ID,PORT,PATH,PARAM,PATH_HASH) VALUES (?,?,?,?,?,?)");
             var queryMaxId = conn.prepareStatement("SELECT MAX(ID) FROM EC_URL"))
        {
            conn.setAutoCommit(false);
            var rs = queryMaxId.executeQuery();
            if (rs.next()) {
                maxOldId = rs.getInt(1);
            }

            int cnt = 0; int batchOffset = 0;

            for (var url : urls) {
                if (data.getUrlId(url) != 0)
                    continue;
                if (url.path.length() >= 255) {
                    logger.info("Skipping bad URL {}", url);
                    continue;
                }
                var domainId = data.getDomainId(url.domain);

                affectedDomains.add(url.domain);

                insertCall.setString(1, url.proto);
                insertCall.setInt(2, domainId);
                if (url.port != null) {
                    insertCall.setInt(3, url.port);
                }
                else {
                    insertCall.setNull(3, Types.INTEGER);
                }
                insertCall.setString(4, url.path);
                insertCall.setString(5, url.param);
                insertCall.setLong(6, hashPath(url.path, url.param));
                insertCall.addBatch();

                if (++cnt == 1000) {
                    var ret = insertCall.executeBatch();
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
                var ret = insertCall.executeBatch();
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
            logger.warn("SQL error inserting URLs", ex);

            if (getClass().desiredAssertionStatus())
                throw new RuntimeException(ex);
        }
    }

    private static final HashFunction murmur3_128 = Hashing.murmur3_128();
    private long hashPath(String path, String queryParam) {
        long pathHash = murmur3_128.hashString(path, StandardCharsets.UTF_8).padToLong();

        if (queryParam == null) {
            return pathHash;
        }
        else {
            return pathHash + murmur3_128.hashString(queryParam, StandardCharsets.UTF_8).padToLong();
        }
    }

    /** Loads urlIDs for the domain into `data` from the database, starting at URL ID minId. */
    public void loadUrlsForDomain(LoaderData data, EdgeDomain domain, int minId) throws SQLException {
        try (var conn = dataSource.getConnection();
             var queryCall = conn.prepareStatement("SELECT ID, PROTO, PATH, PARAM FROM EC_URL WHERE DOMAIN_ID=? AND ID > ?")) {

            queryCall.setInt(1, data.getDomainId(domain));
            queryCall.setInt(2, minId);

            var rsp = queryCall.executeQuery();
            rsp.setFetchSize(1000);

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
