package nu.marginalia.wmsa.edge.integration.wikipedia;

import lombok.SneakyThrows;
import nu.marginalia.util.ParallelPipe;
import nu.marginalia.util.TestLanguageModels;
import nu.marginalia.util.language.DocumentDebugger;
import nu.marginalia.util.language.conf.LanguageModels;
import nu.marginalia.util.language.processing.DocumentKeywordExtractor;
import nu.marginalia.util.language.processing.SentenceExtractor;
import nu.marginalia.wmsa.edge.assistant.dict.TermFrequencyDict;
import nu.marginalia.wmsa.edge.integration.model.BasicDocumentData;
import nu.marginalia.wmsa.edge.integration.wikipedia.model.WikipediaArticle;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;

@Tag("slow")
public class WikipediaTest {
    final LanguageModels lm = TestLanguageModels.getLanguageModels();

    @Test @SneakyThrows
    public void test() {
        var documentKeywordExtractor = new DocumentKeywordExtractor(new TermFrequencyDict(lm));
        ThreadLocal<WikipediaProcessor> processor = ThreadLocal.withInitial(() -> {
            return new WikipediaProcessor(new SentenceExtractor(lm), documentKeywordExtractor);
        });

        var pipe = new ParallelPipe<WikipediaArticle, BasicDocumentData>("pipe", 10, 5, 2) {
            @Override
            public BasicDocumentData onProcess(WikipediaArticle stackOverflowPost) {
                return processor.get().process(stackOverflowPost);
            }

            @Override
            public void onReceive(BasicDocumentData indexData) {
                System.out.println(indexData.url);
                System.out.println(indexData.title);
                System.out.println(indexData.description);
            }
        };

        var reader = new WikipediaReader("/home/vlofgren/Work/wikipedia_en_100_nopic_2021-06.zim", new EdgeDomain("encyclopedia.marginalia.nu"),
                pipe::accept);
        reader.join();
    }


    @Test @SneakyThrows
    public void test2() {
        var documentKeywordExtractor = new DocumentKeywordExtractor(new TermFrequencyDict(lm));
        var debugger = new DocumentDebugger(lm);

        ThreadLocal<WikipediaProcessor> processor = ThreadLocal.withInitial(() -> {
            return new WikipediaProcessor(new SentenceExtractor(lm), documentKeywordExtractor);
        });

        var reader = new WikipediaReader("/home/vlofgren/Work/wikipedia_en_100_nopic_2021-06.zim", new EdgeDomain("encyclopedia.marginalia.nu"),
                article -> {
                    try {
                        debugger.debugDocument(article.url.getPath(), Jsoup.parse(article.body));

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

        reader.join();
        debugger.writeIndex();
    }
}
