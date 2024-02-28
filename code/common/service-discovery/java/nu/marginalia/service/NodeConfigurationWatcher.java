package nu.marginalia.service;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NodeConfigurationWatcher {
    private static final Logger logger = LoggerFactory.getLogger(NodeConfigurationWatcher.class);
    private final HikariDataSource dataSource;

    private volatile List<Integer> queryNodes = new ArrayList<>();

    @Inject
    public NodeConfigurationWatcher(HikariDataSource dataSource) {
        this.dataSource = dataSource;

        var watcherThread = new Thread(this::pollConfiguration, "Node Configuration Watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    @SneakyThrows
    private void pollConfiguration() {
        for (;;) {
            List<Integer> goodNodes = new ArrayList<>();

            try (var conn = dataSource.getConnection()) {
                var stmt = conn.prepareStatement("""
                    SELECT ID FROM NODE_CONFIGURATION
                    WHERE ACCEPT_QUERIES AND NOT DISABLED 
                    """);
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    goodNodes.add(rs.getInt(1));
                }
            }
            catch (SQLException ex) {
                logger.error("Error polling node configuration", ex);
            }

            queryNodes = goodNodes;

            TimeUnit.SECONDS.sleep(10);
        }
    }

    public List<Integer> getQueryNodes() {
        return queryNodes;
    }
}
