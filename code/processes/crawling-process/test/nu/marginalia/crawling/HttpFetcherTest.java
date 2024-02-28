package nu.marginalia.crawling;

import lombok.SneakyThrows;
import nu.marginalia.crawl.retreival.RateLimitException;
import nu.marginalia.crawl.retreival.fetcher.ContentTags;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcherImpl;
import nu.marginalia.crawling.body.DocumentBodyExtractor;
import nu.marginalia.crawling.body.DocumentBodyResult;
import nu.marginalia.crawl.retreival.fetcher.warc.WarcRecorder;
import nu.marginalia.crawling.body.ContentTypeLogic;
import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;

class HttpFetcherTest {

    @SneakyThrows
    @Test
    void testUrlPattern() {
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
    void fetchUTF8() throws URISyntaxException, RateLimitException, IOException {
        var fetcher = new HttpFetcherImpl("nu.marginalia.edge-crawler");
        try (var recorder = new WarcRecorder()) {
            var result = fetcher.fetchContent(new EdgeUrl("https://www.marginalia.nu"), recorder, ContentTags.empty());
            if (DocumentBodyExtractor.asString(result) instanceof DocumentBodyResult.Ok bodyOk) {
                System.out.println(bodyOk.contentType());
            }
        }
    }

    @Test
    void fetchText() throws URISyntaxException, RateLimitException, IOException {
        var fetcher = new HttpFetcherImpl("nu.marginalia.edge-crawler");

        try (var recorder = new WarcRecorder()) {
            var result = fetcher.fetchContent(new EdgeUrl("https://www.marginalia.nu/robots.txt"), recorder, ContentTags.empty());
            if (DocumentBodyExtractor.asString(result) instanceof DocumentBodyResult.Ok bodyOk) {
                System.out.println(bodyOk.contentType());
            }
        }
    }
}