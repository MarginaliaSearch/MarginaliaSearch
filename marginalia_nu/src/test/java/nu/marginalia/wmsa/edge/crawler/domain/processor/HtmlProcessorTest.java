package nu.marginalia.wmsa.edge.crawler.domain.processor;

import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.DocumentKeywordExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.SentenceExtractor;
import nu.marginalia.wmsa.edge.model.crawl.EdgeRawPageContents;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Disabled
class HtmlProcessorTest {
    Logger logger = LoggerFactory.getLogger(getClass());

    LanguageModels lm = new LanguageModels(
            Path.of("/home/vlofgren/Work/ngrams/ngrams-generous-emstr.bin"),
            Path.of("/home/vlofgren/Work/ngrams/tfreq-generous-emstr.bin"),
            Path.of("/home/vlofgren/Work/ngrams/opennlp-sentence.bin"),
            Path.of("/var/lib/wmsa/model/English.RDR"),
            Path.of("/var/lib/wmsa/model/English.DICT"),
            Path.of("/home/vlofgren/Work/ngrams/opennlp-tok.bin")
    );
    HtmlProcessor processor = new HtmlProcessor(new DocumentKeywordExtractor(new NGramDict(lm)),new SentenceExtractor(lm));

    @Test
    @Disabled
    void processHtmlPage0() throws IOException, URISyntaxException {
        List<String> urls = List.of("https://www.marginalia.nu/",
                "https://www.marginalia.nu/00-skrifter/",
                "https://www.marginalia.nu/2021-04-smart/",
                "https://www.marginalia.nu/2020-06-att-l%C3%A4ra/",
                "https://www.marginalia.nu/2020-04-grader-av-liv/",
                "https://www.marginalia.nu/2020-03-battre-internet/",
                "https://www.marginalia.nu/2020-05-dr%C3%B6m/",
                "https://www.marginalia.nu/2020-02-faktaresistens/",
                "https://www.marginalia.nu/2020-01-dialog-forfattaren-i-verket/",
                "https://search.marginalia.nu/about.html",
                "https://www.putty.org/",
                "https://www.chiark.greenend.org.uk/~sgtatham/putty/latest.html",
                "https://legacy.3drealms.com/duke3d/",
                "http://classics.mit.edu/Plato/stateman.html",
                "http://www.southaustralianhistory.com.au/bruce.htm",
                "http://www.castlecraft.com/main.htm",
                "http://www.discoveryvallarta.com/gaybars.html",
                "https://twitterrific.com/ios"
                );



        for (String url : urls) {



            var doc = Jsoup.parse(new URL(url), 15000);
            var res = processor.processHtmlPage(new EdgeRawPageContents(new EdgeUrl("http://www.example.com/"), new EdgeUrl("http://www.example.com/"), doc.html(), null, "", true, LocalDateTime.now().toString()),
                    doc);


            System.out.println("Q:" + res.metadata.quality());
            System.out.println(100*Math.exp(res.metadata.quality()));
            System.out.println(res.metadata.rawLength + ", " + res.metadata.textBodyLength);

            System.out.println(res.metadata.totalWords + ", " + res.metadata.textDistinctWords / res.metadata.totalWords);
            for (var words : res.words.values()) {
                logger.info("{}: {}", words.block, words.getWords());
            }
        }
    }

    @Test @Disabled
    void processHtmlPage() throws IOException, URISyntaxException {
        var doc = Jsoup.parse(new URL("https://aysia.blondeninna.com/"), 5000);
        var res = processor.processHtmlPage(new EdgeRawPageContents(new EdgeUrl("http://www.example.com/"), new EdgeUrl("http://www.example.com/"), doc.data(), null, "", true, LocalDateTime.now().toString()),
                doc);
        System.out.println(res);
        System.out.println("--");
        System.out.println(res.metadata.title);
        System.out.println("--");
        System.out.println(res.metadata.description);
        System.out.println(res.metadata.textDistinctWords);

        System.out.println(res.metadata.smutCoefficient);
    }

    @Test @Disabled
    void processHtmlPage3() throws IOException, URISyntaxException {
        var doc = Jsoup.parse(new URL("http://thelagniappechateau.com/wwwboard/720p/starcraft-2-pc-iso-download/"), 5000);
        var res = processor.processHtmlPage(new EdgeRawPageContents(new EdgeUrl("http://www.example.com/"), new EdgeUrl("http://www.example.com/"), doc.data(), null, "", true, LocalDateTime.now().toString()),
                 doc);
        System.out.println(res);
        System.out.println("--");
        System.out.println(res.metadata.title);
        System.out.println("--");
        System.out.println(res.metadata.description);
        System.out.println(res.metadata.textDistinctWords);

        System.out.println(res.metadata.smutCoefficient);
    }

    @Test @Disabled
    void processHtmlPage2() throws IOException {

        var doc = Jsoup.parse(new String(Files.readAllBytes(Path.of("/home/vlofgren/monadnock.html"))));
        doc.getElementsByTag("a").forEach(System.out::println);

    }
}