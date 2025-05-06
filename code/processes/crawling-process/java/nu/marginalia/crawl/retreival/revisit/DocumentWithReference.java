package nu.marginalia.crawl.retreival.revisit;

import nu.marginalia.crawl.fetcher.ContentTags;
import nu.marginalia.crawl.retreival.CrawlDataReference;
import nu.marginalia.model.body.HttpFetchResult;
import nu.marginalia.model.crawldata.CrawledDocument;

import javax.annotation.Nullable;
import java.util.Objects;

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
        if (doc.documentBodyBytes.length == 0) {
            if (doc.httpStatus < 300) {
                return resultOk.bytesLength() == 0;
            }
            else if (doc.httpStatus == 301 || doc.httpStatus == 302 || doc.httpStatus == 307) {
                @Nullable
                String docLocation = doc.getHeader("Location");
                @Nullable
                String resultLocation = resultOk.header("Location");

                return Objects.equals(docLocation, resultLocation);
            }
            else {
                return doc.httpStatus == resultOk.statusCode();
            }
        }

        return CrawlDataReference.isContentBodySame(doc.documentBodyBytes, resultOk.bytesRaw());
    }

    public ContentTags getContentTags() {
        if (null == doc)
            return ContentTags.empty();

        if (doc.documentBodyBytes.length == 0 || (doc.httpStatus != 200 && doc.httpStatus != 206))
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
