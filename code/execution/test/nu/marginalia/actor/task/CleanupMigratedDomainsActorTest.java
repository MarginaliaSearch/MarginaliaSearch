package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.service.control.ServiceAdHocTaskHeartbeat;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.control.ServiceHeartbeat;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageState;
import nu.marginalia.storage.model.FileStorageType;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Tag("slow")
class CleanupMigratedDomainsActorTest {

    private static final int THIS_NODE = 2;

    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;

    private Path storageDir;
    private CleanupMigratedDomainsActor actor;

    @BeforeAll
    static void setupDatabase() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);
        TestMigrationLoader.flywayMigration(dataSource);
    }

    @AfterAll
    static void shutDownDatabase() {
        dataSource.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        // keep.com is assigned to this node, foreign.com to another, and gone.com is not present
        // in EC_DOMAIN at all.
        try (var conn = dataSource.getConnection();
             var clear = conn.prepareStatement("DELETE FROM EC_DOMAIN");
             var insert = conn.prepareStatement(
                     "INSERT INTO EC_DOMAIN (DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY) VALUES (?, ?, ?)"))
        {
            clear.executeUpdate();
            insertDomain(insert, "keep.com", THIS_NODE);
            insertDomain(insert, "foreign.com", 5);
            insert.executeBatch();
        }

        storageDir = Files.createTempDirectory("cleanup-test");
        System.setProperty("storage.root", storageDir.toString());

        FileStorage storage = new FileStorage(new FileStorageId(1), null, FileStorageType.CRAWL_DATA,
                LocalDateTime.now(), "", FileStorageState.ACTIVE, "test");

        FileStorageService storageService = mock(FileStorageService.class);
        when(storageService.getOnlyActiveFileStorage(FileStorageType.CRAWL_DATA))
                .thenReturn(Optional.of(new FileStorageId(1)));
        when(storageService.getStorage(new FileStorageId(1))).thenReturn(storage);

        ServiceAdHocTaskHeartbeat task = mock(ServiceAdHocTaskHeartbeat.class);
        when(task.wrap(anyString(), anyCollection())).thenAnswer(inv -> inv.getArgument(1));
        ServiceHeartbeat heartbeat = mock(ServiceHeartbeat.class);
        when(heartbeat.createServiceAdHocTaskHeartbeat(anyString())).thenReturn(task);

        actor = new CleanupMigratedDomainsActor(new Gson(), storageService, dataSource,
                new ServiceConfiguration(null, THIS_NODE, null, null, -1, null),
                mock(ServiceEventLog.class), heartbeat);
    }

    @AfterEach
    void tearDown() throws IOException {
        System.clearProperty("storage.root");
        try (var paths = Files.walk(storageDir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }

    @Test
    void testDeleteForeign() throws Exception {
        Path keep = writeSlop("0001-keep.com.slop.zip");
        Path foreign = writeSlop("0002-foreign.com.slop.zip");
        Path gone = writeSlop("0003-gone.com.slop.zip");
        Path orphan = writeSlop("9999-orphan.com.slop.zip");

        try (WorkLog log = new WorkLog(storageDir.resolve("crawler.log"))) {
            log.setJobToFinished("keep.com", keep.toString(), 1);
            log.setJobToFinished("foreign.com", foreign.toString(), 1);
            log.setJobToFinished("gone.com", gone.toString(), 1);
        }

        ActorStep next = actor.transition(new CleanupMigratedDomainsActor.Initial());
        assertInstanceOf(CleanupMigratedDomainsActor.Cleanup.class, next);
        ActorStep end = actor.transition(next);
        assertEquals("End", end.getClass().getSimpleName());

        assertTrue(Files.exists(keep), "file for a domain assigned here should be retained");
        assertFalse(Files.exists(foreign), "file for a domain assigned elsewhere should be deleted");
        assertFalse(Files.exists(gone), "file for a domain absent from the DB should be deleted");
        assertFalse(Files.exists(orphan), "file not referenced by the log should be deleted");

        List<String> remainingDomains = new ArrayList<>();
        for (var entry : WorkLog.iterable(storageDir.resolve("crawler.log"))) {
            remainingDomains.add(entry.id());
        }
        assertEquals(List.of("keep.com"), remainingDomains);
    }

    private void insertDomain(java.sql.PreparedStatement insert, String domain, int nodeAffinity) throws SQLException {
        insert.setString(1, domain);
        insert.setString(2, domain);
        insert.setInt(3, nodeAffinity);
        insert.addBatch();
    }

    private Path writeSlop(String name) throws IOException {
        Path path = storageDir.resolve(name);
        Files.writeString(path, "data");
        return path;
    }
}
