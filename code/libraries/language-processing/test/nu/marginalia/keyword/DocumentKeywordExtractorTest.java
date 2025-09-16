package nu.marginalia.keyword;

import nu.marginalia.WmsaHome;
import nu.marginalia.dom.DomPruningFilter;
import nu.marginalia.language.config.LanguageConfigLocation;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.model.UnsupportedLanguageException;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.sequence.CodedSequence;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class DocumentKeywordExtractorTest {

    static DocumentKeywordExtractor extractor = new DocumentKeywordExtractor();
    static SentenceExtractor se;


    @BeforeAll
    public static void setUpAll() throws IOException, ParserConfigurationException, SAXException {
        se = new SentenceExtractor(new LanguageConfiguration(WmsaHome.getLanguageModels(), new LanguageConfigLocation.Experimental()), WmsaHome.getLanguageModels());
    }

    @Test
    public void testKeyboards2() throws IOException, URISyntaxException, UnsupportedLanguageException {
        var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("test-data/keyboards.html"),
                "Could not load word frequency table");
        String html = new String(resource.readAllBytes(), Charset.defaultCharset());
        var doc = Jsoup.parse(html);
        doc.filter(new DomPruningFilter(0.5));

        var keywords = extractor.extractKeywords(se.extractSentences(doc), new LinkTexts(), new EdgeUrl("https://pmortensen.eu/world2/2021/12/24/rapoo-mechanical-keyboards-gotchas-and-setup/"));

        keywords.getWordToMeta().forEach((k, v) -> {
            if (k.contains("_")) {
                System.out.println(k + " " + WordFlags.decode(v));
            }
        });
    }


    @Test
    public void testMadonna() throws IOException, URISyntaxException, UnsupportedLanguageException {
        var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("test-data/madonna.html"),
                "Could not load word frequency table");
        String html = new String(resource.readAllBytes(), Charset.defaultCharset());
        var doc = Jsoup.parse(html);
        doc.filter(new DomPruningFilter(0.5));

        var keywords = extractor.extractKeywords(
                se.extractSentences(doc),
                new LinkTexts(), new EdgeUrl("https://encyclopedia.marginalia.nu/article/Don't_Tell_Me_(Madonna_song)")
        );

        var keywordsBuilt = keywords.build();

        Map<String, Byte> flags = new HashMap<>();
        Map<String, CodedSequence> positions = new HashMap<>();

        for (int i = 0; i < keywordsBuilt.size(); i++) {
            String keyword = keywordsBuilt.keywords().get(i);
            byte metadata = keywordsBuilt.metadata()[i]
                    ;

            if (Set.of("dirty", "blues").contains(keyword)) {
                flags.put(keyword, metadata);
                positions.put(keyword, keywordsBuilt.positions().get(i));

            }
        }

        Assertions.assertTrue(flags.containsKey("dirty"));
        Assertions.assertTrue(flags.containsKey("blues"));
        Assertions.assertNotEquals(
                positions.get("dirty"),
                positions.get("blues")
                );
    }

}