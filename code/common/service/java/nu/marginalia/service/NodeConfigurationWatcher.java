package nu.marginalia.service;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NodeConfigurationWatcher implements NodeConfigurationWatcherIf {
    private static final Logger logger = LoggerFactory.getLogger(NodeConfigurationWatcher.class);
    private final HikariDataSource dataSource;

    private volatile List<Integer> queryNodes = new ArrayList<>();

    @Inject
    public NodeConfigurationWatcher(HikariDataSource dataSource) {
        this.dataSource = dataSource;

        // Perform the first poll synchronously so that getQueryNodes()
        // returns valid data immediately after construction
        queryNodes = pollQueryNodes();

        var watcherThread = new Thread(this::pollConfiguration, "Node Configuration Watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    private List<Integer> pollQueryNodes() {
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

        return goodNodes;
    }

    private void pollConfiguration() {
        for (;;) {
            try {
                TimeUnit.SECONDS.sleep(10);
            }
            catch (InterruptedException ex) {
                return;
            }

            queryNodes = pollQueryNodes();
        }
    }

    @Override
    public List<Integer> getQueryNodes() {
        return queryNodes;
    }
}
