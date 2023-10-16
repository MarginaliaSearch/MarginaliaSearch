package nu.marginalia.client;

import lombok.SneakyThrows;
import nu.marginalia.client.route.ServiceRoutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/** Keep tabs on which endpoints are accessible via polling.  This permits us to reduce the chances of
 * synchronous requests blocking on timeout.
 */
public class EndpointLivenessMonitor {

    private final ConcurrentHashMap<Integer, Boolean> alivenessMap = new ConcurrentHashMap<>();
    private final AbstractClient client;
    private final ServiceRoutes serviceRoutes;

    private static final Logger logger = LoggerFactory.getLogger(EndpointLivenessMonitor.class);
    private static Thread daemonThread;

    public EndpointLivenessMonitor(AbstractClient client) {
        this.client = client;
        this.serviceRoutes = client.serviceRoutes;

        daemonThread = new Thread(this::run, client.getClass().getSimpleName()+":Liveness");
        daemonThread.setDaemon(true);
        daemonThread.start();
    }

    @SneakyThrows
    public void run() {
        Thread.sleep(100); // Wait for initialization
        try {
            while (!Thread.interrupted()) {
                if (updateLivenessMap()) {
                    synchronized (this) {
                        wait(1000);
                    }
                }
                else Thread.sleep(100);
            }
        } catch (InterruptedException ex) {
            // nothing to see here
        }
    }

    private boolean updateLivenessMap() {
        boolean allAlive = true;

        for (int node : serviceRoutes.getNodes()) {
            allAlive &= alivenessMap.compute(node, this::isResponsive);
        }

        return allAlive;
    }

    private boolean isResponsive(int node, Boolean oldValue) {
        try {
            boolean wasAlive = Boolean.TRUE.equals(oldValue);
            boolean isAlive = client.isResponsive(node);
            if (wasAlive != isAlive) {
                logger.info("Liveness change {}:{} -- {}", client.name(), node, isAlive ? "UP":"DOWN");
            }
            return isAlive;
        }
        catch (Exception ex) {
            logger.warn("Oops", ex);
            return false;
        }
    }

    public boolean isAlive(int node) {
        // compute-if-absence ensures we do a synchronous status check if this is a cold start,
        // that way we don't have to wait for the polling loop to find out if the service is up
        return alivenessMap.computeIfAbsent(node, client::isResponsive);
    }


    public void close() {
        daemonThread.interrupt();
    }
}
