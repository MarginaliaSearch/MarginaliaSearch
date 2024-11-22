package nu.marginalia.model.crawldata;

public sealed interface SerializableCrawlData permits CrawledDocument, CrawledDomain {
    String getDomain();
}
