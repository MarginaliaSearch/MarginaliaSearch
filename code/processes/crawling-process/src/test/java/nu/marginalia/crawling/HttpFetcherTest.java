package nu.marginalia.crawling;

import lombok.SneakyThrows;
import nu.marginalia.crawl.retreival.RateLimitException;
import nu.marginalia.crawl.retreival.fetcher.ContentTags;
import nu.marginalia.crawl.retreival.fetcher.HttpFetcherImpl;
import nu.marginalia.crawl.retreival.logic.ContentTypeLogic;
import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
    void fetchUTF8() throws URISyntaxException, RateLimitException {
        var fetcher = new HttpFetcherImpl("nu.marginalia.edge-crawler");
        var str = fetcher.fetchContent(new EdgeUrl("https://www.marginalia.nu"), ContentTags.empty());
        System.out.println(str.contentType);
    }

    @Test
    void fetchText() throws URISyntaxException, RateLimitException {
        var fetcher = new HttpFetcherImpl("nu.marginalia.edge-crawler");
        var str = fetcher.fetchContent(new EdgeUrl("https://www.marginalia.nu/robots.txt"), ContentTags.empty());
        System.out.println(str);
    }
}