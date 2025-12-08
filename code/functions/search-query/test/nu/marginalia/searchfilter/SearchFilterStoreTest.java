package nu.marginalia.searchfilter;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.api.searchquery.model.SearchFilterDefaults;
import nu.marginalia.functions.searchquery.searchfilter.SearchFilterParser;
import nu.marginalia.functions.searchquery.searchfilter.SearchFilterStore;
import nu.marginalia.functions.searchquery.searchfilter.model.SearchFilterSpec;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
@Tag("slow")
class SearchFilterStoreTest {

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

    @Test
    public void testDefaultConfig() {
        SearchFilterStore store = new SearchFilterStore(dataSource, new SearchFilterParser());
        store.loadDefaultConfigs();
    }

    @Test
    public void testSaveLoad() throws SQLException {
        SearchFilterStore store = new SearchFilterStore(dataSource, new SearchFilterParser());
        store.saveFilter(SearchFilterDefaults.SYSTEM_USER_ID, "test", """
                <?xml version="1.0"?>
                <filter>
                    <search-set>BLOGS</search-set>
                </filter>
                """);
        Optional<SearchFilterSpec> filter = store.getFilter(SearchFilterDefaults.SYSTEM_USER_ID, "test");
        Assertions.assertTrue(filter.isPresent());
        var spec = filter.get();
        Assertions.assertEquals(SearchFilterDefaults.SYSTEM_USER_ID, spec.userId());
        Assertions.assertEquals("test", spec.identifier());
        Assertions.assertEquals("BLOGS", spec.searchSetIdentifier());
    }
}