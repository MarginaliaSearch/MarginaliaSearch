package nu.marginalia.nodecfg;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.nodecfg.model.NodeConfiguration;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class NodeConfigurationService {

    private final HikariDataSource dataSource;

    @Inject
    public NodeConfigurationService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public NodeConfiguration create(int id, String description, boolean acceptQueries) throws SQLException {
        try (var conn = dataSource.getConnection();
             var is = conn.prepareStatement("""
                     INSERT INTO NODE_CONFIGURATION(ID, DESCRIPTION, ACCEPT_QUERIES) VALUES(?, ?, ?)
                     """)
        )
        {
            is.setInt(1, id);
            is.setString(2, description);
            is.setBoolean(3, acceptQueries);

            if (is.executeUpdate() <= 0) {
                throw new IllegalStateException("Failed to insert configuration");
            }

            return get(id);
        }
    }

    public List<NodeConfiguration> getAll() throws SQLException {
        try (var conn = dataSource.getConnection();
             var qs = conn.prepareStatement("""
                     SELECT ID, DESCRIPTION, ACCEPT_QUERIES, AUTO_CLEAN, PRECESSION, DISABLED
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
                        rs.getBoolean("DISABLED")
                ));
            }
            return ret;
        }
    }

    public NodeConfiguration get(int nodeId) throws SQLException {
        try (var conn = dataSource.getConnection();
             var qs = conn.prepareStatement("""
                     SELECT ID, DESCRIPTION, ACCEPT_QUERIES, AUTO_CLEAN, PRECESSION, DISABLED
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
                     SET DESCRIPTION=?, ACCEPT_QUERIES=?,  AUTO_CLEAN=?, PRECESSION=?, DISABLED=?
                     WHERE ID=?
                     """))
        {
            us.setString(1, config.description());
            us.setBoolean(2, config.acceptQueries());
            us.setBoolean(3, config.autoClean());
            us.setBoolean(4, config.includeInPrecession());
            us.setBoolean(5, config.disabled());
            us.setInt(6, config.node());

            if (us.executeUpdate() <= 0)
                throw new IllegalStateException("Failed to update configuration");

        }
    }
}
