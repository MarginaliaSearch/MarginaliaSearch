package nu.marginalia.crawl;

import nu.marginalia.crawling.model.CrawlingSpecification;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.dbcommon.DomainBlacklistImpl;
import nu.marginalia.service.module.DatabaseModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class CrawlJobExtractorMain {

    public static void main(String... args) throws IOException {
        if (args.length == 0) {
            System.out.println("Parameters: outputfile.spec [domain1, domain2, ...]");
            System.out.println();
            System.out.println("If no domains are provided, a full crawl spec is created from the database");
            return;
        }

        Path outFile = Path.of(args[0]);
        if (Files.exists(outFile)) {
            System.err.println("Out file " + outFile + " already exists, remove it first!");
            return;
        }

        String[] targetDomains = Arrays.copyOfRange(args, 1, args.length);

        try (CrawlJobSpecWriter out = new CrawlJobSpecWriter(outFile))
        {
            streamSpecs(targetDomains).forEach(out::accept);
        }
    }

    private static Stream<CrawlingSpecification> streamSpecs(String[] targetDomains) {
        var ds = new DatabaseModule().provideConnection();
        var domainExtractor = new CrawlJobDomainExtractor(new DomainBlacklistImpl(ds), ds);

        if (targetDomains.length > 0) {
            return Arrays.stream(targetDomains).map(EdgeDomain::new).map(domainExtractor::extractDomain);
        } else {
            return domainExtractor.extractDomainsFromQueue();
        }
    }

}
