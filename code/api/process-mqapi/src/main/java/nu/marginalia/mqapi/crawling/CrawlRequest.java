package nu.marginalia.mqapi.crawling;

import lombok.AllArgsConstructor;
import nu.marginalia.db.storage.model.FileStorageId;

/** A request to start a crawl */
@AllArgsConstructor
public class CrawlRequest {
    FileStorageId specStorage;
    FileStorageId crawlStorage;
}
