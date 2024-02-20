package nu.marginalia.loading.domains;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.io.processed.DomainLinkRecordParquetFileWriter;
import nu.marginalia.io.processed.DomainRecordParquetFileWriter;
import nu.marginalia.io.processed.ProcessedDataFileNames;
import nu.marginalia.loading.LoaderInputData;
import nu.marginalia.model.processed.DomainLinkRecord;
import nu.marginalia.model.processed.DomainRecord;
import nu.marginalia.process.control.ProcessAdHocTaskHeartbeat;
import nu.marginalia.process.control.ProcessHeartbeat;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
@Testcontainers
class DomainLoaderServiceTest {
    List<Path> toDelete = new ArrayList<>();
    ProcessHeartbeat heartbeat;

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
    void readDomainNames() throws IOException {
        Path workDir = Files.createTempDirectory(getClass().getSimpleName());
        Path parquetFile1 = ProcessedDataFileNames.domainFileName(workDir, 0);
        Path parquetFile2 = ProcessedDataFileNames.domainFileName(workDir, 1);
        Path parquetFile3 = ProcessedDataFileNames.domainLinkFileName(workDir, 0);

        toDelete.add(workDir);
        toDelete.add(parquetFile1);
        toDelete.add(parquetFile2);
        toDelete.add(parquetFile3);

        // Prep by creating two parquet files with domains
        // and one with domain links

        List<String> domains1 = List.of("www.marginalia.nu", "memex.marginalia.nu", "search.marginalia.nu");
        List<String> domains2 = List.of("wiby.me", "www.mojeek.com", "www.altavista.com");
        List<String> linkDomains = List.of("maya.land", "xkcd.com", "aaronsw.com");

        try (var pw = new DomainRecordParquetFileWriter(parquetFile1)) {
            for (var domain : domains1) {
                pw.write(dr(domain));
            }
        }
        try (var pw = new DomainRecordParquetFileWriter(parquetFile2)) {
            for (var domain : domains2) {
                pw.write(dr(domain));
            }
        }
        try (var pw = new DomainLinkRecordParquetFileWriter(parquetFile3)) {
            for (var domain : linkDomains) {
                pw.write(dl(domain));
            }
        }
        // Read them
        var domainService = new DomainLoaderService(null, new ProcessConfiguration("test", 1, UUID.randomUUID()));

        // Verify
        Set<String> expectedDomains1 = Sets.union(new HashSet<>(domains1), new HashSet<>(domains2));
        assertEquals(expectedDomains1, domainService.readBasicDomainInformation(new LoaderInputData(workDir, 2)).stream().map(d -> d.domain).collect(Collectors.toSet()));

        Set<String> expectedDomains2 = new HashSet<>(linkDomains);
        assertEquals(expectedDomains2, domainService.readReferencedDomainNames(new LoaderInputData(workDir, 2)));
    }

    private DomainRecord dr(String domainName) {
        return new DomainRecord(domainName, 0, 0, 0, null, null, null, null);
    }

    private DomainLinkRecord dl(String destDomainName) {
        return new DomainLinkRecord("www.marginalia.nu", destDomainName);
    }
}