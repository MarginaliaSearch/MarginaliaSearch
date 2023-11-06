package nu.marginalia.crawl.spec;

import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.crawlspec.CrawlSpecRecord;

import java.util.List;
import java.util.stream.Stream;

public interface CrawlSpecProvider {
    int totalCount() throws Exception;
    Stream<CrawlSpecRecord> stream();

    default List<EdgeDomain> getDomains() {
        return stream().map(CrawlSpecRecord::getDomain).map(EdgeDomain::new).toList();
    }
}
