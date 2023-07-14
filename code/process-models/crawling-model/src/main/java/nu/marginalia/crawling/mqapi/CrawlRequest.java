package nu.marginalia.crawling.mqapi;

import nu.marginalia.db.storage.model.FileStorageId;

/** A request to start a crawl */
public class CrawlRequest {
    FileStorageId specStorage;
    FileStorageId crawlStorage;
}
