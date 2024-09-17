package nu.marginalia.crawl.retreival;

import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.body.HttpFetchResult;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.model.crawldata.CrawlerDocumentStatus;

import java.time.LocalDateTime;
import java.util.Objects;

public class CrawledDocumentFactory {

    public static CrawledDocument createHardErrorRsp(EdgeUrl url, Exception why) {
        return CrawledDocument.builder()
                .crawlerStatus(CrawlerDocumentStatus.ERROR.toString())
                .crawlerStatusDesc(why.getClass().getSimpleName() + ": " + why.getMessage())
                .timestamp(LocalDateTime.now().toString())
                .url(url.toString())
                .build();
    }

    public static CrawledDocument createUnknownHostError(EdgeUrl url) {
        return CrawledDocument.builder()
                .crawlerStatus(CrawlerDocumentStatus.ERROR.toString())
                .crawlerStatusDesc("Unknown Host")
                .timestamp(LocalDateTime.now().toString())
                .url(url.toString())
                .build();
    }

    public static CrawledDocument createTimeoutErrorRsp(EdgeUrl url) {
        return CrawledDocument.builder()
                .crawlerStatus("Timeout")
                .timestamp(LocalDateTime.now().toString())
                .url(url.toString())
                .build();
    }

    public static CrawledDocument createErrorResponse(EdgeUrl url, HttpFetchResult.ResultOk rsp, CrawlerDocumentStatus status, String why) {
        return CrawledDocument.builder()
                .crawlerStatus(status.toString())
                .crawlerStatusDesc(why)
                .headers(rsp.headers().toString())
                .contentType(Objects.requireNonNullElse(rsp.headers().get("Content-Type"), ""))
                .timestamp(LocalDateTime.now().toString())
                .httpStatus(rsp.statusCode())
                .url(url.toString())
                .build();
    }
    public static CrawledDocument createErrorResponse(EdgeUrl url, String contentType, int statusCode, CrawlerDocumentStatus status, String why) {
        return CrawledDocument.builder()
                .crawlerStatus(status.toString())
                .crawlerStatusDesc(why)
                .headers("")
                .contentType(contentType)
                .timestamp(LocalDateTime.now().toString())
                .httpStatus(statusCode)
                .url(url.toString())
                .build();
    }

    public static CrawledDocument createRedirectResponse(EdgeUrl url, HttpFetchResult.ResultOk rsp, EdgeUrl responseUrl) {

        return CrawledDocument.builder()
                .crawlerStatus(CrawlerDocumentStatus.REDIRECT.name())
                .redirectUrl(responseUrl.toString())
                .headers(rsp.headers().toString())
                .contentType(Objects.requireNonNullElse(rsp.headers().get("Content-Type"), ""))
                .timestamp(LocalDateTime.now().toString())
                .httpStatus(rsp.statusCode())
                .url(url.toString())
                .build();
    }

    public static CrawledDocument createRobotsError(EdgeUrl url) {
        return CrawledDocument.builder()
                .url(url.toString())
                .timestamp(LocalDateTime.now().toString())
                .httpStatus(-1)
                .crawlerStatus(CrawlerDocumentStatus.ROBOTS_TXT.name())
                .build();
    }
    public static CrawledDocument createRetryError(EdgeUrl url) {
        return CrawledDocument.builder()
                .url(url.toString())
                .timestamp(LocalDateTime.now().toString())
                .httpStatus(429)
                .crawlerStatus(CrawlerDocumentStatus.ERROR.name())
                .build();
    }
}
