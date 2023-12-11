package nu.marginalia.crawl.retreival.revisit;

import nu.marginalia.crawl.retreival.CrawlDataReference;
import nu.marginalia.crawl.retreival.CrawlerRetreiver;
import nu.marginalia.crawl.retreival.fetcher.ContentTags;
import nu.marginalia.crawling.model.CrawledDocument;

import javax.annotation.Nullable;
import java.time.LocalDateTime;

public record DocumentWithReference(
        @Nullable CrawledDocument doc,
        @Nullable CrawlDataReference reference) {

    private static final DocumentWithReference emptyInstance = new DocumentWithReference(null, null);

    public static DocumentWithReference empty() {
        return emptyInstance;
    }

    public boolean isContentBodySame(CrawledDocument newDoc) {
        if (reference == null)
            return false;
        if (doc == null)
            return false;
        if (doc.documentBody == null)
            return false;
        if (newDoc.documentBody == null)
            return false;

        return reference.isContentBodySame(doc, newDoc);
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

    /**
     * If the provided document has HTTP status 304, and the reference document is provided,
     * return the reference document; otherwise return the provided document.
     */
    public CrawledDocument replaceOn304(CrawledDocument fetchedDoc) {

        if (doc == null)
            return fetchedDoc;

        // HTTP status 304 is NOT MODIFIED, which means the document is the same as it was when
        // we fetched it last time. We can recycle the reference document.
        if (fetchedDoc.httpStatus != 304)
            return fetchedDoc;

        var ret = doc;
        ret.recrawlState = CrawlerRevisitor.documentWasRetainedTag;
        ret.timestamp = LocalDateTime.now().toString();
        return ret;
    }
}
