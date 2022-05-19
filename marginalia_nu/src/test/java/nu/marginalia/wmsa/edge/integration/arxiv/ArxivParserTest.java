package nu.marginalia.wmsa.edge.integration.arxiv;

import nu.marginalia.util.TestLanguageModels;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.DocumentKeywordExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.SentenceExtractor;
import nu.marginalia.wmsa.edge.integration.arxiv.model.ArxivMetadata;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

@Disabled // this isn't used and the test is hella slow
class ArxivParserTest {
    final LanguageModels lm = TestLanguageModels.getLanguageModels();

    @Test
    void parse() throws IOException {
        var parser = new ArxivParser();
        var data = parser.parse(new File("/home/vlofgren/Work/arxiv/arxiv-metadata-oai-snapshot.json"));

        data.stream().map(ArxivMetadata::getAbstract).limit(100).forEach(System.out::println);
    }

    @Test
    void extractKeywords() throws IOException {
        var dict = new NGramDict(lm);

        DocumentKeywordExtractor documentKeywordExtractor = new DocumentKeywordExtractor(dict);

        var parser = new ArxivParser();
        var data = parser.parse(new File("/home/vlofgren/Work/arxiv/arxiv-metadata-oai-snapshot.json"));

        var se = new SentenceExtractor(lm);

        data.stream().map(meta -> documentKeywordExtractor.extractKeywords(se.extractSentences(meta.getAbstract(), meta.getTitle()))).limit(100).forEach(System.out::println);
    }
}