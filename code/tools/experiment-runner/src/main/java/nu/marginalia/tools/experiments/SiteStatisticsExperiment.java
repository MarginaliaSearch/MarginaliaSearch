package nu.marginalia.tools.experiments;

import com.google.inject.Inject;
import nu.marginalia.WmsaHome;
import nu.marginalia.adblock.GoogleAnwersSpamDetector;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.processor.DocumentProcessor;
import nu.marginalia.converting.processor.DomainProcessor;
import nu.marginalia.converting.processor.logic.dom.DomPruningFilter;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.tools.Experiment;
import nu.marginalia.topic.RecipeDetector;
import nu.marginalia.topic.TextileCraftDetector;
import nu.marginalia.topic.WoodworkingDetector;
import org.jsoup.Jsoup;

import java.util.Comparator;

public class SiteStatisticsExperiment implements Experiment {


    private final DomainProcessor domainProcessor;

    @Inject
    public SiteStatisticsExperiment(DomainProcessor domainProcessor) {
        this.domainProcessor = domainProcessor;

    }

    @Override
    public boolean process(CrawledDomain domain) {
        var ret = domainProcessor.process(domain);

        ret.documents.stream()
                .filter(ProcessedDocument::isProcessedFully)
                .sorted(Comparator.comparing(doc -> doc.details.metadata.topology()))
                .forEach(doc -> System.out.println(doc.url + ":" + doc.details.metadata));

        return true;
    }

    @Override
    public void onFinish() {
    }
}
