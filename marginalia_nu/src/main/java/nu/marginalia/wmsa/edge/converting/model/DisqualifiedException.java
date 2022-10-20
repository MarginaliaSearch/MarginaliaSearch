package nu.marginalia.wmsa.edge.converting.model;

import nu.marginalia.wmsa.edge.crawling.model.CrawlerDocumentStatus;

public class DisqualifiedException extends Exception {
    public final DisqualificationReason reason;

    public DisqualifiedException(DisqualificationReason reason) {
        this.reason = reason;
    }

    public DisqualifiedException(CrawlerDocumentStatus crawlerStatus) {
        this.reason = DisqualificationReason.fromCrawlerStatus(crawlerStatus);
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    public enum DisqualificationReason {
        LENGTH,
        CONTENT_TYPE,
        LANGUAGE,
        STATUS,
        QUALITY,
        ACCEPTABLE_ADS,
        FORBIDDEN,
        SHORT_CIRCUIT,

        PROCESSING_EXCEPTION,

        BAD_CONTENT_TYPE,
        BAD_CHARSET,
        REDIRECT,
        ROBOTS_TXT,
        ERROR,
        Timeout, // Don't you dare
        BAD_CANONICAL
        ;

        public static DisqualificationReason fromCrawlerStatus(CrawlerDocumentStatus crawlerStatus) {
            return DisqualificationReason.valueOf(crawlerStatus.name());
        }
    }
}
