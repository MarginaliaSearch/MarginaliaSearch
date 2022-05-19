package nu.marginalia.wmsa.edge.converting.loader;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.Types;

import static java.sql.Statement.SUCCESS_NO_INFO;

public class SqlLoadUrls {

    private final HikariDataSource dataSource;
    private static final Logger logger = LoggerFactory.getLogger(SqlLoadUrls.class);

    @Inject
    public SqlLoadUrls(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.createStatement()) {
                stmt.execute("DROP PROCEDURE IF EXISTS INSERT_URL");
                stmt.execute("""
                        CREATE PROCEDURE INSERT_URL (
                            IN PROTO VARCHAR(255),
                            IN DOMAIN_NAME VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                            IN PORT INT,
                            IN URL VARCHAR(255)
                            )
                        BEGIN
                            INSERT IGNORE INTO EC_URL (PROTO,DOMAIN_ID,PORT,URL) SELECT PROTO,ID,PORT,URL FROM EC_DOMAIN WHERE URL_PART=DOMAIN_NAME;
                        END
                        """);
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException("Failed to set up loader", ex);
        }
    }

    public void load(LoaderData data, EdgeUrl[] urls) {
        try (var conn = dataSource.getConnection();
             var insertCall = conn.prepareCall("CALL INSERT_URL(?,?,?,?)");
             var queryCall = conn.prepareStatement("SELECT ID, PROTO, URL FROM EC_URL WHERE DOMAIN_ID=?")
             )
        {
            conn.setAutoCommit(false);
            for (var url : urls) {

                insertCall.setString(1, url.proto);
                insertCall.setString(2, url.domain.toString());
                if (url.port != null) {
                    insertCall.setInt(3, url.port);
                }
                else {
                    insertCall.setNull(3, Types.INTEGER);
                }
                insertCall.setString(4, url.path);
                insertCall.addBatch();
            }
            var ret = insertCall.executeBatch();
            for (int rv = 0; rv < urls.length; rv++) {
                if (ret[rv] < 0 && ret[rv] != SUCCESS_NO_INFO) {
                    logger.warn("load({}) -- bad row count {}", urls[rv], ret[rv]);
                }
            }

            conn.commit();
            conn.setAutoCommit(true);


            var targetDomain = data.getTargetDomain();
            queryCall.setInt(1, data.getDomainId(targetDomain));

            var rsp = queryCall.executeQuery();

            while (rsp.next()) {
                int urlId = rsp.getInt(1);
                String proto = rsp.getString(2);
                String path = rsp.getString(3);

                data.addUrl(new EdgeUrl(proto, targetDomain, null, path), urlId);
            }

        }
        catch (SQLException ex) {
            ex.printStackTrace();
        }
    }
}
