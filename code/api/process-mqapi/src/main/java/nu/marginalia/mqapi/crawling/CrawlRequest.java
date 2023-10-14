package nu.marginalia.mqapi.crawling;

import lombok.AllArgsConstructor;
import nu.marginalia.storage.model.FileStorageId;

import java.util.List;

/** A request to start a crawl */
@AllArgsConstructor
public class CrawlRequest {
    public List<FileStorageId> specStorage;
    public FileStorageId crawlStorage;
}
