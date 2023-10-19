package nu.marginalia.crawlspec;

import nu.marginalia.db.DbDomainStatsExportMultitool;
import nu.marginalia.io.crawlspec.CrawlSpecRecordParquetFileWriter;
import nu.marginalia.model.crawlspec.CrawlSpecRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CrawlSpecGenerator {
    private static final int MIN_VISIT_COUNT = 200;
    private static final int MAX_VISIT_COUNT = 100000;

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
                                .crawlDepth(calculateCrawlDepthFromVisitedCount(
                                        counts.getKnownUrlCount(domain)
                                ))
                                .urls(listSource.getKnownUrls(domain))
                                .domain(domain)
                        .build());
            }
        }
    }

    private static int calculateCrawlDepthFromVisitedCount(int count) {
        if (count < MIN_VISIT_COUNT / 2) {
            /* If we aren't getting very many good documents
              out of this webpage on previous attempts, we
              won't dig very deeply this time.  This saves time
              and resources for both the crawler and the server,
              and also prevents deep crawls on foreign websites we aren't
              interested in crawling at this point. */
            count = MIN_VISIT_COUNT;
        }
        else {
            /* If we got many results previously, we'll
               dig deeper with each successive crawl. */
            count = count + 1000 + count / 4;
        }

        if (count > MAX_VISIT_COUNT) {
            count = MAX_VISIT_COUNT;
        }

        return count;
    }

    public interface DomainSource {
        List<String> getDomainNames() throws IOException, SQLException;

        static DomainSource combined(DomainSource... sources) {
            if (sources.length == 0) {
                return List::of;
            }

            return () -> {
                List<String> combined = new ArrayList<>(sources[0].getDomainNames());

                for (int i = 1; i < sources.length; i++) {
                    combined.addAll(sources[i].getDomainNames());
                }

                return combined;
            };
        }

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

        static DomainSource knownUrlsFromDb(DbDomainStatsExportMultitool dbData) {
            return dbData::getAllIndexedDomains;
        }

        static DomainSource fromCrawlQueue(DbDomainStatsExportMultitool dbData) {
            return dbData::getCrawlQueueDomains;
        }
    }

    public interface KnownUrlsCountSource {
        int getKnownUrlCount(String domainName) throws SQLException;

        static KnownUrlsCountSource fixed(int value) {
            return domainName -> value;
        }

        static KnownUrlsCountSource fromDb(DbDomainStatsExportMultitool dbData, int defaultValue) {
            return domainName ->
                    dbData.getVisitedUrls(domainName)
                            .orElse(defaultValue);
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
