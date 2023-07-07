package nu.marginalia.crawl;

import nu.marginalia.crawling.model.spec.CrawlingSpecification;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.db.DomainBlacklistImpl;
import nu.marginalia.service.ServiceHomeNotConfiguredException;
import nu.marginalia.service.module.DatabaseModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class CrawlJobExtractorMain {

    public static void main(String... args) throws IOException {
        if (args.length == 0) {
            System.out.println("Parameters: outputfile.spec [-f domains.txt] | [domain1, domain2, ...]");
            System.out.println();
            System.out.println("If no domains are provided, a full crawl spec is created from the database");
            return;
        }

        Path outFile = Path.of(args[0]);
        if (Files.exists(outFile)) {
            System.err.println("Out file " + outFile + " already exists, remove it first!");
            return;
        }

        String[] targetDomains = getTargetDomains(Arrays.copyOfRange(args, 1, args.length));

        try (CrawlJobSpecWriter out = new CrawlJobSpecWriter(outFile))
        {
            streamSpecs(targetDomains).forEach(out::accept);
        }

        System.out.println("All done! Wrote " + outFile);
    }

    private static String[] getTargetDomains(String[] strings) throws IOException {
        if (strings.length == 0)
            return strings;

        if (strings.length == 2 && "-f".equals(strings[0])) {
            Path file = Path.of(strings[1]);

            System.out.println("Reading domains from " + file);

            try (var lines = Files.lines(file)) {
                return lines
                        .filter(s -> !s.isBlank())
                        .filter(s -> !s.startsWith("#"))
                        .map(String::trim)
                        .map(String::toLowerCase)
                        .toArray(String[]::new);
            }
        }

        return strings;
    }

    private static Stream<CrawlingSpecification> streamSpecs(String[] targetDomains) {
        if (targetDomains.length > 0) {

            try {
                var dataSource = new DatabaseModule().provideConnection();
                var domainExtractor = new CrawlJobDomainExtractor(new DomainBlacklistImpl(dataSource), dataSource);
                return Arrays.stream(targetDomains).map(EdgeDomain::new).map(domainExtractor::extractKnownDomain);
            }
            catch (ServiceHomeNotConfiguredException ex) {
                System.err.println("""
                    Could not connect to database, running crawl job creation in bootstrap mode.
                    This means that the crawl job will be created without any knowledge of the domains in the database.
                    
                    If this is not desirable, ensure that WMSA_HOME is configured and that the database is running.
                    """);

                var domainExtractor = new CrawlJobDomainExtractor(domain -> false, null);
                return Arrays.stream(targetDomains).map(EdgeDomain::new).map(domainExtractor::extractNewDomain);
            }

        } else {
            var ds = new DatabaseModule().provideConnection();
            var domainExtractor = new CrawlJobDomainExtractor(new DomainBlacklistImpl(ds), ds);
            return domainExtractor.extractDomainsFromQueue();
        }
    }

}
