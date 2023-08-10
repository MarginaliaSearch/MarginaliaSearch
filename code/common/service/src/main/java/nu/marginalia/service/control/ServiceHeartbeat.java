package nu.marginalia.service.control;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

/** This service sends a heartbeat to the database every 5 seconds,
 * updating the control service with the liveness information for the service.
 */
@Singleton
public class ServiceHeartbeat {
    private final Logger logger = LoggerFactory.getLogger(ServiceHeartbeat.class);
    private final String serviceName;
    private final String serviceBase;
    private final String instanceUUID;
    private final ServiceConfiguration configuration;
    private final ServiceEventLog eventLog;
    private final HikariDataSource dataSource;


    private final Thread runnerThread;
    private final int heartbeatInterval = Integer.getInteger("mcp.heartbeat.interval", 5);

    private volatile boolean running = false;

    @Inject
    public ServiceHeartbeat(ServiceConfiguration configuration,
                            ServiceEventLog eventLog,
                            HikariDataSource dataSource)
    {
        this.serviceName = configuration.serviceName() + ":" + configuration.node();
        this.serviceBase = configuration.serviceName();
        this.configuration = configuration;
        this.eventLog = eventLog;
        this.dataSource = dataSource;

        this.instanceUUID = configuration.instanceUuid().toString();

        runnerThread = new Thread(this::run);

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutDown));
    }

    public <T extends Enum<T>> ServiceTaskHeartbeat<T> createServiceTaskHeartbeat(Class<T> steps, String processName) {
        return new ServiceTaskHeartbeat<>(steps, configuration, processName, eventLog, dataSource);
    }


    public void start() {
        if (!running) {
            runnerThread.start();
        }
    }

    public void shutDown() {
        if (!running)
            return;

        running = false;

        try {
            runnerThread.join();
            heartbeatStop();
        }
        catch (InterruptedException|SQLException ex) {
            logger.warn("ServiceHeartbeat shutdown failed", ex);
        }
    }

    private void run() {
        if (!running)
            running = true;
        else
            return;

        try {
            heartbeatInit();

            while (running) {

                try {
                    heartbeatUpdate();
                }
                catch (SQLException ex) {
                    logger.warn("ServiceHeartbeat failed to update", ex);
                }

                TimeUnit.SECONDS.sleep(heartbeatInterval);
            }
        }
        catch (InterruptedException|SQLException ex) {
            logger.error("ServiceHeartbeat caught irrecoverable exception, killing service", ex);
            System.exit(255);
        }
    }

    private void heartbeatInit() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement(
                    """
                        INSERT INTO SERVICE_HEARTBEAT (SERVICE_NAME, SERVICE_BASE, INSTANCE, HEARTBEAT_TIME, ALIVE)
                        VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), 1)
                        ON DUPLICATE KEY UPDATE
                            INSTANCE = ?,
                            HEARTBEAT_TIME = CURRENT_TIMESTAMP(6),
                            ALIVE = 1
                        """
                    ))
            {
                stmt.setString(1, serviceName);
                stmt.setString(2, serviceBase);
                stmt.setString(3, instanceUUID);
                stmt.setString(4, instanceUUID);
                stmt.executeUpdate();
            }
        }
    }

    private void heartbeatUpdate() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement(
                    """
                        UPDATE SERVICE_HEARTBEAT
                        SET HEARTBEAT_TIME = CURRENT_TIMESTAMP(6)
                        WHERE INSTANCE = ? AND ALIVE = 1
                        """)
            )
            {
                stmt.setString(1, instanceUUID);
                stmt.executeUpdate();
            }
        }
    }

    private void heartbeatStop() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement(
                    """
                        UPDATE SERVICE_HEARTBEAT
                        SET HEARTBEAT_TIME = CURRENT_TIMESTAMP(6), ALIVE = 0
                        WHERE INSTANCE = ?
                        """)
            )
            {
                stmt.setString(1, instanceUUID);
                stmt.executeUpdate();
            }
        }
    }

}
