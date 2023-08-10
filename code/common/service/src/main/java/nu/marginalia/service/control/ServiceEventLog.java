package nu.marginalia.service.control;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.service.module.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

@Singleton
public class ServiceEventLog {
    private final HikariDataSource dataSource;

    private final Logger logger = LoggerFactory.getLogger(ServiceEventLog.class);

    private final String serviceName;
    private final UUID instanceUuid;
    private final String serviceBase;

    @Inject
    public ServiceEventLog(HikariDataSource dataSource,
                           ServiceConfiguration configuration
                    ) {
        this.dataSource = dataSource;

        this.serviceName = configuration.serviceName() + ":" + configuration.node();
        this.instanceUuid = configuration.instanceUuid();
        this.serviceBase = configuration.serviceName();

        logger.info("Starting service {} instance {}", serviceName, instanceUuid);

        logEvent("START", "Service starting");
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
