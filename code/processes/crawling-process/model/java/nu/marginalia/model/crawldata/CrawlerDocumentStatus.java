package nu.marginalia.model.crawldata;

public enum CrawlerDocumentStatus {
    OK,
    BAD_CONTENT_TYPE,
    BAD_CHARSET,
    REDIRECT,
    ROBOTS_TXT,
    ERROR,
    BAD_CANONICAL,
    Timeout
}
