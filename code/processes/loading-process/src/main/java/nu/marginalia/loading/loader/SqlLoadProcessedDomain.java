package nu.marginalia.loading.loader;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.converting.instruction.instructions.DomainLink;
import nu.marginalia.model.EdgeDomain;
import org.apache.commons.lang3.StringUtils;
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
                            IN IP VARCHAR(48))
                        BEGIN
                            DELETE FROM DOMAIN_METADATA WHERE ID=DID;
                            DELETE FROM EC_DOMAIN_LINK WHERE SOURCE_DOMAIN_ID=DID;
                            DELETE FROM EC_URL WHERE DOMAIN_ID = DID;
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

    public void load(LoaderData data, EdgeDomain domain, DomainIndexingState state, String ip) {
        data.setTargetDomain(domain);

        loadDomains.load(data, domain);

        try (var conn = dataSource.getConnection();
             var initCall = conn.prepareCall("CALL INITIALIZE_DOMAIN(?,?,?,?)"))
        {
            initCall.setString(1, state.name());
            initCall.setInt(2, 1 + data.sizeHint / 100);
            initCall.setInt(3, data.getDomainId(domain));
            initCall.setString(4, StringUtils.truncate(ip, 48));
            int rc = initCall.executeUpdate();
            conn.commit();
            if (rc < 1) {
                logger.warn("load({},{}) -- bad rowcount {}", domain, state, rc);
            }
        }
        catch (SQLException ex) {
            logger.warn("SQL error initializing domain", ex);

            if (getClass().desiredAssertionStatus())
                throw new RuntimeException(ex);
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
            conn.commit();
            if (rc != 1) {
                logger.warn("loadAlias({}) - unexpected row count {}", link, rc);
            }
        }
        catch (SQLException ex) {
            logger.warn("SQL error inserting domain alias", ex);

            if (getClass().desiredAssertionStatus())
                throw new RuntimeException(ex);
        }
    }
}
