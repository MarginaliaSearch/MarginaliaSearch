package nu.marginalia.query.svc;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.nodecfg.NodeConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NodeConfigurationWatcher {
    private static final Logger logger = LoggerFactory.getLogger(NodeConfigurationWatcher.class);

    private volatile List<Integer> queryNodes = new ArrayList<>();
    private final NodeConfigurationService configurationService;

    @Inject
    public NodeConfigurationWatcher(NodeConfigurationService configurationService) {
        this.configurationService = configurationService;

        var watcherThread = new Thread(this::pollConfiguration, "Node Configuration Watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    @SneakyThrows
    private void pollConfiguration() {
        for (;;) {
            List<Integer> goodNodes = new ArrayList<>();
            try {
                for (var cfg : configurationService.getAll()) {

                    if (!cfg.disabled() && cfg.acceptQueries()) {
                        goodNodes.add(cfg.node());
                    }
                }
                queryNodes = goodNodes;
            }
            catch (SQLException ex) {
                logger.warn("Failed to update node configurations", ex);
            }

            TimeUnit.SECONDS.sleep(10);
        }
    }

    public List<Integer> getQueryNodes() {
        return queryNodes;
    }
}
