package nu.marginalia.db;

import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import nu.marginalia.model.EdgeDomain;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** A list of domains that are known to be of a certain type */
@Singleton
public class DomainTypes {

    public enum Type {
        BLOG,
        TEST
    };

    private final Logger logger = LoggerFactory.getLogger(DomainTypes.class);

    private final HikariDataSource dataSource;

    @Inject
    public DomainTypes(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Get all domains of a certain type, including domains that are not in the EC_DOMAIN table */
    public List<String> getAllDomainsByType(Type type) {
        List<String> ret = new ArrayList<>();

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                                SELECT DOMAIN_NAME
                                FROM DOMAIN_SELECTION INNER JOIN DOMAIN_SELECTION_TYPE ON DOMAIN_TYPE_ID = DOMAIN_SELECTION_TYPE.ID
                                WHERE DOMAIN_SELECTION_TYPE.NAME = ?
                                """))
        {
            stmt.setString(1, type.name());
            var rs = stmt.executeQuery();
            while (rs.next()) {
                ret.add(rs.getString(1));
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }

        return ret;
    }

    /** Retrieve the domain id of all domains of a certain type,
     * ignoring entries that are not in the EC_DOMAIN table */
    public TIntList getKnownDomainsByType(Type type) {
        TIntList ret = new TIntArrayList();

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                            SELECT EC_DOMAIN.ID
                            FROM DOMAIN_SELECTION
                            INNER JOIN DOMAIN_SELECTION_TYPE ON DOMAIN_TYPE_ID = DOMAIN_SELECTION_TYPE.ID
                            INNER JOIN EC_DOMAIN ON DOMAIN_SELECTION.DOMAIN_NAME = EC_DOMAIN.DOMAIN_NAME
                            WHERE DOMAIN_SELECTION_TYPE.NAME = ?
                            """))
        {
            stmt.setString(1, type.name());
            var rs = stmt.executeQuery();
            while (rs.next()) {
                ret.add(rs.getInt(1));
            }
        }
        catch (SQLException ex) {
            throw new RuntimeException(ex);
        }

        return ret;
    }

    /** Reload the list of domains of a certain type from the source */
    public void reloadDomainsList(Type type) throws IOException, SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                            SELECT SOURCE, ID FROM DOMAIN_SELECTION_TYPE WHERE NAME = ?
                            """);
             var deleteStatement = conn.prepareStatement("""
                            DELETE FROM DOMAIN_SELECTION WHERE DOMAIN_TYPE_ID = ?
                            """);
             var insertStatement = conn.prepareStatement("""
                            INSERT IGNORE INTO DOMAIN_SELECTION (DOMAIN_NAME, DOMAIN_TYPE_ID) VALUES (?, ?)
                            """)
             )
        {
            stmt.setString(1, type.name());
            var rsp = stmt.executeQuery();

            if (!rsp.next()) {
                throw new RuntimeException("No such domain selection type: " + type);
            }

            var source = rsp.getString(1);
            int typeId = rsp.getInt(2);

            List<String> downloadDomains = downloadDomainsList(source);

            try {
                conn.setAutoCommit(false);
                deleteStatement.setInt(1, typeId);
                deleteStatement.executeUpdate();

                for (String domain : downloadDomains) {
                    insertStatement.setString(1, domain);
                    insertStatement.setInt(2, typeId);
                    insertStatement.executeUpdate();
                    // Could use batch insert here, but this executes infrequently, so it's not worth the hassle
                }

                conn.commit();
            }
            catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
            finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private List<String> downloadDomainsList(String source) throws IOException {
        List<String> ret = new ArrayList<>();

        logger.info("Downloading domain list from {}", source);

        try (var br = new BufferedReader(new InputStreamReader(new URL(source).openStream()))) {
            String line;

            while ((line = br.readLine()) != null) {
                line = cleanDomainListLine(line);


                if (isValidDomainListEntry(line))
                    ret.add(line);
            }
        }

        logger.info("-- found {}", ret.size());


        return ret;
    }

    private String cleanDomainListLine(String line) {
        line = line.trim();

        int hashIdx = line.indexOf('#');
        if (hashIdx >= 0)
            line = line.substring(0, hashIdx).trim();

        return line;
    }

    private boolean isValidDomainListEntry(String line) {
        if (line.isBlank())
            return false;
        if (!line.matches("[a-z0-9\\-.]+"))
            return false;

        return true;
    }
}
