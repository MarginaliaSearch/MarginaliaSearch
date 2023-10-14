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

    public NodeConfiguration create(String description, boolean acceptQueries) throws SQLException {
        try (var conn = dataSource.getConnection();
             var is = conn.prepareStatement("""
                     INSERT INTO NODE_CONFIGURATION(DESCRIPTION, ACCEPT_QUERIES) VALUES(?, ?)
                     """);
             var qs = conn.prepareStatement("""
                     SELECT LAST_INSERT_ID()
                     """))
        {
            is.setString(1, description);
            is.setBoolean(2, acceptQueries);

            if (is.executeUpdate() <= 0) {
                throw new IllegalStateException("Failed to insert configuration");
            }

            var rs = qs.executeQuery();

            if (rs.next()) {
                return get(rs.getInt(1));
            }

            throw new AssertionError("No LAST_INSERT_ID()");
        }
    }

    public List<NodeConfiguration> getAll() throws SQLException {
        try (var conn = dataSource.getConnection();
             var qs = conn.prepareStatement("""
                     SELECT ID, DESCRIPTION, ACCEPT_QUERIES, DISABLED
                     FROM NODE_CONFIGURATION
                     """)) {
            var rs = qs.executeQuery();

            List<NodeConfiguration> ret = new ArrayList<>();

            while (rs.next()) {
                ret.add(new NodeConfiguration(
                        rs.getInt("ID"),
                        rs.getString("DESCRIPTION"),
                        rs.getBoolean("ACCEPT_QUERIES"),
                        rs.getBoolean("DISABLED")
                ));
            }
            return ret;
        }
    }

    public NodeConfiguration get(int nodeId) throws SQLException {
        try (var conn = dataSource.getConnection();
             var qs = conn.prepareStatement("""
                     SELECT ID, DESCRIPTION, ACCEPT_QUERIES, DISABLED
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
                     SET DESCRIPTION=?, ACCEPT_QUERIES=?, DISABLED=?
                     WHERE ID=?
                     """))
        {
            us.setString(1, config.description());
            us.setBoolean(2, config.acceptQueries());
            us.setBoolean(3, config.disabled());
            us.setInt(4, config.node());

            if (us.executeUpdate() <= 0)
                throw new IllegalStateException("Failed to update configuration");

        }
    }
}
