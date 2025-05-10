package nu.marginalia.converting.processor.plugin;

import nu.marginalia.WmsaHome;
import nu.marginalia.converting.model.DisqualifiedException;
import nu.marginalia.converting.processor.DocumentClass;
import nu.marginalia.converting.processor.logic.DocumentLengthLogic;
import nu.marginalia.converting.processor.logic.TitleExtractor;
import nu.marginalia.converting.processor.plugin.specialization.DefaultSpecialization;
import nu.marginalia.converting.processor.summary.SummaryExtractor;
import nu.marginalia.converting.processor.summary.heuristic.*;
import nu.marginalia.keyword.DocumentKeywordExtractor;
import nu.marginalia.keyword.LinkTexts;
import nu.marginalia.language.filter.LanguageFilter;
import nu.marginalia.language.sentence.ThreadLocalSentenceExtractorProvider;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

class PdfDocumentProcessorPluginTest {
    static PdfDocumentProcessorPlugin plugin;

    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        var lm = WmsaHome.getLanguageModels();
        plugin = new PdfDocumentProcessorPlugin(255,
                new LanguageFilter(lm),
                new ThreadLocalSentenceExtractorProvider(lm),
                new DocumentKeywordExtractor(new TermFrequencyDict(lm)),
                new DocumentLengthLogic(100),
                new DefaultSpecialization(new SummaryExtractor(
                        255,
                        new DomFilterHeuristic(255),
                        new TagDensityHeuristic(255),
                        new OpenGraphDescriptionHeuristic(),
                        new MetaDescriptionHeuristic(),
                        new FallbackHeuristic()
                ),
                        new TitleExtractor(255)
                        ));
    }

    @Test
    void createDetails() throws IOException, URISyntaxException, DisqualifiedException {
        byte[] pdfBytes = Files.readAllBytes(Path.of("/home/st_work/Work/sample.pdf"));
        var doc = new CrawledDocument("test", "https://www.example.com/sample.pdf", "application/pdf", Instant.now().toString(), 200, "OK", "OK", "", pdfBytes, false, null, null);
        var details = plugin.createDetails(doc, new LinkTexts(), DocumentClass.NORMAL);

        System.out.println(details);
    }
}