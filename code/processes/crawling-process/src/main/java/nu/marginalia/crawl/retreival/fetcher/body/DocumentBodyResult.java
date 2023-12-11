package nu.marginalia.crawl.retreival.fetcher.body;

import nu.marginalia.crawling.model.CrawlerDocumentStatus;

public sealed interface DocumentBodyResult {
    record Ok(String contentType, String body) implements DocumentBodyResult { }
    record Error(CrawlerDocumentStatus status, String why) implements DocumentBodyResult { }
}
