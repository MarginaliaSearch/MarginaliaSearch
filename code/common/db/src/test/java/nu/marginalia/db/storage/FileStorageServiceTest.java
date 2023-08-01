package nu.marginalia.db.storage;

import com.google.common.collect.Lists;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.db.storage.model.FileStorageBaseType;
import nu.marginalia.db.storage.model.FileStorageType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Testcontainers
@Execution(SAME_THREAD)
@Tag("slow")
public class FileStorageServiceTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withInitScript("db/migration/V23_07_0_004__file_storage.sql")
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
    }


    @BeforeEach
    public void setupEach() {
        fileStorageService = new FileStorageService(dataSource);
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
    public void testCreateBase() throws SQLException, FileNotFoundException {
        String name = "test-" + UUID.randomUUID();

        var storage = new FileStorageService(dataSource);
        var base = storage.createStorageBase(name, createTempDir(), FileStorageBaseType.SLOW, false, false);

        Assertions.assertEquals(name, base.name());
        Assertions.assertEquals(FileStorageBaseType.SLOW, base.type());
        Assertions.assertFalse(base.mustClean());
        Assertions.assertFalse(base.permitTemp());
    }
    @Test
    public void testAllocateTempInNonPermitted() throws SQLException, FileNotFoundException {
        String name = "test-" + UUID.randomUUID();

        var storage = new FileStorageService(dataSource);

        var base = storage.createStorageBase(name, createTempDir(), FileStorageBaseType.SLOW, false, false);

        try {
            storage.allocateTemporaryStorage(base, FileStorageType.CRAWL_DATA, "xyz", "thisShouldFail");
            fail();
        }
        catch (IllegalArgumentException ex) {} // ok
        catch (Exception ex) {
            ex.printStackTrace();
            fail();
        }
    }

    @Test
    public void testAllocatePermanentInNonPermitted() throws SQLException, IOException {
        String name = "test-" + UUID.randomUUID();

        var storage = new FileStorageService(dataSource);

        var base = storage.createStorageBase(name, createTempDir(), FileStorageBaseType.SLOW, false, false);

        var created = storage.allocatePermanentStorage(base, "xyz", FileStorageType.CRAWL_DATA, "thisShouldSucceed");
        tempDirs.add(created.asPath());

        var actual = storage.getStorage(created.id());
        Assertions.assertEquals(created, actual);
    }

    @Test
    public void testAllocateTempInPermitted() throws IOException, SQLException {
        String name = "test-" + UUID.randomUUID();

        var storage = new FileStorageService(dataSource);

        var base = storage.createStorageBase(name, createTempDir(), FileStorageBaseType.SLOW, false, true);
        var fileStorage = storage.allocateTemporaryStorage(base, FileStorageType.CRAWL_DATA, "xyz", "thisShouldSucceed");

        Assertions.assertTrue(Files.exists(fileStorage.asPath()));
        tempDirs.add(fileStorage.asPath());
    }


}