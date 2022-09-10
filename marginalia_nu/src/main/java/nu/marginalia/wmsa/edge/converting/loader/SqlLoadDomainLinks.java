package nu.marginalia.wmsa.edge.converting.loader;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DomainLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

import static java.sql.Statement.SUCCESS_NO_INFO;

public class SqlLoadDomainLinks {

    private final HikariDataSource dataSource;
    private static final Logger logger = LoggerFactory.getLogger(SqlLoadDomainLinks.class);

    @Inject
    public SqlLoadDomainLinks(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.createStatement()) {
                stmt.execute("DROP PROCEDURE IF EXISTS INSERT_LINK");
                stmt.execute("""
                       CREATE PROCEDURE INSERT_LINK (
                               IN FROM_DOMAIN VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                               IN TO_DOMAIN VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                               )
                       BEGIN
                               INSERT IGNORE INTO EC_DOMAIN_LINK (SOURCE_DOMAIN_ID, DEST_DOMAIN_ID)
                                   SELECT SOURCE.ID,DEST.ID
                                   FROM EC_DOMAIN SOURCE INNER JOIN EC_DOMAIN DEST
                                   ON SOURCE.DOMAIN_NAME=FROM_DOMAIN AND DEST.DOMAIN_NAME=TO_DOMAIN;
                       END
                        """);
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException("Failed to set up loader", ex);
        }
    }

    public void load(LoaderData data, DomainLink[] links) {

        try (var connection = dataSource.getConnection();
             var nukeExistingLinksForDomain =
                     connection.prepareStatement("""
                             DELETE FROM EC_DOMAIN_LINK WHERE SOURCE_DOMAIN_ID=?
                             """);
             var stmt =
                     connection.prepareCall("CALL INSERT_LINK(?,?)"))
        {

            connection.setAutoCommit(false);
            nukeExistingLinksForDomain.setInt(1, data.getDomainId(links[0].from()));
            nukeExistingLinksForDomain.executeUpdate();

            for (DomainLink link : links) {
                stmt.setString(1, link.from().toString());
                stmt.setString(2, link.to().toString());

                stmt.addBatch();
            }

            var ret = stmt.executeBatch();
            for (int rv = 0; rv < links.length; rv++) {
                if (ret[rv] != 1 && ret[rv] != SUCCESS_NO_INFO) {
                    logger.warn("load({}) -- bad row count {}", links[rv], ret[rv]);
                }
            }

            connection.commit();
            connection.setAutoCommit(true);

        }
        catch (SQLException ex) {
            logger.warn("SQL error inserting domain links", ex);
        }

    }
}
