package nu.marginalia.wmsa.edge.crawling.model;

public enum CrawlerDocumentStatus {
    OK,
    BAD_CONTENT_TYPE,
    BAD_CHARSET,
    REDIRECT,
    ROBOTS_TXT,
    ERROR,
    Timeout
}
