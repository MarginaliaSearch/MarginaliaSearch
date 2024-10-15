package nu.marginalia.crawl.retreival.revisit;

import nu.marginalia.crawl.fetcher.ContentTags;
import nu.marginalia.crawl.retreival.CrawlDataReference;
import nu.marginalia.model.body.DocumentBodyExtractor;
import nu.marginalia.model.body.DocumentBodyResult;
import nu.marginalia.model.body.HttpFetchResult;
import nu.marginalia.model.crawldata.CrawledDocument;

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
        if (result instanceof HttpFetchResult.Result304Raw)
            return true;
        if (result instanceof HttpFetchResult.Result304ReplacedWithReference)
            return true;

        if (!(result instanceof HttpFetchResult.ResultOk resultOk))
            return false;

        if (reference == null)
            return false;
        if (doc == null)
            return false;
        if (doc.documentBody == null)
            return false;

        if (!(DocumentBodyExtractor.asString(resultOk) instanceof DocumentBodyResult.Ok<String> bodyOk)) {
            return false;
        }

        return CrawlDataReference.isContentBodySame(doc.documentBody, bodyOk.body());
    }

    public ContentTags getContentTags() {
        if (null == doc)
            return ContentTags.empty();

        if (doc.documentBody == null || doc.httpStatus != 200)
            return ContentTags.empty();

        String lastmod = doc.getLastModified();
        String etag = doc.getEtag();

        if (lastmod == null && etag == null) {
            return ContentTags.empty();
        }

        return new ContentTags(etag, lastmod);
    }

    public boolean isEmpty() {
        return doc == null || reference == null;
    }

}
