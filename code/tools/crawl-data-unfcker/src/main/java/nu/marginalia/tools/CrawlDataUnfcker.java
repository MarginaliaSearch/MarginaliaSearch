package nu.marginalia.tools;

import nu.marginalia.crawling.io.CrawlerOutputFile;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.crawling.io.CrawledDomainReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class CrawlDataUnfcker {
    public static void main(String... args) {
        if (args.length != 2) {
            System.out.println("Usage: crawl-data-unfcker input output");
            return;
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);

        if (!Files.isDirectory(input)) {
            System.err.println("Input directory is not valid");
            return;
        }
        if (!Files.isDirectory(output)) {
            System.err.println("Output directory is not valid");
            return;
        }

        try (var wl = new WorkLog(output.resolve("crawler.log"))) {
            for (var inputItem : WorkLog.iterable(input.resolve("crawler.log"))) {
                Path inputPath = input.resolve(inputItem.relPath());

                var domainMaybe = readDomain(inputPath).map(CrawledDomain::getDomain);
                if (domainMaybe.isEmpty())
                    continue;
                var domain = domainMaybe.get();

                // Generate conformant ID
                String newId = Integer.toHexString(domain.hashCode());

                var outputPath = CrawlerOutputFile.createLegacyOutputPath(output, newId, domain);
                var outputFileName = outputPath.toFile().getName();

                System.out.println(inputPath + " -> " + outputPath);
                Files.move(inputPath, outputPath);

                wl.setJobToFinished(domain, outputFileName, inputItem.cnt());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static Optional<CrawledDomain> readDomain(Path file) {
        if (!Files.exists(file)) {
            System.out.println("Missing file " + file);
            return Optional.empty();
        }

        try (var stream = CrawledDomainReader.createDataStream(file)) {
            while (stream.hasNext()) {
                if (stream.next() instanceof CrawledDomain domain) {
                    return Optional.of(domain);
                }
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return Optional.empty();
    }
}
