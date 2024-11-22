package nu.marginalia.nodecfg;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.nodecfg.model.NodeConfiguration;
import nu.marginalia.nodecfg.model.NodeProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class NodeConfigurationService {
    private final Logger logger = LoggerFactory.getLogger(NodeConfigurationService.class);

    private final HikariDataSource dataSource;

    @Inject
    public NodeConfigurationService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public NodeConfiguration create(int id, String description, boolean acceptQueries, boolean keepWarcs, NodeProfile nodeProfile) throws SQLException {
        try (var conn = dataSource.getConnection();
             var is = conn.prepareStatement("""
                     INSERT IGNORE INTO NODE_CONFIGURATION(ID, DESCRIPTION, ACCEPT_QUERIES, KEEP_WARCS, NODE_PROFILE) VALUES(?, ?, ?, ?, ?)
                     """)
        )
        {
            is.setInt(1, id);
            is.setString(2, description);
            is.setBoolean(3, acceptQueries);
            is.setBoolean(4, keepWarcs);
            is.setString(5, nodeProfile.name());

            if (is.executeUpdate() <= 0) {
                throw new IllegalStateException("Failed to insert configuration");
            }

            return get(id);
        }
    }

    public List<NodeConfiguration> getAll() {
        try (var conn = dataSource.getConnection();
             var qs = conn.prepareStatement("""
                     SELECT ID, DESCRIPTION, ACCEPT_QUERIES, AUTO_CLEAN, PRECESSION, KEEP_WARCS, NODE_PROFILE, DISABLED
                     FROM NODE_CONFIGURATION
                     """)) {
            var rs = qs.executeQuery();

            List<NodeConfiguration> ret = new ArrayList<>();

            while (rs.next()) {
                ret.add(new NodeConfiguration(
                        rs.getInt("ID"),
                        rs.getString("DESCRIPTION"),
                        rs.getBoolean("ACCEPT_QUERIES"),
                        rs.getBoolean("AUTO_CLEAN"),
                        rs.getBoolean("PRECESSION"),
                        rs.getBoolean("KEEP_WARCS"),
                        NodeProfile.valueOf(rs.getString("NODE_PROFILE")),
                        rs.getBoolean("DISABLED")
                ));
            }
            return ret;
        }
        catch (SQLException ex) {
            logger.warn("Failed to get node configurations", ex);
            return List.of();
        }
    }

    public NodeConfiguration get(int nodeId) throws SQLException {
        try (var conn = dataSource.getConnection();
             var qs = conn.prepareStatement("""
                     SELECT ID, DESCRIPTION, ACCEPT_QUERIES, AUTO_CLEAN, PRECESSION, KEEP_WARCS, NODE_PROFILE, DISABLED
                     FROM NODE_CONFIGURATION
                     WHERE ID=?
                     """)) {
            qs.setInt(1, nodeId);
            var rs = qs.executeQuery();
            if (rs.next()) {
                return new NodeConfiguration(
                        rs.getInt("ID"),
                        rs.getString("DESCRIPTION"),
                        rs.getBoolean("ACCEPT_QUERIES"),
                        rs.getBoolean("AUTO_CLEAN"),
                        rs.getBoolean("PRECESSION"),
                        rs.getBoolean("KEEP_WARCS"),
                        NodeProfile.valueOf(rs.getString("NODE_PROFILE")),
                        rs.getBoolean("DISABLED")
                );
            }
        }

        return null;
    }

    public void save(NodeConfiguration config) throws SQLException {
        try (var conn = dataSource.getConnection();
             var us = conn.prepareStatement("""
                     UPDATE NODE_CONFIGURATION
                     SET DESCRIPTION=?, ACCEPT_QUERIES=?,  AUTO_CLEAN=?, PRECESSION=?, KEEP_WARCS=?, DISABLED=?, NODE_PROFILE=?
                     WHERE ID=?
                     """))
        {
            us.setString(1, config.description());
            us.setBoolean(2, config.acceptQueries());
            us.setBoolean(3, config.autoClean());
            us.setBoolean(4, config.includeInPrecession());
            us.setBoolean(5, config.keepWarcs());
            us.setBoolean(6, config.disabled());
            us.setString(7, config.profile().name());
            us.setInt(8, config.node());

            if (us.executeUpdate() <= 0)
                throw new IllegalStateException("Failed to update configuration");

        }
    }
}
