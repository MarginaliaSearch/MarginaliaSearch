package nu.marginalia.crawl.fetcher;

import okhttp3.Request;

/** Encapsulates request modifiers; the ETag and Last-Modified tags for a resource */
public record ContentTags(String etag, String lastMod) {
    public static ContentTags empty() {
        return new ContentTags(null, null);
    }

    public boolean isPresent() {
        return etag != null || lastMod != null;
    }

    public boolean isEmpty() {
        return etag == null && lastMod == null;
    }

    /** Paints the tags onto the request builder. */
    public void paint(Request.Builder getBuilder) {

        if (etag != null) {
            getBuilder.addHeader("If-None-Match", ifNoneMatch());
        }

        if (lastMod != null) {
            getBuilder.addHeader("If-Modified-Since", ifModifiedSince());
        }
    }

    private String ifNoneMatch() {
        // Remove the W/ prefix if it exists

        //'W/' (case-sensitive) indicates that a weak validator is used. Weak etags are
        // easy to generate, but are far less useful for comparisons. Strong validators
        // are ideal for comparisons but can be very difficult to generate efficiently.
        // Weak ETag values of two representations of the same resources might be semantically
        // equivalent, but not byte-for-byte identical. This means weak etags prevent caching
        // when byte range requests are used, but strong etags mean range requests can
        // still be cached.
        // - https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/ETag

        if (null != etag && etag.startsWith("W/")) {
            return etag.substring(2);
        } else {
            return etag;
        }
    }

    private String ifModifiedSince() {
        return lastMod;
    }
}
