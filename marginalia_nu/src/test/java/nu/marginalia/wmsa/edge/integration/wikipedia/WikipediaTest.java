package nu.marginalia.wmsa.edge.integration.wikipedia;

import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.crawler.domain.language.DocumentDebugger;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.DocumentKeywordExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.SentenceExtractor;
import nu.marginalia.util.ParallelPipe;
import nu.marginalia.wmsa.edge.integration.model.BasicDocumentData;
import nu.marginalia.wmsa.edge.integration.wikipedia.model.WikipediaArticle;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

public class WikipediaTest {
    LanguageModels lm = new LanguageModels(
            Path.of("/home/vlofgren/Work/ngrams/ngrams-generous-emstr.bin"),
            Path.of("/home/vlofgren/Work/ngrams/tfreq-new-algo4.bin"),
            Path.of("/home/vlofgren/Work/ngrams/opennlp-en-ud-ewt-sentence-1.0-1.9.3.bin"),
            Path.of("/home/vlofgren/Work/ngrams/English.RDR"),
            Path.of("/home/vlofgren/Work/ngrams/English.DICT"),
            Path.of("/home/vlofgren/Work/ngrams/opennlp-en-ud-ewt-tokens-1.0-1.9.3.bin")
    );

    @Test @SneakyThrows
    public void test() {
        var documentKeywordExtractor = new DocumentKeywordExtractor(new NGramDict(lm));
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
        var documentKeywordExtractor = new DocumentKeywordExtractor(new NGramDict(lm));
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
