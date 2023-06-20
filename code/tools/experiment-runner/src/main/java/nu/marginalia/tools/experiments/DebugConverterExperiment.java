package nu.marginalia.tools.experiments;

import com.google.inject.Inject;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.processor.DomainProcessor;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.tools.Experiment;

public class DebugConverterExperiment extends Experiment {


    private final DomainProcessor domainProcessor;

    @Inject
    public DebugConverterExperiment(DomainProcessor domainProcessor) {
        this.domainProcessor = domainProcessor;

    }

    @Override
    public boolean process(CrawledDomain domain) {
        var ret = domainProcessor.process(domain);

        ret.documents.stream()
                .filter(ProcessedDocument::isProcessedFully)
                .peek(d -> System.out.println(d.url))
                .map(d -> d.details.metadata)
                .forEach(System.out::println);

        return true;
    }

    @Override
    public void onFinish() {
    }
}
