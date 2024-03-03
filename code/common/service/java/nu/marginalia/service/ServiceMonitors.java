package nu.marginalia.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Singleton
public class ServiceMonitors {
    private final HikariDataSource dataSource;
    private static final Logger logger = LoggerFactory.getLogger(ServiceMonitors.class);

    private final Set<ServiceNode> runningServices = new HashSet<>();
    private final Set<Runnable> callbacks = new HashSet<>();

    private final int heartbeatInterval = Integer.getInteger("mcp.heartbeat.interval", 5);

    private volatile boolean running;

    @Inject
    public ServiceMonitors(HikariDataSource dataSource) {
        this.dataSource = dataSource;

        var runThread = new Thread(this::run, "service monitor");
        runThread.setDaemon(true);
        runThread.start();
    }

    public void subscribe(Runnable callback) {
        synchronized (callbacks) {
            callbacks.add(callback);
        }
    }
    public void unsubscribe(Runnable callback) {
        synchronized (callbacks) {
            callbacks.remove(callback);
        }
    }

    public void run() {
        if (running) {
            return;
        }
        else {
            running = true;
        }

        while (running) {
            if (updateRunningServices()) {
                runCallbacks();
            }

            try {
                TimeUnit.SECONDS.sleep(heartbeatInterval);
            }
            catch (InterruptedException ex) {
                logger.warn("ServiceMonitors interrupted", ex);
                running = false;
            }
        }
    }

    private void runCallbacks() {
        synchronized (callbacks) {
            for (var callback : callbacks) {
                synchronized (runningServices) {
                    callback.run();
                }
            }
        }
    }

    private boolean updateRunningServices() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                    SELECT SERVICE_NAME, TIMESTAMPDIFF(SECOND, HEARTBEAT_TIME, CURRENT_TIMESTAMP(6))
                    FROM SERVICE_HEARTBEAT
                    WHERE ALIVE=1
                    """)) {
            try (var rs = stmt.executeQuery()) {
                Set<ServiceNode> newRunningServices = new HashSet<>(10);
                while (rs.next()) {
                    ServiceNode svc = ServiceNode.parse(rs.getString(1));
                    int dtime = rs.getInt(2);
                    if (dtime < 2.5 * heartbeatInterval) {
                        newRunningServices.add(svc);
                    }
                }

                boolean changed;

                synchronized (runningServices) {
                    changed = !Objects.equals(runningServices, newRunningServices);

                    runningServices.clear();
                    runningServices.addAll(newRunningServices);
                }

                return changed;
            }
        }
        catch (SQLException ex) {
            logger.warn("Failed to update running services", ex);
        }

        return false;
    }

    public boolean isServiceUp(ServiceId serviceId, int node) {
        synchronized (runningServices) {
            return runningServices.contains(new ServiceNode(serviceId.serviceName, node));
        }
    }

    public List<ServiceNode> getRunningServices() {
        List<ServiceNode> ret = new ArrayList<>(ServiceId.values().length);

        synchronized (runningServices) {
            ret.addAll(runningServices);
        }

        return ret;
    }

    public record ServiceNode(String service, int node) {
        public static ServiceNode parse(String serviceName) {

            if (serviceName.contains(":")) {
                String[] parts = serviceName.split(":", 2);
                try {
                    return new ServiceNode(parts[0], Integer.parseInt(parts[1]));
                }
                catch (NumberFormatException ex) {
                    logger.warn("Failed to parse serviceName '" + serviceName + "'", ex);
                    //fallthrough
                }
            }

            return new ServiceNode(serviceName, -1);
        }
    }
}
