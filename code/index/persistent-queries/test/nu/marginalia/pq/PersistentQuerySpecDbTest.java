package nu.marginalia.pq;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@Tag("slow")
@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class PersistentQuerySpecDbTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;

    @BeforeAll
    public static void setUp() throws IOException, ParserConfigurationException, SAXException {

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);
        TestMigrationLoader.flywayMigration(dataSource);

    }

    @AfterEach
    public void tearDown() throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement();
        ) {
            stmt.executeUpdate("TRUNCATE TABLE PERSISTENT_QUERIES");
        }
    }

    @Test
    public void testCreate() throws SQLException {
        var db = new PersistentQuerySpecsDb(dataSource);

        String customerId = "test";
        List<String> termsInclude = List.of("foo", "bar");
        List<String> termsExclude = List.of("baz");
        String languageIsoCode = "en";

        var ret = db.create(customerId, termsInclude, termsExclude, languageIsoCode);

        System.out.println(ret);

        Assertions.assertEquals(ret.customerId(), customerId);
        Assertions.assertEquals(ret.termsInclude(), termsInclude);
        Assertions.assertEquals(ret.termsExclude(), termsExclude);
        Assertions.assertEquals(ret.languageIsoCode(), languageIsoCode);
        Assertions.assertNotNull(ret.customerId());
        Assertions.assertNotNull(ret.tsAdded());
        Assertions.assertNull(ret.tsDisabled());

        Assertions.assertEquals(1, db.getEnabledQueries().size());
    }

    @Test
    public void testCreateAndDisable() throws SQLException {
        var db = new PersistentQuerySpecsDb(dataSource);

        String customerId = "test";
        List<String> termsInclude = List.of("foo", "bar");
        List<String> termsExclude = List.of("baz");
        String languageIsoCode = "en";

        var ret = db.create(customerId, termsInclude, termsExclude, languageIsoCode);

        db.disable(ret);

        ret = db.get(ret.customerId(), ret.publicId()).orElseThrow();

        System.out.println(ret);

        Assertions.assertEquals(ret.customerId(), customerId);
        Assertions.assertEquals(ret.termsInclude(), termsInclude);
        Assertions.assertEquals(ret.termsExclude(), termsExclude);
        Assertions.assertEquals(ret.languageIsoCode(), languageIsoCode);
        Assertions.assertNotNull(ret.customerId());
        Assertions.assertNotNull(ret.tsAdded());
        Assertions.assertNotNull(ret.tsDisabled());

        Assertions.assertEquals(0, db.getEnabledQueries().size());
    }


    @Test
    public void testCustomerQuery() throws SQLException {
        var db = new PersistentQuerySpecsDb(dataSource);

        String customerId1 = "test1";
        String customerId2 = "test2";
        List<String> termsInclude = List.of("foo", "bar");
        List<String> termsExclude = List.of("baz");
        String languageIsoCode = "en";

        db.create(customerId1, termsInclude, termsExclude, languageIsoCode);
        db.create(customerId1, termsInclude, termsExclude, languageIsoCode);
        db.create(customerId1, termsInclude, termsExclude, languageIsoCode);
        db.create(customerId2, termsInclude, termsExclude, languageIsoCode);
        db.create(customerId2, termsInclude, termsExclude, languageIsoCode);

        var c1 = db.getQueriesForCustomer(customerId1);
        var c2 = db.getQueriesForCustomer(customerId2);

        System.out.println(c1);
        System.out.println(c2);

        Assertions.assertEquals(3, c1.size());
        Assertions.assertEquals(2, c2.size());
    }

}
