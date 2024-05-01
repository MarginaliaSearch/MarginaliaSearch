package nu.marginalia.storage;

import com.google.common.collect.Lists;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.storage.model.FileStorage;
import nu.marginalia.storage.model.FileStorageBase;
import nu.marginalia.storage.model.FileStorageBaseType;
import nu.marginalia.storage.model.FileStorageType;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Tag("slow")
public class FileStorageServiceTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;
    static FileStorageService fileStorageService;

    static List<Path> tempDirs = new ArrayList<>();

    @BeforeAll
    public static void setup() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);

        TestMigrationLoader.flywayMigration(dataSource);
    }


    @BeforeEach
    public void setupEach() {
        fileStorageService = new FileStorageService(dataSource, 0);
    }

    @AfterEach
    public void tearDownEach() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM FILE_STORAGE");
            stmt.execute("DELETE FROM FILE_STORAGE_BASE");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterAll
    public static void teardown() {
        dataSource.close();

        Lists.reverse(tempDirs).forEach(path -> {
            try {
                System.out.println("Deleting " + path);
                Files.delete(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private Path createTempDir() {
        try {
            Path dir = Files.createTempDirectory("file-storage-test");
            tempDirs.add(dir);
            return dir;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Test
    public void testPathOverride() {
        try {
            System.setProperty("storage.root", "/tmp");

            var path = new FileStorageBase(null, null, 0, null, "test").asPath();
            Assertions.assertEquals(Path.of("/tmp/test"), path);
        }
        finally {
            System.clearProperty("storage.root");
        }
    }
    @Test
    public void testPathOverride3() {
        try {
            System.setProperty("storage.root", "/tmp");

            var path = new FileStorageBase(null, null, 0, null, "/test").asPath();
            Assertions.assertEquals(Path.of("/tmp/test"), path);
        }
        finally {
            System.clearProperty("storage.root");
        }
    }
    @Test
    public void testPathOverride2() {
        try {
            System.setProperty("storage.root", "/tmp");

            var path = new FileStorage(null, null, null, null, "test", null, null).asPath();

            Assertions.assertEquals(Path.of("/tmp/test"), path);
        }
        finally {
            System.clearProperty("storage.root");
        }
    }

    @Test
    public void testCreateBase() throws SQLException {
        String name = "test-" + UUID.randomUUID();

        var storage = new FileStorageService(dataSource, 0);
        var base = storage.createStorageBase(name, createTempDir(), FileStorageBaseType.WORK);

        Assertions.assertEquals(name, base.name());
        Assertions.assertEquals(FileStorageBaseType.WORK, base.type());
    }

    @Test
    public void testAllocateTemp() throws IOException, SQLException {
        String name = "test-" + UUID.randomUUID();

        // ensure a base exists
        var base = fileStorageService.createStorageBase(name, createTempDir(), FileStorageBaseType.STORAGE);
        tempDirs.add(base.asPath());

        var storage = new FileStorageService(dataSource, 0);

        var fileStorage = storage.allocateStorage(FileStorageType.CRAWL_DATA, "xyz", "thisShouldSucceed");
        System.out.println("Allocated " + fileStorage.asPath());
        Assertions.assertTrue(Files.exists(fileStorage.asPath()));
        tempDirs.add(fileStorage.asPath());
    }


}