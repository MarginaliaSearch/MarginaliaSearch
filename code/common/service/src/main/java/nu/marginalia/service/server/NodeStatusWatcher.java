package nu.marginalia.service.server;

import com.google.inject.name.Named;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageBaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
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
    private final FileStorageService fileStorageService;
    private final MqPersistence persistence;
    private final int nodeId;

    private final Duration pollDuration = Duration.ofSeconds(15);

    @Inject
    public NodeStatusWatcher(NodeConfigurationService configurationService,
                             FileStorageService fileStorageService,
                             MqPersistence persistence,
                             @Named("wmsa-system-node") Integer nodeId) {
        this.configurationService = configurationService;
        this.fileStorageService = fileStorageService;
        this.persistence = persistence;

        this.nodeId = nodeId;

        if (!isConfigured()) {
           setupNode();
        }


        var watcherThread = new Thread(this::watcher, "node watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    private void setupNode() {
        try {
            configurationService.create(nodeId, "Node " + nodeId, nodeId == 1);
            fileStorageService.createStorageBase("Index Data", Path.of("/idx"), nodeId, FileStorageBaseType.CURRENT);
            fileStorageService.createStorageBase("Index Backups", Path.of("/backup"), nodeId, FileStorageBaseType.BACKUP);
            fileStorageService.createStorageBase("Crawl Data", Path.of("/storage"), nodeId, FileStorageBaseType.STORAGE);
            fileStorageService.createStorageBase("Work Area", Path.of("/work"), nodeId, FileStorageBaseType.WORK);

            persistence.sendNewMessage("executor-service:"+nodeId,
                    null,
                    null,
                    "FIRST-BOOT",
                    "",
                    null);
        }
        catch (IllegalStateException ex) {
            // There is a slight chance of a race condition between the index and executor services both trying to run this,
            // at the same time.  Thanks to ACID, only one of them will succeed in creating the node, and the other will throw
            // IllegalStateException.  This is fine!
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @SneakyThrows
    private boolean isConfigured() {
        var configuration = configurationService.get(nodeId);
        return configuration != null;
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
