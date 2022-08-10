package nu.marginalia.wmsa.edge.tools;

import nu.marginalia.wmsa.edge.converting.processor.logic.topic.AdblockSimulator;
import nu.marginalia.wmsa.edge.crawling.CrawlPlanLoader;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDocument;
import nu.marginalia.wmsa.edge.crawling.model.CrawledDomain;
import nu.marginalia.wmsa.edge.model.EdgeCrawlPlan;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.nio.file.Path;

import static nu.marginalia.wmsa.edge.converting.processor.DocumentProcessor.isAcceptedContentType;

public class AdblockTesterTool {

    static AdblockSimulator simulator;

    static {
        try {
            simulator = new AdblockSimulator(Path.of("/home/vlofgren/easylist.txt"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String... args) throws IOException {
        EdgeCrawlPlan plan = new CrawlPlanLoader().load(Path.of(args[0]));


        try (var iterable = plan.domainsIterable()) {
            for (var domain : iterable) {
                processDomain(domain);
            }
        }

    }

    private static void processDomain(CrawledDomain domain) {
        if (domain.doc == null) return;
        for (var doc : domain.doc) {
            if (isAcceptedContentType(doc) && "OK".equals(doc.crawlerStatus)) {
                processDocument(doc);
            }
        }
    }


    private static void processDocument(CrawledDocument doc) {
        Document parsedDocument = Jsoup.parse(doc.documentBody);

        if (simulator.hasAds(parsedDocument)) {
            System.out.println(doc.url);
        }
    }
}
