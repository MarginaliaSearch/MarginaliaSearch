package nu.marginalia.mqapi.crawling;

import lombok.AllArgsConstructor;
import nu.marginalia.storage.model.FileStorageId;

import java.util.List;

/** A request to start a crawl */
@AllArgsConstructor
public class CrawlRequest {
    /** (optional)  Crawl spec(s) for sourcing domains to crawl.  If not set,
     * the EC_DOMAIN table will be consulted and domains with the corresponding
     * node affinity will be used.
     */
    public List<FileStorageId> specStorage;

    /** (optional)  Name of a single domain to be re-crawled */
    public String targetDomainName;

    /** File storage where the crawl data will be written.  If it contains existing crawl data,
     * this crawl data will be referenced for e-tags and last-mofified checks.
     */
    public FileStorageId crawlStorage;

    public static CrawlRequest forSpec(FileStorageId specStorage, FileStorageId crawlStorage) {
        return new CrawlRequest(List.of(specStorage), null, crawlStorage);
    }

    public static CrawlRequest forSingleDomain(String targetDomainName, FileStorageId crawlStorage) {
        return new CrawlRequest(null, targetDomainName, crawlStorage);
    }

    public static CrawlRequest forRecrawl(FileStorageId crawlStorage) {
        return new CrawlRequest(null, null, crawlStorage);
    }

}
