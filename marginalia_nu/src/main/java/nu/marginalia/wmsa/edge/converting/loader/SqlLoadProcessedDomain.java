package nu.marginalia.wmsa.edge.converting.loader;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DomainLink;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

public class SqlLoadProcessedDomain {
    private final HikariDataSource dataSource;
    private final SqlLoadDomains loadDomains;
    private static final Logger logger = LoggerFactory.getLogger(SqlLoadProcessedDomain.class);
    @Inject
    public SqlLoadProcessedDomain(HikariDataSource dataSource, SqlLoadDomains loadDomains) {
        this.dataSource = dataSource;
        this.loadDomains = loadDomains;


        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.createStatement()) {
                stmt.execute("DROP PROCEDURE IF EXISTS INITIALIZE_DOMAIN");
                stmt.execute("""
                        CREATE PROCEDURE INITIALIZE_DOMAIN (
                            IN ST ENUM('ACTIVE', 'EXHAUSTED', 'SPECIAL', 'SOCIAL_MEDIA', 'BLOCKED', 'REDIR', 'ERROR', 'UNKNOWN'),
                            IN IDX INT,
                            IN DID INT,
                            IN IP VARCHAR(32))
                        BEGIN
                            UPDATE EC_DOMAIN SET INDEX_DATE=NOW(), STATE=ST, DOMAIN_ALIAS=NULL, INDEXED=GREATEST(INDEXED,IDX), IP=IP WHERE ID=DID;
                            DELETE FROM EC_DOMAIN_LINK WHERE SOURCE_DOMAIN_ID=DID;
                        END
                        """);
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException("Failed to set up loader", ex);
        }
    }

    public void load(LoaderData data, EdgeDomain domain, EdgeDomainIndexingState state, String ip) {
        data.setTargetDomain(domain);

        loadDomains.load(data, domain);

        try (var conn = dataSource.getConnection();
             var initCall = conn.prepareCall("CALL INITIALIZE_DOMAIN(?,?,?,?)"))
        {
            initCall.setString(1, state.name());
            initCall.setInt(2, 1 + data.sizeHint / 100);
            initCall.setInt(3, data.getDomainId(domain));
            initCall.setString(4, ip);
            int rc = initCall.executeUpdate();
            if (rc < 1) {
                logger.warn("load({},{}) -- bad rowcount {}", domain, state, rc);
            }
        }
        catch (SQLException ex) {
            logger.warn("SQL error initializing domain", ex);
        }

    }

    public void loadAlias(LoaderData data, DomainLink link) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                     UPDATE EC_DOMAIN TARGET 
                            INNER JOIN EC_DOMAIN ALIAS ON ALIAS.DOMAIN_NAME=? 
                            SET TARGET.DOMAIN_ALIAS=ALIAS.ID
                            WHERE TARGET.DOMAIN_NAME=?
                     """)) {
            stmt.setString(1, link.to().toString());
            stmt.setString(2, link.from().toString());
            int rc = stmt.executeUpdate();
            if (rc != 1) {
                logger.warn("loadAlias({}) - unexpected row count {}", link, rc);
            }
        }
        catch (SQLException ex) {
            logger.warn("SQL error inserting domain alias", ex);
        }
    }
}
