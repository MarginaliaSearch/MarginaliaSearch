package nu.marginalia.wmsa.edge.integration.arxiv;

import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.DocumentKeywordExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.SentenceExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.DocumentLanguageData;
import nu.marginalia.wmsa.edge.integration.arxiv.model.ArxivMetadata;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Disabled // this isn't used and the test is hella slow
class ArxivParserTest {
    LanguageModels lm = new LanguageModels(
            Path.of("/home/vlofgren/Work/ngrams/ngrams-generous-emstr.bin"),
            Path.of("/home/vlofgren/Work/ngrams/tfreq-new-algo.bin"),
            Path.of("/home/vlofgren/Work/ngrams/opennlp-en-ud-ewt-sentence-1.0-1.9.3.bin"),
            Path.of("/home/vlofgren/Work/ngrams/English.RDR"),
            Path.of("/home/vlofgren/Work/ngrams/English.DICT"),
            Path.of("/home/vlofgren/Work/ngrams/opennlp-en-ud-ewt-tokens-1.0-1.9.3.bin")
    );

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