package nu.marginalia.wmsa.edge.integration.stackoverflow;

import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.DocumentKeywordExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.SentenceExtractor;
import nu.marginalia.util.ParallelPipe;
import nu.marginalia.wmsa.edge.integration.model.BasicDocumentData;
import nu.marginalia.wmsa.edge.integration.stackoverflow.model.StackOverflowPost;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Path;

public class StackOverflowPostsTest {
    LanguageModels lm = new LanguageModels(
            Path.of("/home/vlofgren/Work/ngrams/ngrams-generous-emstr.bin"),
            Path.of("/home/vlofgren/Work/ngrams/tfreq-new-algo4.bin"),
            Path.of("/home/vlofgren/Work/ngrams/opennlp-en-ud-ewt-sentence-1.0-1.9.3.bin"),
            Path.of("/home/vlofgren/Work/ngrams/English.RDR"),
            Path.of("/home/vlofgren/Work/ngrams/English.DICT"),
            Path.of("/home/vlofgren/Work/ngrams/opennlp-en-ud-ewt-tokens-1.0-1.9.3.bin")
    );

    @Test
    public void test() throws IOException, ParserConfigurationException, SAXException, InterruptedException {
        var documentKeywordExtractor = new DocumentKeywordExtractor(new NGramDict(lm));

        ThreadLocal<StackOverflowPostProcessor> processor = ThreadLocal.withInitial(() -> {
            return new StackOverflowPostProcessor(new SentenceExtractor(lm), documentKeywordExtractor);
        });

        var pipe = new ParallelPipe<StackOverflowPost, BasicDocumentData>("pipe", 10, 5, 2) {
            @Override
            public BasicDocumentData onProcess(StackOverflowPost stackOverflowPost) {
                return processor.get().process(stackOverflowPost);
            }

            @Override
            public void onReceive(BasicDocumentData stackOverflowIndexData) {
                System.out.println(stackOverflowIndexData.url);
            }
        };

        var reader = new StackOverflowPostsReader("/mnt/storage/downloads.new/stackexchange/sites/philosophy/Posts.xml", new EdgeDomain("philosophy.stackexchange.com"),
                pipe::accept);
        reader.join();
        System.out.println("Waiting for pipe");
        pipe.join();
    }
}
