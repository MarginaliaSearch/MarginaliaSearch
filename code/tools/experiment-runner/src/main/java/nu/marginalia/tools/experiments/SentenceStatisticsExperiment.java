package nu.marginalia.tools.experiments;

import com.google.inject.Inject;
import nu.marginalia.WmsaHome;
import nu.marginalia.adblock.GoogleAnwersSpamDetector;
import nu.marginalia.converting.processor.logic.dom.DomPruningFilter;
import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.tools.Experiment;
import nu.marginalia.topic.RecipeDetector;
import nu.marginalia.topic.TextileCraftDetector;
import nu.marginalia.topic.WoodworkingDetector;
import org.jsoup.Jsoup;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class SentenceStatisticsExperiment implements Experiment {

    SentenceExtractor se = new SentenceExtractor(WmsaHome.getLanguageModels());
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
    @Override
    public boolean process(CrawledDomain domain) {
        if (domain.doc == null) return true;

        logLine("Processing: " + domain.domain);

        for (var doc : domain.doc) {
            if (doc.documentBody == null) continue;

            var parsed = Jsoup.parse(doc.documentBody.decode());

            parsed.body().filter(new DomPruningFilter(0.5));

            var dld = se.extractSentences(parsed);


            int numSentences = dld.sentences.length;
            if (numSentences == 0) {
                continue;
            }

            double avgLength = dld.totalNumWords() / (double) numSentences;
            if (avgLength < 50) {
                writer.printf("%s\t%d\t%f\n", doc.url, dld.totalNumWords(), avgLength);
            }
        }

        return true;
    }

    @Override
    public void onFinish() {
        logLine("Done!\n");
        writer.close();
    }
}
