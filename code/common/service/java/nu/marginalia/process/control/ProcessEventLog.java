package nu.marginalia.process.control;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.process.ProcessConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

@Singleton
public class ProcessEventLog {
    private final HikariDataSource dataSource;

    private final Logger logger = LoggerFactory.getLogger(ProcessEventLog.class);

    private final String serviceName;
    private final UUID instanceUuid;
    private final String serviceBase;

    @Inject
    public ProcessEventLog(HikariDataSource dataSource, ProcessConfiguration configuration) {
        this.dataSource = dataSource;

        this.serviceName = configuration.processName() + ":" + configuration.node();
        this.instanceUuid = configuration.instanceUuid();
        this.serviceBase = configuration.processName();

        logger.info("Starting service {} instance {}", serviceName, instanceUuid);

        logEvent("PCS-START", serviceName);
    }

    public void logEvent(Class<?> type, String message) {
        logEvent(type.getSimpleName(), message);
    }
    public void logEvent(String type, String message) {

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                        INSERT INTO SERVICE_EVENTLOG(SERVICE_NAME, SERVICE_BASE, INSTANCE, EVENT_TYPE, EVENT_MESSAGE)
                        VALUES (?, ?, ?, ?, ?)
                     """)) {
            stmt.setString(1, serviceName);
            stmt.setString(2, serviceBase);
            stmt.setString(3, instanceUuid.toString());
            stmt.setString(4, type);
            stmt.setString(5, Objects.requireNonNull(message, ""));

            stmt.executeUpdate();
        }
        catch (SQLException ex) {
            logger.error("Failed to log event {}:{}", type, message);
        }
    }
}
