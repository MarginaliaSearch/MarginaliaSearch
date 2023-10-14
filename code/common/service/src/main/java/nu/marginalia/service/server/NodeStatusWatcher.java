package nu.marginalia.service.server;

import com.google.inject.name.Named;
import jakarta.inject.Inject;
import nu.marginalia.nodecfg.NodeConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** The node status watcher ensures that services that can be run on multiple nodes
 * find the configuration they expect, and kills the services when a node is disabled.
 * <br><br>
 * Install the watcher by adding to the Main class an
 * <br>
 * <code>injector.getInstance(NodeStatusWatcher.class);</code>
 * <br>
 * before anything else is initialized.
 */
public class NodeStatusWatcher {
    private static final Logger logger = LoggerFactory.getLogger(NodeStatusWatcher.class);

    private final NodeConfigurationService configurationService;
    private final int nodeId;

    private final Duration pollDuration = Duration.ofSeconds(15);

    @Inject
    public NodeStatusWatcher(NodeConfigurationService configurationService,
                             @Named("wmsa-system-node") Integer nodeId) throws InterruptedException {
        this.configurationService = configurationService;

        this.nodeId = nodeId;

        awaitConfiguration();


        var watcherThread = new Thread(this::watcher, "node watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    /** Wait for the presence of an enabled NodeConfiguration before permitting the service to start */
    private void awaitConfiguration() throws InterruptedException {

        boolean complained = false;

        for (;;) {
            try {
                var config = configurationService.get(nodeId);
                if (null != config && !config.disabled()) {
                    return;
                }
                else if (!complained) {
                    logger.info("Waiting for node configuration, id = {}", nodeId);
                    complained = true;
                }
            }
            catch (SQLException ex) {
                logger.error("Error updating node status", ex);
            }

            TimeUnit.SECONDS.sleep(pollDuration.toSeconds());
        }

    }

    /** Look for changes in the configuration and kill the service if the corresponding
     * NodeConfiguration is set to be disabled.
     */
    private void watcher() {
        for (;;) {
            try {
                TimeUnit.SECONDS.sleep(pollDuration.toSeconds());
            }
            catch (InterruptedException ex) {
                logger.error("Watcher thread interrupted", ex);
                return;
            }

            try {
                var config = configurationService.get(nodeId);
                if (null == config || config.disabled()) {
                    logger.info("Current node disabled!! Shutting down!");
                    System.exit(0);
                }
            }
            catch (SQLException ex) {
                logger.error("Error updating node status", ex);
            }

        }
    }

}
