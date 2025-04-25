package nu.marginalia.crawl.fetcher;

import org.apache.hc.client5.http.classic.methods.HttpGet;

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
    public void paint(HttpGet request) {

        // Paint the ETag header if present,
        // otherwise paint the Last-Modified header
        // (but not both at the same time due to some servers not liking it)

        if (etag != null) {
            request.addHeader("If-None-Match", etag);
        } else if (lastMod != null) {
            request.addHeader("If-Modified-Since", lastMod);
        }
    }
}
