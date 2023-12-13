package nu.marginalia.crawl.retreival.revisit;

import lombok.SneakyThrows;
import nu.marginalia.crawl.retreival.CrawlDataReference;
import nu.marginalia.crawl.retreival.fetcher.ContentTags;
import nu.marginalia.crawl.retreival.fetcher.warc.WarcRecorder;
import nu.marginalia.crawling.body.DocumentBodyExtractor;
import nu.marginalia.crawling.body.HttpFetchResult;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.model.EdgeUrl;

import javax.annotation.Nullable;

public record DocumentWithReference(
        @Nullable CrawledDocument doc,
        @Nullable CrawlDataReference reference) {

    private static final DocumentWithReference emptyInstance = new DocumentWithReference(null, null);

    public static DocumentWithReference empty() {
        return emptyInstance;
    }

    /** Returns true if the provided document is the same as the reference document,
     * or if the result was retained via HTTP 304.
     */
    public boolean isSame(HttpFetchResult result) {
        if (result instanceof HttpFetchResult.ResultSame)
            return true;
        if (result instanceof HttpFetchResult.ResultRetained)
            return true;

        if (!(result instanceof HttpFetchResult.ResultOk resultOk))
            return false;

        if (reference == null)
            return false;
        if (doc == null)
            return false;
        if (doc.documentBody == null)
            return false;

        return DocumentBodyExtractor.extractBody(resultOk)
                .map((contentType, body) -> reference.isContentBodySame(doc.documentBody, body))
                .orElse(false);
    }

    public ContentTags getContentTags() {
        if (null == doc)
            return ContentTags.empty();

        String headers = doc.headers;
        if (headers == null)
            return ContentTags.empty();

        String[] headersLines = headers.split("\n");

        String lastmod = null;
        String etag = null;

        for (String line : headersLines) {
            if (line.toLowerCase().startsWith("etag:")) {
                etag = line.substring(5).trim();
            }
            if (line.toLowerCase().startsWith("last-modified:")) {
                lastmod = line.substring(14).trim();
            }
        }

        return new ContentTags(etag, lastmod);
    }

    public boolean isEmpty() {
        return doc == null || reference == null;
    }

}
