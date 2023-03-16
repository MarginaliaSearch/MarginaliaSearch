package nu.marginalia.experimental;

import nu.marginalia.adblock.AdblockSimulator;
import nu.marginalia.converting.processor.DocumentProcessor;
import plan.CrawlPlanLoader;
import plan.CrawlPlan;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.nio.file.Path;


public class AdblockTesterTool {

    static AdblockSimulator simulator;

    static {
        try {
            simulator = new AdblockSimulator();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String... args) throws IOException {
        CrawlPlan plan = new CrawlPlanLoader().load(Path.of(args[0]));

        try (var iterable = plan.domainsIterable()) {
            for (var domain : iterable) {
                processDomain(domain);
            }
        }

    }

    private static void processDomain(CrawledDomain domain) {
        if (domain.doc == null) return;
        for (var doc : domain.doc) {
            if (DocumentProcessor.isAcceptedContentType(doc) && "OK".equals(doc.crawlerStatus)) {
                processDocument(doc);
            }
        }
    }


    private static void processDocument(CrawledDocument doc) {
        Document parsedDocument = Jsoup.parse(doc.documentBody.decode());

        if (simulator.hasAds(parsedDocument)) {
            System.out.println(doc.url);
        }
    }
}
