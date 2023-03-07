package nu.marginalia.loading.loader;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.model.EdgeDomain;
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
                connection.setAutoCommit(false);
                insertCall.setString(1, domain.toString());
                insertCall.setString(2, domain.domain);

                var ret = insertCall.executeUpdate();
                connection.commit();
                if (ret < 0) {
                    logger.warn("load({}) -- bad return status {}", domain, ret);
                }

                findIdForDomain(connection, data, domain);
                connection.setAutoCommit(true);
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


                int cnt = 0; int batchOffset = 0;
                for (var domain : domains) {
                    insertCall.setString(1, domain.toString());
                    insertCall.setString(2, domain.domain);
                    insertCall.addBatch();

                    if (++cnt == 1000) {
                        var ret = insertCall.executeBatch();
                        connection.commit();

                        for (int rv = 0; rv < cnt; rv++) {
                            if (ret[rv] < 0 && ret[rv] != SUCCESS_NO_INFO) {
                                logger.warn("load({}) -- bad row count {}", domains[batchOffset + rv], ret[rv]);
                            }
                        }

                        cnt = 0;
                        batchOffset += 1000;
                    }
                }
                if (cnt > 0) {
                    var ret = insertCall.executeBatch();
                    connection.commit();
                    for (int rv = 0; rv < cnt; rv++) {
                        if (ret[rv] < 0 && ret[rv] != SUCCESS_NO_INFO) {
                            logger.warn("load({}) -- bad row count {}", domains[batchOffset + rv], ret[rv]);
                        }
                    }
                }

            }
            connection.commit();
            connection.setAutoCommit(true);
            findIdForDomain(connection, data, domains);
        }
        catch (SQLException ex) {
            logger.warn("SQL error inserting domains", ex);
        }
    }

    void findIdForDomain(Connection connection, LoaderData data, EdgeDomain... domains) {
        if (data.getTargetDomain() == null || data.getDomainId(data.getTargetDomain()) > 0) {
            return;
        }

        try (var query = connection.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE DOMAIN_NAME=?"))
        {

            for (var domain : domains) {
                if (data.getDomainId(domain) > 0)
                    continue;

                query.setString(1, domain.toString());
                var rsp = query.executeQuery();
                if (rsp.next()) {
                    data.addDomain(domain, rsp.getInt(1));
                } else {
                    logger.warn("load() -- could not find ID for target domain {}", domain);
                }
            }
        }
        catch (SQLException ex) {
            logger.warn("SQL error finding id for domain", ex);
        }
    }

    void loadAdditionalDomains(Connection connection, LoaderData data, EdgeDomain[] domains) {

        try (var query = connection.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE DOMAIN_NAME=?"))
        {
            for (var domain : domains) {

                if (data.getDomainId(domain) == 0) continue;

                query.setString(1, domain.toString());
                var rsp = query.executeQuery();
                if (rsp.next()) {
                    data.addDomain(domain, rsp.getInt(1));
                } else {
                    logger.warn("load() -- could not find ID for target domain {}", domain);
                }
            }
        }
        catch (SQLException ex) {
            logger.warn("SQL error finding id for domain", ex);
        }
    }
}
