package nu.marginalia.tools.experiments;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.WmsaHome;
import nu.marginalia.converting.processor.classifier.topic.AdHocDetector;
import nu.marginalia.converting.processor.logic.dom.DomPruningFilter;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.model.crawldata.CrawledDomain;
import nu.marginalia.tools.LegacyExperiment;
import org.jsoup.Jsoup;

import java.nio.file.Files;
import java.nio.file.Path;

public class TopicExperiment extends LegacyExperiment {

    AdHocDetector detector;

    SentenceExtractor se = new SentenceExtractor(WmsaHome.getLanguageModels());
    Path filename = null;

    @SneakyThrows
    public void args(String... args) {
        filename = Path.of(args[0]);
        detector = new AdHocDetector(Files.readAllLines(filename));
    }

    @Inject
    public TopicExperiment() {
    }

    @Override
    public boolean process(CrawledDomain domain) {
        if (domain.doc == null) return true;


        for (var doc : domain.doc) {
            if (doc.documentBody == null) continue;

            var parsed = Jsoup.parse(doc.documentBody);

            parsed.body().filter(new DomPruningFilter(0.5));
            var dld = se.extractSentences(parsed);

            if (dld.totalNumWords() < 50)
                continue;

            if (detector.testP(dld) > 0.5) {
                System.out.println("match\t" + doc.url);
            }

        }

        return true;
    }

    @Override
    public void onFinish() {
    }
}
