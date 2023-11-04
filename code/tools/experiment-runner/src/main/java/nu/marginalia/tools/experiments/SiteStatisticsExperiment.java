package nu.marginalia.tools.experiments;

import com.google.inject.Inject;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.processor.DomainProcessor;
import nu.marginalia.crawling.io.SerializableCrawlDataStream;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.tools.Experiment;

import java.util.Comparator;

public class SiteStatisticsExperiment extends Experiment {


    private final DomainProcessor domainProcessor;

    @Inject
    public SiteStatisticsExperiment(DomainProcessor domainProcessor) {
        this.domainProcessor = domainProcessor;

    }

    @Override
    public boolean process(SerializableCrawlDataStream stream) {
        var ret = domainProcessor.process(stream);

        ret.documents.stream()
                .filter(ProcessedDocument::isProcessedFully)
                .sorted(Comparator.comparing(doc -> doc.details.metadata.topology()))
                .flatMap(doc -> doc.details.feedLinks.stream())
                .map(EdgeUrl::toString)
                .min(Comparator.comparing(String::length))
                .ifPresent(url -> {
                    System.out.printf("\"%s\",\"%s\"\n", ret.domain, url);
                });

        return true;
    }

    @Override
    public void onFinish() {
    }
}
