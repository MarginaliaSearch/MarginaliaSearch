package nu.marginalia.atags.source;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.WmsaHome;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.process.ProcessConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AnchorTagsSourceFactory {
    private final Path atagsPath;
    private final int nodeId;
    private final HikariDataSource dataSource;
    private static final Logger logger = LoggerFactory.getLogger(AnchorTagsSourceFactory.class);

    @Inject
    public AnchorTagsSourceFactory(HikariDataSource dataSource,
                                   ProcessConfiguration config)
    {
        this.dataSource = dataSource;
        this.atagsPath = WmsaHome.getAtagsPath();
        this.nodeId = config.node();
    }

    public AnchorTagsSource create() throws SQLException {
        try {
            return create(getRelevantDomainsByNodeAffinity());
        }
        catch (Exception e) {
            // likely a test environment
            logger.warn("Failed to create anchor tags source", e);
            return domain -> new DomainLinks();
        }
    }

    public AnchorTagsSource create(List<EdgeDomain> relevantDomains) throws SQLException {
        if (!Files.exists(atagsPath)) {
            logger.info("Omitting anchor tag data because '{}' does not exist, or is not reachable from the crawler process", atagsPath);

            return domain -> new DomainLinks();
        }

        if (relevantDomains.isEmpty()) {
            logger.info("Omitting anchor tag data because no relevant domains were provided");

            return domain -> new DomainLinks();
        }

        return new AnchorTagsImpl(atagsPath, relevantDomains);
    }

    // Only get domains that are assigned to this node.  This reduces the amount of data
    // that needs to be loaded into the duckdb instance to a more manageable level, and keeps
    // the memory footprint of the service down.
    private List<EdgeDomain> getRelevantDomainsByNodeAffinity() {
        if (dataSource == null)
            return List.of();

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                        SELECT DOMAIN_NAME
                        FROM WMSA_prod.EC_DOMAIN
                        WHERE NODE_AFFINITY = ?
                        """))
        {
            stmt.setInt(1, nodeId);
            var rs = stmt.executeQuery();
            var ret = new ArrayList<EdgeDomain>();
            while (rs.next()) {
                ret.add(new EdgeDomain(rs.getString(1)));
            }
            return ret;
        } catch (Exception e) {
            logger.warn("Failed to get relevant domains for node id " + nodeId, e);
            return List.of();
        }
    }


}
