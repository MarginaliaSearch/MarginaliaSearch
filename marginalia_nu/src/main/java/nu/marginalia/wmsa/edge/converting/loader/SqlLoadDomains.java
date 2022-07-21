package nu.marginalia.wmsa.edge.converting.loader;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

import static java.sql.Statement.SUCCESS_NO_INFO;

public class SqlLoadDomains {

    private final HikariDataSource dataSource;
    private static final Logger logger = LoggerFactory.getLogger(SqlLoadDomains.class);

    @Inject
    public SqlLoadDomains(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.createStatement()) {
                stmt.execute("DROP PROCEDURE IF EXISTS INSERT_DOMAIN");
                stmt.execute("""
                        CREATE PROCEDURE INSERT_DOMAIN (
                            IN DOMAIN_NAME VARCHAR(255),
                            IN TOP_DOMAIN VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci)
                        BEGIN
                            INSERT IGNORE INTO EC_DOMAIN(DOMAIN_NAME, DOMAIN_TOP) VALUES (DOMAIN_NAME, TOP_DOMAIN);
                        END
                        """);
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException("Failed to set up loader", ex);
        }
    }

    public void load(LoaderData data, EdgeDomain domain) {

        try (var connection = dataSource.getConnection()) {
            try (var insertCall = connection.prepareCall("CALL INSERT_DOMAIN(?,?)")) {
                insertCall.setString(1, domain.toString());
                insertCall.setString(2, domain.domain);
                insertCall.addBatch();

                var ret = insertCall.executeUpdate();
                if (ret < 0) {
                    logger.warn("load({}) -- bad row count {}", domain, ret);
                }

                findIdForTargetDomain(connection, data);
            }
        }
        catch (SQLException ex) {
            logger.warn("SQL error inserting domain", ex);
        }


    }

    public void load(LoaderData data, EdgeDomain[] domains) {

        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try (var insertCall = connection.prepareCall("CALL INSERT_DOMAIN(?,?)")) {

                for (var domain : domains) {
                    insertCall.setString(1, domain.toString());
                    insertCall.setString(2, domain.domain);
                    insertCall.addBatch();
                }
                var ret = insertCall.executeBatch();

                for (int rv = 0; rv < domains.length; rv++) {
                    if (ret[rv] < 0 && ret[rv] != SUCCESS_NO_INFO) {
                        logger.warn("load({}) -- bad row count {}", domains[rv], ret[rv]);
                    }
                }

            }
            connection.commit();
            connection.setAutoCommit(true);
            findIdForTargetDomain(connection, data);
        }
        catch (SQLException ex) {
            logger.warn("SQL error inserting domains", ex);
        }
    }

    void findIdForTargetDomain(Connection connection, LoaderData data) {
        if (data.getTargetDomain() == null || data.getDomainId(data.getTargetDomain()) > 0) {
            return;
        }

        try (var query = connection.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE DOMAIN_NAME=?"))
        {

            var targetDomain = data.getTargetDomain();
            query.setString(1, targetDomain.toString());
            var rsp = query.executeQuery();
            if (rsp.next()) {
                data.addDomain(targetDomain, rsp.getInt(1));
            }
            else {
                logger.warn("load() -- could not find ID for target domain {}", targetDomain);
            }
        }
        catch (SQLException ex) {
            logger.warn("SQL error finding id for domain", ex);
        }
    }
}
