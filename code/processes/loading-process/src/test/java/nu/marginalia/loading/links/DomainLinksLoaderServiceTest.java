package nu.marginalia.loading.links;

import com.google.common.collect.Lists;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.io.processed.DomainLinkRecordParquetFileWriter;
import nu.marginalia.io.processed.DomainRecordParquetFileWriter;
import nu.marginalia.io.processed.ProcessedDataFileNames;
import nu.marginalia.loader.DbTestUtil;
import nu.marginalia.loading.LoaderInputData;
import nu.marginalia.loading.domains.DomainLoaderService;
import nu.marginalia.model.processed.DomainLinkRecord;
import nu.marginalia.model.processed.DomainRecord;
import nu.marginalia.process.control.ProcessAdHocTaskHeartbeat;
import nu.marginalia.process.control.ProcessHeartbeat;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("slow")
@Testcontainers
@Disabled // Error in the SQL loading mechanism, we don't deal with DELIMITER correctly
          // which means we can't get around flyway's bugs necessitating DELIMITER.
class DomainLinksLoaderServiceTest {
    List<Path> toDelete = new ArrayList<>();
    ProcessHeartbeat heartbeat;

    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withInitScript("db/migration/V23_06_0_000__base.sql")
            .withNetworkAliases("mariadb");

    HikariDataSource dataSource;

    @BeforeEach
    public void setUp() {

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);

        List<String> migrations = List.of(
                "db/migration/V23_11_0_007__domain_node_affinity.sql",
                "db/migration/V23_11_0_008__purge_procedure.sql"
                );
        for (String migration : migrations) {
            try (var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream(migration),
                    "Could not load migration script " + migration);
                 var conn = dataSource.getConnection();
                 var stmt = conn.createStatement()
            ) {
                String script = new String(resource.readAllBytes());
                String[] cmds = script.split("\\s*;\\s*");
                for (String cmd : cmds) {
                    if (cmd.isBlank())
                        continue;
                    System.out.println(cmd);
                    stmt.executeUpdate(cmd);
                }
            } catch (IOException | SQLException ex) {

            }
        }

        heartbeat = Mockito.mock(ProcessHeartbeat.class);

        Mockito.when(heartbeat.createAdHocTaskHeartbeat(Mockito.anyString())).thenReturn(
                Mockito.mock(ProcessAdHocTaskHeartbeat.class)
        );
    }

    @AfterEach
    public void tearDown() throws IOException {
        for (var path : Lists.reverse(toDelete)) {
            Files.deleteIfExists(path);
        }

        toDelete.clear();
        dataSource.close();
    }

    @Test
    public void test() throws IOException, SQLException {
        Path workDir = Files.createTempDirectory(getClass().getSimpleName());
        Path parquetFile1 = ProcessedDataFileNames.domainFileName(workDir, 0);
        Path parquetFile2 = ProcessedDataFileNames.domainLinkFileName(workDir, 0);
        Path parquetFile3 = ProcessedDataFileNames.domainLinkFileName(workDir, 1);

        toDelete.add(workDir);
        toDelete.add(parquetFile1);
        toDelete.add(parquetFile2);
        toDelete.add(parquetFile3);

        List<String> domains1 = List.of("www.marginalia.nu", "search.marginalia.nu");
        List<String> linkDomains1 = List.of("wiby.me", "www.mojeek.com", "www.altavista.com");
        List<String> linkDomains2 = List.of("maya.land", "xkcd.com", "aaronsw.com");

        try (var pw = new DomainRecordParquetFileWriter(parquetFile1)) {
            for (var domain : domains1) {
                pw.write(dr(domain));
            }
        }
        try (var pw = new DomainLinkRecordParquetFileWriter(parquetFile2)) {
            for (var domain : linkDomains1) {
                pw.write(dl("www.marginalia.nu", domain));
            }
        }
        try (var pw = new DomainLinkRecordParquetFileWriter(parquetFile3)) {
            for (var domain : linkDomains2) {
                pw.write(dl("search.marginalia.nu", domain));
            }
        }

        try (var dataSource = DbTestUtil.getConnection(mariaDBContainer.getJdbcUrl());
             var conn = dataSource.getConnection();
             var query = conn.prepareStatement("""
                     SELECT SOURCE_DOMAIN_ID, DEST_DOMAIN_ID FROM EC_DOMAIN_LINK
                     """)
        ) {
            var domainService = new DomainLoaderService(dataSource, new ProcessConfiguration("test", 1, UUID.randomUUID()));
            var input = new LoaderInputData(workDir, 2);
            var domainRegistry = domainService.getOrCreateDomainIds(input);

            var dls = new DomainLinksLoaderService(dataSource, new ProcessConfiguration("test", 1, UUID.randomUUID()));
            dls.loadLinks(domainRegistry, heartbeat, input);

            Map<Integer, Set<Integer>> expected = new HashMap<>();
            Map<Integer, Set<Integer>> actual = new HashMap<>();
            expected.put(domainRegistry.getDomainId("www.marginalia.nu"), new HashSet<>());
            expected.put(domainRegistry.getDomainId("search.marginalia.nu"), new HashSet<>());

            for (var domain : linkDomains1) {
                expected.get(domainRegistry.getDomainId("www.marginalia.nu")).add(domainRegistry.getDomainId(domain));
            }
            for (var domain : linkDomains2) {
                expected.get(domainRegistry.getDomainId("search.marginalia.nu")).add(domainRegistry.getDomainId(domain));
            }

            var rs = query.executeQuery();
            while (rs.next()) {
                actual.computeIfAbsent(rs.getInt(1), k -> new HashSet<>())
                                .add(rs.getInt(2));
            }

            assertEquals(expected, actual);

        }


    }

    private DomainRecord dr(String domainName) {
        return new DomainRecord(domainName, 0, 0, 0, null, null, null, null);
    }

    private DomainLinkRecord dl(String sourceDomainName, String destDomainName) {
        return new DomainLinkRecord(sourceDomainName, destDomainName);
    }
}