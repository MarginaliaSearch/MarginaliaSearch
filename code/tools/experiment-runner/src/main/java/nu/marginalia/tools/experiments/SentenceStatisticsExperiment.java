package nu.marginalia.tools.experiments;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.WmsaHome;
import nu.marginalia.converting.processor.logic.dom.DomPruningFilter;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.keyword.DocumentKeywordExtractor;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import nu.marginalia.tools.Experiment;
import nu.marginalia.tools.LegacyExperiment;
import org.jsoup.Jsoup;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class SentenceStatisticsExperiment extends LegacyExperiment {

    SentenceExtractor se = new SentenceExtractor(WmsaHome.getLanguageModels());
    DocumentKeywordExtractor documentKeywordExtractor = new DocumentKeywordExtractor(new TermFrequencyDict(WmsaHome.getLanguageModels()));
    Path filename;
    PrintWriter writer;

    @Inject
    public SentenceStatisticsExperiment() throws IOException {
        filename = Files.createTempFile(getClass().getSimpleName(), ".csv");
        System.out.println("Writing to " + filename);

        writer = new PrintWriter(new BufferedOutputStream(new FileOutputStream(filename.toFile())));
    }

    private void logLine(String message) {
        System.out.printf("\u001b[2K\r%s", message);
    }
    @SneakyThrows
    @Override
    public boolean process(CrawledDomain domain) {
        if (domain.doc == null) return true;

        logLine("Processing: " + domain.domain);

        for (var doc : domain.doc) {
            if (doc.documentBody == null) continue;

            var parsed = Jsoup.parse(doc.documentBody);

            parsed.body().filter(new DomPruningFilter(0.5));

            var dld = se.extractSentences(parsed);
            var keywords = documentKeywordExtractor.extractKeywords(dld, new EdgeUrl(doc.url));

            keywords.build();
        }

        return true;
    }

    @Override
    public void onFinish() {
        logLine("Done!\n");
        writer.close();
    }
}
