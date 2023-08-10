package nu.marginalia.tools.experiments;

import com.google.inject.Inject;
import nu.marginalia.adblock.AdblockSimulator;
import nu.marginalia.converting.processor.DocumentProcessor;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.tools.Experiment;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class AdblockExperiment extends Experiment {

    private final AdblockSimulator simulator;

    @Inject
    public AdblockExperiment(AdblockSimulator simulator) {
        this.simulator = simulator;
    }

    @Override
    public boolean process(CrawledDomain domain) {
        if (domain.doc == null) return true;

        for (var doc : domain.doc) {
            if (DocumentProcessor.isAcceptedContentType(doc) && "OK".equals(doc.crawlerStatus)) {
                processDocument(doc);
            }
        }

        return true;
    }

    private void processDocument(CrawledDocument doc) {
        Document parsedDocument = Jsoup.parse(doc.documentBody);

        if (simulator.hasAds(parsedDocument)) {
            System.out.println(doc.url);
        }
    }

    @Override
    public void onFinish() {
    }
}
