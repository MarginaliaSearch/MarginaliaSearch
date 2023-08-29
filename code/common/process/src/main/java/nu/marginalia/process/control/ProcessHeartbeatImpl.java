package nu.marginalia.process.control;


import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.ProcessConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

/** This service sends a heartbeat to the database every 5 seconds.
 */
@Singleton
public class ProcessHeartbeatImpl implements ProcessHeartbeat {
    private final Logger logger = LoggerFactory.getLogger(ProcessHeartbeatImpl.class);
    private final String processName;
    private final String processBase;
    private final String instanceUUID;
    @org.jetbrains.annotations.NotNull
    private final ProcessConfiguration configuration;
    private final HikariDataSource dataSource;


    private final Thread runnerThread;
    private final int heartbeatInterval = Integer.getInteger("mcp.heartbeat.interval", 1);

    private volatile boolean running = false;

    private volatile int progress = -1;

    @Inject
    public ProcessHeartbeatImpl(ProcessConfiguration configuration,
                                HikariDataSource dataSource)
    {
        this.processName = configuration.processName() + ":" + configuration.node();
        this.processBase = configuration.processName();
        this.configuration = configuration;
        this.dataSource = dataSource;

        this.instanceUUID = configuration.instanceUuid().toString();

        runnerThread = new Thread(this::run);

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutDown));
    }


    @Override
    public <T extends Enum<T>> ProcessTaskHeartbeat<T> createProcessTaskHeartbeat(Class<T> steps, String processName) {
        return new ProcessTaskHeartbeatImpl<>(steps, configuration, processName, dataSource);
    }

    @Override
    public ProcessAdHocTaskHeartbeat createAdHocTaskHeartbeat(String processName) {
        return new ProcessAdHocTaskHeartbeatImpl(configuration, processName, dataSource);
    }

    @Override
    public void setProgress(double progress) {
        this.progress = (int) (progress * 100);
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
                        INSERT INTO PROCESS_HEARTBEAT (PROCESS_NAME, PROCESS_BASE, INSTANCE, HEARTBEAT_TIME, STATUS)
                        VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), 'STARTING')
                        ON DUPLICATE KEY UPDATE
                            INSTANCE = ?,
                            HEARTBEAT_TIME = CURRENT_TIMESTAMP(6),
                            STATUS = 'STARTING'
                        """
            ))
            {
                stmt.setString(1, processName);
                stmt.setString(2, processBase);
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
                        UPDATE PROCESS_HEARTBEAT
                        SET HEARTBEAT_TIME = CURRENT_TIMESTAMP(6), STATUS = 'RUNNING', PROGRESS = ?
                        WHERE INSTANCE = ?
                        """)
            )
            {
                stmt.setInt(1, progress);
                stmt.setString(2, instanceUUID);
                stmt.executeUpdate();
            }
        }
    }

    private void heartbeatStop() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement(
                    """
                        UPDATE PROCESS_HEARTBEAT
                        SET HEARTBEAT_TIME = CURRENT_TIMESTAMP(6), STATUS='STOPPED', PROGRESS=?
                        WHERE INSTANCE = ?
                        """)
            )
            {
                stmt.setInt(1, progress);
                stmt.setString( 2, instanceUUID);
                stmt.executeUpdate();
            }
        }
    }
}

