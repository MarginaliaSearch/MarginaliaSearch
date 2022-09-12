package nu.marginalia.wmsa.edge.integration.stackoverflow;

import nu.marginalia.util.ParallelPipe;
import nu.marginalia.util.TestLanguageModels;
import nu.marginalia.util.language.conf.LanguageModels;
import nu.marginalia.util.language.processing.DocumentKeywordExtractor;
import nu.marginalia.util.language.processing.SentenceExtractor;
import nu.marginalia.wmsa.edge.assistant.dict.TermFrequencyDict;
import nu.marginalia.wmsa.edge.integration.model.BasicDocumentData;
import nu.marginalia.wmsa.edge.integration.stackoverflow.model.StackOverflowPost;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;

public class StackOverflowPostsTest {
    final LanguageModels lm = TestLanguageModels.getLanguageModels();

    @Test @Disabled("this is stupidly slow")
    public void test() throws ParserConfigurationException, SAXException, InterruptedException {
        var documentKeywordExtractor = new DocumentKeywordExtractor(new TermFrequencyDict(lm));

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
