package nu.marginalia.loading.links;

import com.google.common.collect.Lists;
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
import nu.marginalia.process.control.ProcessHeartbeatImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

    @BeforeEach
    public void setUp() {
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
            var domainService = new DomainLoaderService(dataSource);
            var input = new LoaderInputData(workDir, 2);
            var domainRegistry = domainService.getOrCreateDomainIds(input);

            var dls = new DomainLinksLoaderService(dataSource);
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