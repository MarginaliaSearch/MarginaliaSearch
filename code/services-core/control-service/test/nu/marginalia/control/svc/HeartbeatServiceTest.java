package nu.marginalia.control.svc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.control.sys.model.TaskHeartbeat;
import nu.marginalia.control.sys.svc.HeartbeatService;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Testcontainers
@Execution(SAME_THREAD)
@Tag("slow")
class HeartbeatServiceTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;
    @BeforeAll
    public static void setup() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);

        TestMigrationLoader.flywayMigration(dataSource);
    }

    @AfterAll
    public static void tearDown() {
        dataSource.close();
        mariaDBContainer.close();
    }

    @Test
    void removeTaskHeartbeat() throws SQLException {
        var service = new HeartbeatService(dataSource);

        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                INSERT INTO TASK_HEARTBEAT(TASK_NAME, TASK_BASE, NODE, INSTANCE, SERVICE_INSTANCE, HEARTBEAT_TIME, STATUS)
                VALUES ("test1", "test", 0, "instance1", "instance", NOW(), "RUNNING"),
                       ("test2", "test", 0, "instance2", "instance", NOW(), "RUNNING") 
                """);

            service.removeTaskHeartbeat(new TaskHeartbeat(
                    "test1", "test", 0, "instance1", "instance", 1, null, "test", "ok")
            );
            assertEquals(1, service.getTaskHeartbeats().size());

        }

    }
}