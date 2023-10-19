package nu.marginalia.crawl.spec;

import nu.marginalia.model.crawlspec.CrawlSpecRecord;

import java.util.stream.Stream;

public interface CrawlSpecProvider {
    int totalCount() throws Exception;
    Stream<CrawlSpecRecord> stream();
}
