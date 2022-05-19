package nu.marginalia.wmsa.edge.crawler.domain;

import com.opencsv.exceptions.CsvValidationException;
import lombok.SneakyThrows;
import nu.marginalia.util.TestLanguageModels;
import nu.marginalia.wmsa.edge.archive.client.ArchiveClient;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.crawler.domain.language.LanguageFilter;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.DocumentKeywordExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.SentenceExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.processor.HtmlProcessor;
import nu.marginalia.wmsa.edge.crawler.domain.processor.PlainTextProcessor;
import nu.marginalia.wmsa.edge.crawler.fetcher.HttpFetcher;
import nu.marginalia.wmsa.edge.crawler.worker.GeoIpBlocklist;
import nu.marginalia.wmsa.edge.crawler.worker.IpBlockList;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.crawl.EdgeIndexTask;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Collectors;

@Tag("nobuild")
@Tag("db")
@ResourceLock(value = "mariadb", mode = ResourceAccessMode.READ_WRITE)
@Execution(ExecutionMode.SAME_THREAD)
class DomainCrawlerTest2 {

    @SneakyThrows
    @Test
    public void test() throws CsvValidationException, IOException {
        var fetcher = new HttpFetcher("search.marginalia.nu");
        var ingress = new EdgeIndexTask(new EdgeDomain("memex.marginalia.nu"), 0, 10, 1.);
        ingress.urls.add(new EdgeUrl("https://memex.marginalia.nu/"));

        LanguageModels lm = TestLanguageModels.getLanguageModels();
        var dict = new NGramDict(lm);
        HtmlProcessor processor = new HtmlProcessor(new DocumentKeywordExtractor(dict),new SentenceExtractor(lm));

        DomainCrawler dc = new DomainCrawler(fetcher,
                Mockito.mock(PlainTextProcessor.class),
                processor,
                Mockito.mock(ArchiveClient.class),
                new DomainCrawlerRobotsTxt(fetcher, "search.marginalia.nu")
        , new LanguageFilter(), ingress , new IpBlockList(new GeoIpBlocklist()));
        var res = dc.crawlToExhaustion(500, ()->true);
        var wordsByCount = res.pageContents.values().stream().map(pc -> pc.words.get(IndexBlock.Top)).flatMap(top -> top.getWords().stream()).collect(Collectors.toMap(w -> w, w->1, Integer::sum));
        wordsByCount.entrySet().stream().filter(e -> dict.getTermFreq(e.getKey()) > 10_000).filter(e -> e.getValue()>2).sorted(Comparator.comparing(e -> e.getValue() / Math.max(1, dict.getTermFreq(e.getKey())))).forEach(System.out::println);

    }

}