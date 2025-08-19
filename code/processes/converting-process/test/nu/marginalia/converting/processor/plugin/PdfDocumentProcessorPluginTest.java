package nu.marginalia.converting.processor.plugin;

import nu.marginalia.WmsaHome;
import nu.marginalia.converting.processor.DocumentClass;
import nu.marginalia.converting.processor.logic.DocumentLengthLogic;
import nu.marginalia.converting.processor.logic.TitleExtractor;
import nu.marginalia.converting.processor.plugin.specialization.DefaultSpecialization;
import nu.marginalia.converting.processor.summary.SummaryExtractor;
import nu.marginalia.converting.processor.summary.heuristic.*;
import nu.marginalia.keyword.DocumentKeywordExtractor;
import nu.marginalia.keyword.LinkTexts;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.sentence.ThreadLocalSentenceExtractorProvider;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

@Tag("flaky")
class PdfDocumentProcessorPluginTest {
    static PdfDocumentProcessorPlugin plugin;

    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        var lm = WmsaHome.getLanguageModels();
        plugin = new PdfDocumentProcessorPlugin(255,
                new LanguageConfiguration(lm),
                new ThreadLocalSentenceExtractorProvider(new LanguageConfiguration(lm), lm),
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
    public AbstractDocumentProcessorPlugin.DetailsWithWords testPdfFile(byte[] pdfBytes) throws Exception {
        var doc = new CrawledDocument("test", "https://www.example.com/sample.pdf", "application/pdf", Instant.now().toString(), 200, "OK", "OK", "", pdfBytes, false, -1, null, null);
        return plugin.createDetails(doc, new LinkTexts(), Set.of(), DocumentClass.NORMAL);
    }

    public AbstractDocumentProcessorPlugin.DetailsWithWords testPdfFile(Path file) throws Exception {
        return testPdfFile(Files.readAllBytes(file));
    }

    private byte[] downloadPDF(String url) throws IOException, URISyntaxException {
        HttpURLConnection conn = (HttpURLConnection) new URI(url).toURL().openConnection();
        try {
            return conn.getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            conn.disconnect();
        }
    }


    @Disabled
    @Test
    void testingTool() throws Exception {
        System.out.println(testPdfFile(Path.of("/home/st_work/Work/sample.pdf")).details().title);
        System.out.println(testPdfFile(Path.of("/home/st_work/Work/sample2.pdf")).details().title);
        System.out.println(testPdfFile(Path.of("/home/st_work/Work/sample3.pdf")).details().title);
        System.out.println(testPdfFile(Path.of("/home/st_work/Work/sample4.pdf")).details().title);
        System.out.println(testPdfFile(Path.of("/home/st_work/Work/sample5.pdf")).details().title);
        System.out.println(testPdfFile(Path.of("/home/st_work/Work/sample6.pdf")).details().title);
    }

    @Disabled
    @Test
    void testingTool2() throws Exception {
        System.out.println(plugin.convertPdfToHtml(Files.readAllBytes(Path.of("/home/st_work/Work/sample6.pdf"))));
    }

    @Test
    void testMarginaliaSample() throws Exception {
        var doc = plugin.convertPdfToHtml(downloadPDF("https://www.marginalia.nu/junk/test.pdf"));
        System.out.println(doc.html());
    }
}