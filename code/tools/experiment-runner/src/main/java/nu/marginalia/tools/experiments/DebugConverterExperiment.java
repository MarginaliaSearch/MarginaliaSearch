package nu.marginalia.tools.experiments;

import com.google.inject.Inject;
import nu.marginalia.converting.processor.DomainProcessor;
import nu.marginalia.converting.processor.plugin.specialization.BlogSpecialization;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.tools.Experiment;
import org.jsoup.Jsoup;

public class DebugConverterExperiment extends Experiment {


    private final DomainProcessor domainProcessor;

    @Inject
    public DebugConverterExperiment(DomainProcessor domainProcessor) {
        this.domainProcessor = domainProcessor;

    }

    @Override
    public boolean process(CrawledDomain domain) {

        if (domain.doc == null) return true;

        for (var doc : domain.doc) {
            if (doc.documentBody == null) continue;

            var parsed = Jsoup.parse(doc.documentBody);

            var tagExtractor = new BlogSpecialization.BlogTagExtractor();
            parsed.traverse(tagExtractor);
            var tags = tagExtractor.getTags();
            if (!tags.isEmpty()) {
                System.out.println(tags);
            }

        }

        return true;
    }

    @Override
    public void onFinish() {
    }
}
