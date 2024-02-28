package nu.marginalia.crawlspec;

import nu.marginalia.io.crawlspec.CrawlSpecRecordParquetFileWriter;
import nu.marginalia.model.crawlspec.CrawlSpecRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

public class CrawlSpecGenerator {

    public static void generateCrawlSpec(Path output,
                                         DomainSource domains,
                                         KnownUrlsCountSource counts,
                                         KnownUrlsListSource listSource)
            throws IOException, SQLException
    {
        try (var writer = new CrawlSpecRecordParquetFileWriter(output)) {
            for (String domain : domains.getDomainNames()) {

                domain = domain.toLowerCase();

                writer.write(CrawlSpecRecord
                        .builder()
                                .crawlDepth(counts.getKnownUrlCount(domain))
                                .urls(listSource.getKnownUrls(domain))
                                .domain(domain)
                        .build());
            }
        }
    }

    public interface DomainSource {
        List<String> getDomainNames() throws IOException, SQLException;

        static DomainSource fromFile(Path file) {
            return () -> {
                var lines = Files.readAllLines(file);
                lines.replaceAll(s ->
                        s.split("#", 2)[0]
                         .trim()
                         .toLowerCase()
                );
                lines.removeIf(String::isBlank);
                return lines;
            };
        }

    }

    public interface KnownUrlsCountSource {
        int getKnownUrlCount(String domainName) throws SQLException;

        static KnownUrlsCountSource fixed(int value) {
            return domainName -> value;
        }
    }

    public interface KnownUrlsListSource {
        List<String> getKnownUrls(String domainName) throws SQLException;

        static KnownUrlsListSource justIndex() {
            return domainName -> List.of(
                    "http://" + domainName + "/",
                    "https://" + domainName + "/"
            );
        }
    }
}
