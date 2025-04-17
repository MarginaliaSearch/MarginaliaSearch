package nu.marginalia.crawling;

import nu.marginalia.crawl.fetcher.ContentTags;
import nu.marginalia.crawl.fetcher.HttpFetcher;
import nu.marginalia.crawl.fetcher.HttpFetcherImpl;
import nu.marginalia.crawl.fetcher.warc.WarcRecorder;
import nu.marginalia.crawl.retreival.CrawlDelayTimer;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.body.ContentTypeLogic;
import nu.marginalia.model.body.DocumentBodyExtractor;
import nu.marginalia.model.body.DocumentBodyResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class HttpFetcherTest {

    @Test
    void testUrlPattern() throws Exception {
        ContentTypeLogic contentTypeLogic = new ContentTypeLogic();

        Assertions.assertFalse(contentTypeLogic.isUrlLikeBinary(new EdgeUrl("https://marginalia.nu/log.txt")));
        Assertions.assertTrue(contentTypeLogic.isUrlLikeBinary(new EdgeUrl("https://marginalia.nu/log.bin")));
        Assertions.assertTrue(contentTypeLogic.isUrlLikeBinary(new EdgeUrl("https://marginalia.nu/log.tar.gz")));
        Assertions.assertFalse(contentTypeLogic.isUrlLikeBinary(new EdgeUrl("https://marginalia.nu/log.htm")));
        Assertions.assertFalse(contentTypeLogic.isUrlLikeBinary(new EdgeUrl("https://marginalia.nu/log.html")));
        Assertions.assertFalse(contentTypeLogic.isUrlLikeBinary(new EdgeUrl("https://marginalia.nu/log")));
        Assertions.assertFalse(contentTypeLogic.isUrlLikeBinary(new EdgeUrl("https://marginalia.nu/log.php?id=1")));
    }

    @Test
    void fetchUTF8() throws Exception {
        var fetcher = new HttpFetcherImpl("nu.marginalia.edge-crawler");
        try (var recorder = new WarcRecorder()) {
            var result = fetcher.fetchContent(new EdgeUrl("https://www.marginalia.nu"), recorder, new CrawlDelayTimer(100), ContentTags.empty(), HttpFetcher.ProbeType.FULL);
            if (DocumentBodyExtractor.asString(result) instanceof DocumentBodyResult.Ok bodyOk) {
                System.out.println(bodyOk.contentType());
            }
        }
    }

    @Test
    void testSitemapMarginalia() {
        var fetcher = new HttpFetcherImpl("nu.marginalia.edge-crawler");
        fetcher.fetchSitemapUrls("https://www.marginalia.nu/sitemap.xml", new CrawlDelayTimer(1)).forEach(System.out::println);
    }

    @Test
    void fetchText() throws Exception {
        var fetcher = new HttpFetcherImpl("nu.marginalia.edge-crawler");

        try (var recorder = new WarcRecorder()) {
            var result = fetcher.fetchContent(new EdgeUrl("https://www.marginalia.nu/robots.txt"), recorder, new CrawlDelayTimer(100), ContentTags.empty(), HttpFetcher.ProbeType.FULL);
            if (DocumentBodyExtractor.asString(result) instanceof DocumentBodyResult.Ok bodyOk) {
                System.out.println(bodyOk.contentType());
            }
        }
    }
}