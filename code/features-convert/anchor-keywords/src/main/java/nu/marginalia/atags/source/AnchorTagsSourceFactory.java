package nu.marginalia.atags.source;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.WmsaHome;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.model.EdgeDomain;
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
        if (!Files.exists(atagsPath))
            return dummy();

        List<EdgeDomain> relevantDomains = getRelevantDomains();

        if (relevantDomains.isEmpty())
            return dummy();

        return new AnchorTagsImpl(atagsPath, relevantDomains);
    }

    private AnchorTagsSource dummy() {
        return x -> new DomainLinks();
    }

    // Only get domains that are assigned to this node.  This reduces the amount of data
    // that needs to be loaded into the duckdb instance to a more manageable level, and keeps
    // the memory footprint of the service down.
    private List<EdgeDomain> getRelevantDomains() {
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
